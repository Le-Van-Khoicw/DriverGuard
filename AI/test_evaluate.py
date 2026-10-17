"""Metric edge cases: missed closures, missing observations, brief blinks and cooldown."""

import unittest

from evaluate import AndroidBaseline, Config, score


def frames(labels, onsets=(), states=None):
    return [dict(frame=i, time_s=i / 10, end_s=(i + 1) / 10, phase="MONITORING",
                 ear=0.2, eye_state=(states[i] if states else label),
                 alarm_active=i in onsets, alarm_onset=i in onsets)
            for i, label in enumerate(labels)]


class EvaluationTests(unittest.TestCase):
    def test_long_closure_one_warning_matches_one_event(self):
        labels = ["CLOSED"] * 40
        result = score(frames(labels, onsets=(21, 30)), dict(enumerate(labels)), Config())
        self.assertEqual(result["alarm_events"]["TP"], 1)
        self.assertEqual(result["alarm_events"]["FP"], 1)
        self.assertEqual(result["alarm_events"]["FN"], 0)
        self.assertAlmostEqual(result["alarm_events"]["mean_delay_after_eligible_s"], 0.1)

    def test_no_alarm_on_long_closure_is_false_negative(self):
        labels = ["CLOSED"] * 40
        result = score(frames(labels), dict(enumerate(labels)), Config())
        self.assertEqual(result["alarm_events"]["FN"], 1)
        self.assertEqual(result["alarm_timeline"]["counts"]["FN"], 20)

    def test_brief_blink_requires_no_alarm(self):
        labels = ["OPEN"] * 10 + ["CLOSED"] * 3 + ["OPEN"] * 10
        result = score(frames(labels), dict(enumerate(labels)), Config())
        self.assertEqual(result["alarm_events"]["FN"], 0)
        self.assertIsNone(result["alarm_events"]["recall"])
        self.assertEqual(result["alarm_timeline"]["counts"]["TN"], len(labels))

    def test_unknown_truth_is_excluded_not_true_negative(self):
        result = score(frames(["UNKNOWN"]), {0: "UNKNOWN"}, Config())
        self.assertEqual(result["eligible_labeled_frames"], 0)
        self.assertEqual(result["alarm_timeline"]["counts"]["TN"], 0)
        self.assertIsNone(result["raw_measurement_coverage"])

    def test_early_alarm_is_visible_and_does_not_hide_missed_eligible_event(self):
        labels = ["CLOSED"] * 40
        result = score(frames(labels, onsets=(19,)), dict(enumerate(labels)), Config())
        self.assertEqual(result["alarm_events"]["TP"], 0)
        self.assertEqual(result["alarm_events"]["FP"], 1)
        self.assertEqual(result["alarm_events"]["FN"], 1)
        self.assertEqual(result["warning_events"][0]["reason"], "EARLY_WARNING")

    def test_calibration_includes_boundary_frame_like_kotlin(self):
        baseline = AndroidBaseline(Config())
        baseline.process(0.3, 0)
        self.assertEqual(baseline.process(0.5, 5)[0], "CALIBRATION_END")
        self.assertAlmostEqual(baseline.threshold, 0.3)

    def test_sound_edge_is_scored_even_when_log_cooldown_suppresses_record(self):
        labels = ["CLOSED"] * 40
        rows = frames(labels, onsets=(21,))
        rows[21]["alarm_onset"] = False
        result = score(rows, dict(enumerate(labels)), Config())
        self.assertEqual(result["alarm_events"]["TP"], 1)
        self.assertEqual(result["recorded_log_events"], 0)

    def test_unknown_prediction_is_reported_as_missed_in_conservative_recall(self):
        result = score(frames(["CLOSED"], states=["UNKNOWN"]), {0: "CLOSED"}, Config())
        self.assertEqual(result["eye_known_frames"]["recall_counting_unknown_as_miss"], 0)
        self.assertIsNone(result["eye_known_frames"]["recall"])

    def test_unknown_truth_breaks_ground_truth_closure(self):
        labels = ["CLOSED"] * 15 + ["UNKNOWN"] + ["CLOSED"] * 15
        result = score(frames(labels), dict(enumerate(labels)), Config())
        self.assertEqual(result["closure_events"], [])

    def test_android_trailing_missing_measurement_is_interpolated(self):
        baseline = AndroidBaseline(Config())
        for index in range(51):
            baseline.process(0.3, index / 10)
        for index in range(51, 80):
            baseline.process(0.1, index / 10)
        # Kotlin tolerates up to 3 missing samples in 13, reusing the last valid EAR.
        self.assertEqual(baseline.process(None, 8.0)[1], "CLOSED")
        baseline.process(None, 8.1)
        baseline.process(None, 8.2)
        self.assertEqual(baseline.process(None, 8.3)[1], "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
