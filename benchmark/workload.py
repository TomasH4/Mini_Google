"""Seeded UTF-8 workloads. All files are created before the first backend starts."""
import hashlib
import random
from pathlib import Path

GOLDEN = Path(__file__).with_name("golden_queries.txt")
PHRASES = """memoria ram|hilos virtuales|índice invertido|tabla hash|red local|árbol binario|búsqueda exacta|programación funcional|cola prioridad|ranking bm25|texto completo|consulta virtual|cache local|latencia de red|concurrencia estable""".split("|")

def generate(directory, documents):
    rng = random.Random(42)
    queries = GOLDEN.read_text(encoding="utf-8").splitlines()
    tokens = 0
    for filename, count, first in [("corpus.tsv", documents, 1), ("writes.tsv", 2000, documents + 1)]:
        with (directory / filename).open("w", encoding="utf-8", newline="\n") as out:
            for identifier in range(first, first + count):
                words = [rng.choice(["java", "scala", "go"]), *rng.choices(PHRASES, k=3)]
                words.extend(f"tema{rng.randrange(512)}" for _ in range(6))
                body = " ".join(words)
                if first == 1:
                    tokens += len(body.split())
                out.write(f"{identifier}\tDocumento {identifier}\t{body}\n")
    with (directory / "queries.txt").open("w", encoding="utf-8", newline="\n") as out:
        for i in range(10000):
            topic = f"tema{rng.randrange(512)}"
            if i % 4 == 0:
                query = rng.choice(queries)
            elif i % 4 == 1:
                query = f'{topic} AND "{rng.choice(PHRASES)}"'
            elif i % 4 == 2:
                query = f"({topic} OR tema{rng.randrange(512)}) AND NOT java"
            else:
                query = topic
            out.write(query + "\n")
    hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in
              [directory / name for name in ("corpus.tsv", "writes.tsv", "queries.txt")] + [GOLDEN]}
    return tokens, hashes

