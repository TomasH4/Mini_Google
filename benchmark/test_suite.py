import json
import math
import statistics
import subprocess
import unittest
from unittest.mock import patch

import run


class ParityTests(unittest.TestCase):
    def compare(self, edits):
        queries = run.GOLDEN.read_text(encoding="utf-8").splitlines()
        commands = {lang: [lang] for lang in run.LANGS}

        def response(args, **kwargs):
            lines = ["HITS 1:1,2:0.5" for _ in queries]
            for query, value in edits.get(args[0], {}).items():
                lines[queries.index(query)] = value
            return subprocess.CompletedProcess(args, 0, "\n".join(lines), "")

        with patch.object(run.subprocess, "run", side_effect=response), patch.object(run.time, "sleep"):
            return run.parity(commands, run.WORK)

    def test_tolerance(self):
        result = self.compare({"scala": {"java": "HITS 1:1.00009,2:0.5"}})
        self.assertTrue(result["passed"])
        self.assertEqual(result["match_rate_pct"], 100)

    def test_score_order_and_missing_suggestions(self):
        edits = {lang: {"mem*": "HITS "} for lang in run.LANGS}
        edits["go"]["java"] = "HITS 2:0.5,1:1"
        edits["scala"]["scala"] = "HITS 1:1.0002,2:0.5"
        result = self.compare(edits)
        self.assertFalse(result["passed"])
        count = result["queries_tested"]
        self.assertAlmostEqual(result["match_rate_pct"], 100*(count-3)/count)


class PublishedReportTests(unittest.TestCase):
    def test_medians_and_local_wrapper(self):
        report = json.loads((run.HERE / "results.json").read_text(encoding="utf-8"))
        script = (run.HERE / "results-data.js").read_text(encoding="utf-8").strip()
        self.assertTrue(script.startswith("window.BENCHMARK_DATA = ") and script.endswith(";"))
        self.assertEqual(json.loads(script.split("=", 1)[1][:-1]), report)
        if not report["parity_check"]["passed"]:
            self.assertEqual(report["results"], {})
            return
        metadata = report["metadata"]
        for lang, result in report["results"].items():
            raw = metadata["runs"][lang]
            self.assertEqual(len(raw), metadata["corpus"]["iterations"])
            for key in raw[0]:
                value = result["latency_us"][key] if key in run.PERCENTILES else result[key]
                self.assertEqual(statistics.median(row[key] for row in raw), value)
            for axis, (_, label, _) in run.AXES.items():
                factor = result["relative_x"][label]
                self.assertGreaterEqual(factor, 1)
                self.assertTrue(math.isclose(result["normalized_scores"][axis], 100 / factor))


if __name__ == "__main__":
    unittest.main()

