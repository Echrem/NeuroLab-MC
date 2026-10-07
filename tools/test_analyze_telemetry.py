import json
from pathlib import Path
import tempfile
import unittest

from analyze_telemetry import analyze, write_report, position_runs, altitude_chart


class AnalysisTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "events.jsonl"

    def save(self, records):
        self.path.write_text("".join(json.dumps(r) + "\n" for r in records), encoding="utf-8")

    def sample(self, **extra):
        return dict(event="sample", runId="run-1", entityUuid="mob-1", mode="neural",
                    dimension="minecraft:overworld", gameTick=extra.pop("gameTick", 100),
                    neuralTick=extra.pop("neuralTick", 10), spikes=extra.pop("spikes", 12), **extra)

    def test_repeated_neural_snapshots_are_not_counted_twice(self):
        self.save([self.sample(), self.sample(gameTick=102),
                   self.sample(gameTick=104, neuralTick=11, spikes=24)])
        groups, _, _ = analyze(self.path)
        self.assertEqual(groups[0].row()["samples"], 3)
        self.assertEqual(groups[0].row()["neuralSnapshots"], 2)
        self.assertEqual(groups[0].row()["meanSpikes"], 18)
        self.assertIsNone(groups[0].row()["meanSynapticEvents"])

    def test_repeated_labels_remain_distinct_trials(self):
        self.save([self.sample(trialLabel="left", trialStartedAt=90),
                   self.sample(gameTick=200, trialLabel="left", trialStartedAt=190)])
        groups, _, _ = analyze(self.path)
        self.assertEqual(len(groups), 2)

    def test_live_or_dropped_logs_are_marked(self):
        self.save([{"event": "session_start", "runId": "run-1"}, self.sample()])
        self.assertIn("no session_end", " ".join(analyze(self.path)[2]))
        self.save([{"event": "session_start", "runId": "run-1"}, self.sample(),
                   {"event": "session_end", "runId": "run-1", "droppedRecords": 3}])
        self.assertIn("3 records were dropped", " ".join(analyze(self.path)[2]))

    def test_malformed_or_reversed_logs_fail_with_line_number(self):
        self.path.write_text('{"event":', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, r"events.jsonl:1:"):
            analyze(self.path)
        self.save([self.sample(), self.sample(gameTick=99)])
        with self.assertRaisesRegex(ValueError, "backwards"):
            analyze(self.path)

    def test_report_is_offline_and_escapes_untrusted_labels(self):
        self.save([self.sample(trialLabel="<script>alert(1)</script>", trialStartedAt=10)])
        output = Path(self.temp.name) / "report"
        self.assertEqual(write_report(self.path, output), 1)
        html = (output / "report.html").read_text()
        self.assertNotIn("<script>", html)
        self.assertIn("&lt;script&gt;", html)
        self.assertIn("<svg", html)
        self.assertNotIn("https://", html)
        self.assertTrue((output / "summary.csv").exists())

    def test_measured_speed_is_time_weighted_and_missing_intervals_are_excluded(self):
        self.save([
            self.sample(schemaVersion=6, gameTick=100, x=0, y=64, z=0),
            self.sample(schemaVersion=6, gameTick=102, x=3, y=64, z=4,
                        positionDeltaValid=True, positionDeltaTicks=2, deltaX=3, deltaY=0, deltaZ=4),
            self.sample(schemaVersion=6, gameTick=112, x=3, y=64, z=9,
                        positionDeltaValid=True, positionDeltaTicks=10, deltaX=0, deltaY=0, deltaZ=5),
            self.sample(schemaVersion=6, gameTick=114),
            self.sample(schemaVersion=6, gameTick=116, x=9, y=67, z=9,
                        positionDeltaValid=True, positionDeltaTicks=2, deltaX=6, deltaY=3, deltaZ=0),
        ])
        groups, _, _ = analyze(self.path)
        row = groups[0].row()
        self.assertEqual(row["positionIntervals"], 2)
        self.assertEqual(row["observedDistance"], 10)
        self.assertEqual(row["observedGameTicks"], 12)
        self.assertAlmostEqual(row["meanSpeed"], 10 * 20 / 12)
        self.assertEqual(row["meanHorizontalDisplacement"], 5)
        self.assertEqual(len(position_runs(groups[0], ("x", "z"))), 2)
        self.assertEqual(altitude_chart(groups[0]).count("<polyline"), 2)

    def test_motion_does_not_bridge_long_gaps_or_conditions(self):
        self.save([
            self.sample(schemaVersion=6, x=0, y=64, z=0),
            self.sample(schemaVersion=6, gameTick=500, x=20, y=65, z=0,
                        positionDeltaValid=True, positionDeltaTicks=400, deltaX=20, deltaY=1, deltaZ=0),
            self.sample(schemaVersion=6, gameTick=502, x=22, y=65, z=0, trialLabel="second",
                        trialStartedAt=501, positionDeltaValid=True, positionDeltaTicks=2,
                        deltaX=2, deltaY=0, deltaZ=0),
        ])
        groups, _, _ = analyze(self.path)
        self.assertEqual(len(groups), 2)
        for group in groups:
            self.assertIsNone(group.row()["meanSpeed"])
            self.assertEqual(group.row()["positionIntervals"], 0)

    def test_old_schema_without_positions_remains_readable(self):
        self.save([self.sample(schemaVersion=5)])
        groups, _, _ = analyze(self.path)
        self.assertIsNone(groups[0].row()["meanSpeed"])
        self.assertEqual(position_runs(groups[0], ("y",)), [])


if __name__ == "__main__":
    unittest.main()
