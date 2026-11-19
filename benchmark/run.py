"""Run from any directory with Python 3.9+, Maven, JDK 21+ and Go 1.22+."""
import argparse
import contextlib
import datetime
import itertools
import json
import math
import os
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from build import ROOT, HERE, WORK, build, tool
from host import machine, peak_mb
from workload import GOLDEN, generate

LANGS = ("go", "java", "scala")
PERCENTILES = ("p50", "p90", "p95", "p99", "p999")
FIELDS = ("indexing_time_ms", "ram_retained_mb", "qps_single", "qps_concurrent", *PERCENTILES)
AXES = {
    "indexing": ("indexing_time_ms", "indexing", False),
    "throughput": ("qps_concurrent", "qps_concurrent", True),
    "p99_stability": ("p99", "p99", False),
    "memory_efficiency": ("ram_retained_mb", "ram_retained", False),
    "code_conciseness": ("loc", "loc", False),
}

def publish(report):
    data = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False)
    for name, content in [("results.json", data), ("results-data.js", "window.BENCHMARK_DATA = " + data + ";")]:
        path = HERE / name
        temp = path.with_suffix(".tmp")
        temp.write_text(content + "\n", encoding="utf-8")
        temp.replace(path)

def args_for(cmd, mode, queries, directory):
    return [*cmd, mode, directory / "corpus.tsv", queries, directory / "writes.tsv"]

def parity(commands, directory):
    queries = GOLDEN.read_text(encoding="utf-8").splitlines()
    if len(set(queries)) < 50 or not all(queries):
        raise ValueError("expected at least 50 distinct, nonempty golden queries")
    responses = {}
    for lang in LANGS:
        proc = subprocess.run(args_for(commands[lang], "parity", GOLDEN, directory), capture_output=True, encoding="utf-8")
        if proc.returncode:
            raise RuntimeError(f"{lang} parity process: {proc.stderr}")
        rows = proc.stdout.splitlines()
        if len(rows) != len(queries) or any(not row.startswith("HITS ") for row in rows):
            raise ValueError(f"invalid {lang} parity response")
        responses[lang] = [[(int(doc), float(score)) for doc, score in
                            (hit.split(":") for hit in row[5:].split(",") if hit)] for row in rows]
        time.sleep(2)
    differences = []
    for n, query in enumerate(queries):
        rows = [responses[lang][n] for lang in LANGS]
        good = all(len(row) <= 10 and len({d for d, _ in row}) == len(row)
                   and all(math.isfinite(s) for _, s in row) for row in rows)
        if query.startswith("~") or query.endswith("*"):
            good = good and all(rows)
        for a, b in itertools.combinations(rows, 2):
            good = good and len(a) == len(b) and all(
                da == db and abs(sa-sb) <= 1e-4 for (da, sa), (db, sb) in zip(a, b))
        if not good:
            differences.append(query)
    return dict(passed=not differences, queries_tested=len(queries),
                match_rate_pct=100*(len(queries)-len(differences))/len(queries),
                notes="Top-10 ordenado y BM25 con tolerancia absoluta 1e-4." if not differences
                else "Consultas divergentes: " + ", ".join(differences))

def measure(cmd, docs, directory, mode):
    start = time.perf_counter_ns()
    with subprocess.Popen(args_for(cmd, mode, directory / "queries.txt", directory),
                          stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, encoding="utf-8") as proc:
        try:
            if proc.stdout.readline().strip() != "READY":
                raise ValueError("missing cold-start marker")
            cold = (time.perf_counter_ns()-start)/1e6
            row = proc.stdout.readline().split()
            fields = FIELDS[:2] if mode == "index" else FIELDS
            marker = "INDEX" if mode == "index" else "METRICS"
            if len(row) != len(fields)+1 or row[0] != marker:
                raise ValueError(f"missing metrics: {row}")
            metrics = dict(zip(fields, map(float, row[1:])))
            metrics.update(cold_start_ms=cold, ram_rss_peak_mb=peak_mb(proc),
                           docs_per_sec=1000*docs/metrics["indexing_time_ms"])
            if any(not math.isfinite(v) or v <= 0 for v in metrics.values()):
                raise ValueError(f"invalid sample: {metrics}")
            proc.stdin.write("\n")
            proc.stdin.flush()
            if proc.wait(timeout=15):
                raise RuntimeError("backend failed")
            return metrics
        finally:
            if proc.poll() is None:
                proc.kill()

def loc(paths):
    # Mask comments but preserve quoted literals, including multiline strings.
    tokens = re.compile(r'""".*?"""|\x60[^\x60]*\x60|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/', re.S)
    def mask(match):
        value = match[0]
        return "\n"*value.count("\n") if value.startswith(("//","/*")) else value
    return sum(bool(line.strip()) for path in paths
               for line in tokens.sub(mask, path.read_text(encoding="utf-8")).splitlines())

def sources(lang):
    module = ROOT / ("mini-google-"+lang)
    if lang == "go":
        return [p for folder in ("cmd","internal") for p in (module/folder).rglob("*.go")
                if not p.name.endswith("_test.go")]
    return [*(module/"src/main").rglob("*."+lang), HERE/"jvm/Suite.java",
            HERE/"jvm/Vocabulary.java", HERE/"jvm"/(lang.capitalize()+"Engine.java")]

def samples():
    specs = {
        "ast_parser": [("internal/query/query.go", "func (p *parser) parseOr"),
                       ("query/QueryParser.java", "private QueryNode parseOr"),
                       ("query/QueryParser.scala", "private def parseOr")],
        "bm25_ranking": [("internal/ranking/ranking.go","func Score"),
                         ("search/SearchEngine.java","double numerator"),
                         ("search/SearchEngine.scala","private def score")],
        "concurrency": [("internal/index/index.go","i.mu.Lock()"),
                        ("jvm/Suite.java","long stamp = lock.writeLock()"),
                        ("bench/MixedFibers.scala","lock <- TReentrantLock.make.commit")],
    }
    insights = ["Los tres analizadores aplican precedencia y construyen un árbol booleano.",
                "La frecuencia del término y la longitud documental determinan el aporte BM25.",
                "Go usa RWMutex, Java combina hilos virtuales con StampedLock y Scala coordina fibras ZIO con un bloqueo transaccional STM."]
    result = {}
    for (name, entries), insight in zip(specs.items(), insights):
        result[name] = {"insight": insight}
        for lang, (suffix, marker) in zip(LANGS, entries):
            path = next(p for p in sources(lang) if p.as_posix().endswith(suffix))
            lines = path.read_text(encoding="utf-8").splitlines()
            start = next(i for i,line in enumerate(lines) if marker in line)
            result[name][lang] = "\n".join(lines[start:start+8])
    return result

def collect(commands, documents, directory, mode, iterations):
    runs = {lang: [] for lang in LANGS}
    for iteration in range(iterations):
        offset = iteration % len(LANGS)
        for lang in LANGS[offset:] + LANGS[:offset]:
            print(f"{documents} docs, {iteration+1}/{iterations} · {lang} ({mode})", flush=True)
            runs[lang].append(measure(commands[lang], documents, directory, mode))
            time.sleep(2)
    medians, spread = {}, {}
    for lang, rows in runs.items():
        medians[lang], spread[lang] = {}, {}
        for key in rows[0]:
            values = [row[key] for row in rows]
            q1, _, q3 = statistics.quantiles(values, n=4, method="inclusive")
            medians[lang][key] = statistics.median(values)
            spread[lang][key] = q3 - q1
    return medians, spread, runs


def validate(commands, directory):
    try:
        return parity(commands, directory)
    except (ValueError, RuntimeError, OSError) as error:
        return dict(passed=False, queries_tested=0, match_rate_pct=0.0, notes=str(error))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--documents",type=int,default=10000)
    parser.add_argument("--iterations",type=int,default=5)
    parser.add_argument("--index-documents", type=int, default=100000, help="secondary indexing scale, 0 disables")
    args = parser.parse_args()
    if args.documents < 1 or args.iterations < 5 or args.index_documents < 0:
        parser.error("positive corpus and at least five iterations required")
    commands, artifacts = build()
    tokens, hashes = generate(WORK, args.documents)
    scala = ET.parse(ROOT/"mini-google-scala/pom.xml").findtext(".//{*}scala.version")
    gate = validate(commands, WORK)
    report = {"metadata": {"timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "machine": machine(), "runtimes": {
                  "go": subprocess.check_output([tool("go"),"version"],text=True).strip(),
                  "java": subprocess.check_output(["java","-version"],stderr=subprocess.STDOUT,text=True).splitlines()[0],
                  "scala":scala},
              "corpus":{"documents":args.documents,"total_tokens":tokens,"queries_per_run":10000,
                        "iterations":args.iterations}, "seed":42,"sha256":hashes},
              "parity_check":gate, "results":{}, "code_samples":samples()}
    if not report["parity_check"]["passed"]:
        publish(report)
        sys.exit(report["parity_check"]["notes"])
    extra = None
    if args.index_documents and args.index_documents != args.documents:
        directory = WORK / f"index-{args.index_documents}"
        directory.mkdir(exist_ok=True)
        extra_tokens, extra_hashes = generate(directory, args.index_documents)
        extra_gate = validate(commands, directory)
        extra = dict(documents=args.index_documents, total_tokens=extra_tokens, sha256=extra_hashes,
                     parity_check=extra_gate)
        report["metadata"]["index_scale"] = extra
        if not extra_gate["passed"]:
            report["parity_check"] = dict(extra_gate, notes="Secondary scale: " + extra_gate["notes"])
            publish(report)
            sys.exit(report["parity_check"]["notes"])
    print("Paridad aprobada.", flush=True)
    results, spread, runs = collect(commands, args.documents, WORK, "measure", args.iterations)
    report["metadata"]["runs"] = runs
    report["results"] = results
    if extra is not None:
        extra["medians"], extra["iqr"], extra["runs"] = collect(
            commands, args.index_documents, directory, "index", args.iterations)
    for lang, result in results.items():
        result.update(loc=loc(sources(lang)), artifact_mb=artifacts[lang].stat().st_size/2**20,
                      relative_x={}, normalized_scores={})
    for axis, (key, label, higher) in AXES.items():
        results = report["results"].values()
        leader = (max if higher else min)(r[key] for r in results)
        for r in results:
            factor = leader/r[key] if higher else r[key]/leader
            r["relative_x"][label] = factor
            r["normalized_scores"][axis] = 100/factor
    for result in report["results"].values():
        result["latency_us"] = {k:result.pop(k) for k in PERCENTILES}
    report["metadata"]["iqr"] = spread
    report["metadata"]["protocol"] = dict(warmup_queries=2000, warmup_mixed_searches=800, warmup_mixed_inserts=200, readers=4, writers=1,
        mixed_searches=8000, mixed_inserts=2000, cooldown_seconds=2, bm25_absolute_tolerance=1e-4,
        latency_phase="sequential", dispersion="inclusive IQR, Q3-Q1")
    publish(report)

@contextlib.contextmanager
def exclusive_run():
    WORK.mkdir(exist_ok=True)
    with (WORK / "suite.lock").open("a+b") as handle:
        if handle.tell() == 0:
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise SystemExit("Otra ejecución tiene ocupado benchmark/work") from error
        yield

if __name__ == "__main__":
    with exclusive_run():
        main()

