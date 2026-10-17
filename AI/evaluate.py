"""Offline replay of the original Android EAR baseline; labels never enter prediction.

This frozen baseline predates the trip lifecycle and observation-rules-v1 score.

Use Python 3.12. Run --help or read README.md before interpreting the scores.
"""

import argparse
import csv
import hashlib
import importlib.metadata
import json
import math
import re
import statistics
from collections import Counter, deque
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "Mobile/DriverGuard/app/src/main/assets/face_landmarker.task"
ANDROID = ROOT / "Mobile/DriverGuard/app/src/main/java/com/example/driverguard"
SOURCE_FILES = [
    "feature/monitoring/FaceLandmarkerAnalyzer.kt",
    "feature/monitoring/MonitoringViewModel.kt",
    "feature/monitoring/MonitoringScreen.kt",
    "feature/monitoring/ai/EarCalculator.kt",
    "feature/monitoring/ai/TemporalFeatureExtractor.kt",
    "feature/monitoring/ai/DrowsinessDetector.kt",
]
LABELS = {"OPEN", "CLOSED", "UNKNOWN"}


@dataclass(frozen=True)
class Config:
    calibration_s: float = 5.0
    threshold_factor: float = 0.75
    alarm_after_s: float = 2.0
    cooldown_s: float = 5.0
    window_size: int = 13
    minimum_valid_ratio: float = 0.70


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def divide(a, b):
    # Undefined scores must remain null, rather than looking like perfect/zero results.
    return a / b if b else None


def binary_metrics(counts):
    tp, fp, tn, fn = (counts.get(k, 0) for k in ("TP", "FP", "TN", "FN"))
    return dict(counts={k: counts.get(k, 0) for k in ("TP", "FP", "TN", "FN")},
                precision=divide(tp, tp + fp), recall=divide(tp, tp + fn),
                f1=divide(2 * tp, 2 * tp + fp + fn),
                accuracy=divide(tp + tn, tp + fp + tn + fn))


def add_confusion(counts, truth, prediction):
    counts[("TP" if prediction else "FN") if truth else ("FP" if prediction else "TN")] += 1


class AndroidBaseline:
    """Match threshold, 13-frame availability gate and trailing interpolation in Kotlin.

    Only the interpolated current EAR affects ThresholdClassifier, so older missing
    values need not be interpolated here. In particular, a few missing current
    frames can reuse the last EAR: preserve this behavior to measure the actual app.
    """

    def __init__(self, config):
        self.config = config
        self.samples = []
        self.history = deque(maxlen=config.window_size)
        self.threshold = None
        self.closed_since = None
        self.last_alert = -math.inf
        self.alert_was_active = False

    def process(self, ear, timestamp):
        c = self.config
        if self.threshold is None:
            if ear is not None and ear > 0.15:
                self.samples.append(ear)
            if timestamp < c.calibration_s:
                return "CALIBRATING", "UNKNOWN", False, False
            self.threshold = (statistics.mean(self.samples) if self.samples else 0.25) * c.threshold_factor
            # Kotlin completes calibration on this frame and returns before detection.
            return "CALIBRATION_END", "UNKNOWN", False, False
        self.history.append(ear)
        valid = [value for value in self.history if value is not None]
        available = (len(self.history) == c.window_size and
                     len(valid) / c.window_size >= c.minimum_valid_ratio)
        state = ("CLOSED" if valid[-1] < self.threshold else "OPEN") if available else "UNKNOWN"
        if state == "CLOSED":
            if self.closed_since is None:
                self.closed_since = timestamp
        else:
            self.closed_since = None
        active = self.closed_since is not None and timestamp - self.closed_since >= c.alarm_after_s
        onset = active and not self.alert_was_active and timestamp - self.last_alert > c.cooldown_s
        if onset:
            self.last_alert = timestamp
        self.alert_was_active = active
        return "MONITORING", state, active, onset


def eye_ear(landmarks, indices):
    points = [(landmarks[i].x, landmarks[i].y) for i in indices]
    width = math.dist(points[0], points[3])
    if width <= 0:
        return None
    # Normalized coordinates intentionally match EarCalculator.kt, including aspect ratio behavior.
    return (math.dist(points[1], points[5]) + math.dist(points[2], points[4])) / (2 * width)


def load_labels(path, frame_count):
    result = {}
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not {"frame", "label"} <= set(reader.fieldnames or []):
            raise ValueError(f"{path}: expected frame,label columns")
        for row in reader:
            frame, label = int(row["frame"]), row["label"].strip().upper()
            if frame in result or not 0 <= frame < frame_count or label not in LABELS:
                raise ValueError(f"{path}: duplicate/out-of-range frame or invalid label: {row}")
            result[frame] = label
    if not result:
        raise ValueError(f"{path}: no labels")
    return result


def predict_video(video, model, config):
    import cv2
    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision

    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise ValueError(f"Cannot open video: {video}")
    fps = capture.get(cv2.CAP_PROP_FPS)
    if not math.isfinite(fps) or fps <= 0:
        capture.release()
        raise ValueError(f"Invalid FPS: {video}")
    expected_frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    options = vision.FaceLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(model)),
        running_mode=vision.RunningMode.IMAGE, num_faces=1,
        min_face_detection_confidence=0.5, min_face_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    baseline, rows = AndroidBaseline(config), []
    try:
        with vision.FaceLandmarker.create_from_options(options) as landmarker:
            while True:
                ok, frame = capture.read()
                if not ok:
                    break
                frame_index = len(rows)
                timestamp = capture.get(cv2.CAP_PROP_POS_MSEC) / 1000
                # Presentation timestamps preserve duration on variable-frame-rate phone videos.
                if not math.isfinite(timestamp) or timestamp < 0 or (rows and timestamp <= rows[-1]["time_s"]):
                    raise ValueError(f"Non-increasing video timestamps at frame {frame_index}; remux video first")
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                result = landmarker.detect(image)
                ears = []
                if result.face_landmarks:
                    face = result.face_landmarks[0]
                    ears = [eye_ear(face, indices) for indices in
                            ((33, 160, 158, 133, 153, 144), (362, 385, 387, 263, 373, 380))]
                    ears = [ear for ear in ears if ear is not None and math.isfinite(ear)]
                ear = statistics.mean(ears) if ears else None
                phase, state, active, onset = baseline.process(ear, timestamp)
                rows.append(dict(frame=frame_index, time_s=timestamp, ear=ear,
                                 phase=phase, eye_state=state,
                                 alarm_active=active, alarm_onset=onset))
                if frame_index % 300 == 0:
                    print(f"  {video.name}: {frame_index}/{expected_frames} frames", flush=True)
    finally:
        capture.release()
    if not rows or (expected_frames and len(rows) != expected_frames):
        raise ValueError(f"Incomplete decode: {video}, expected {expected_frames}, got {len(rows)}")
    # The last frame has no next PTS; estimate only its duration using reported FPS.
    for index, row in enumerate(rows):
        row["end_s"] = rows[index + 1]["time_s"] if index + 1 < len(rows) else row["time_s"] + 1 / fps
    return rows, dict(fps=fps, frames=len(rows), duration_s=rows[-1]["end_s"],
                      threshold=baseline.threshold, calibration_samples=len(baseline.samples),
                      calibration_fallback=not baseline.samples)


def score(rows, labels, config):
    """Separate eye classification from temporal alarm detection; no event-level TN."""
    eye, alarm = Counter(), Counter()
    unknown_by_truth = Counter()
    alarm_truth = set()
    events = []
    run_start = None
    run_end = None
    previous_active = False
    for row in rows:
        # MonitoringScreen starts audio on DROWSY, independent of the log cooldown.
        # alarm_onset follows the ViewModel log gate; sound_onset follows UI state.
        row["sound_onset"] = row["alarm_active"] and not previous_active
        previous_active = row["alarm_active"]

    def finish_run():
        if run_start is not None and run_end - run_start > config.alarm_after_s:
            events.append(dict(start_s=run_start, eligible_s=run_start + config.alarm_after_s,
                               end_s=run_end, matched=False, delay_s=None))

    # Calibration frames are excluded; a closure already in progress starts at monitoring.
    for row in rows:
        truth = labels.get(row["frame"])
        if row["phase"] == "MONITORING" and truth == "CLOSED":
            if run_start is None:
                run_start = row["time_s"]
            run_end = row["end_s"]
            if row["time_s"] - run_start >= config.alarm_after_s:
                alarm_truth.add(row["frame"])
        else:
            finish_run()
            run_start = run_end = None
    finish_run()

    eligible = raw_valid = state_valid = 0
    observed_s = 0.0
    ignored = Counter()
    event_fp = event_tp = ignored_onsets = 0
    delays = []
    warning_events = []
    for row in rows:
        truth = labels.get(row["frame"])
        if row["phase"] != "MONITORING":
            ignored["calibration_frames"] += 1
            continue
        if truth not in {"OPEN", "CLOSED"}:
            ignored["unknown_ground_truth" if truth == "UNKNOWN" else "unlabeled_frames"] += 1
            ignored_onsets += int(row["sound_onset"])
            continue
        eligible += 1
        observed_s += row["end_s"] - row["time_s"]
        raw_valid += row["ear"] is not None
        if row["eye_state"] == "UNKNOWN":
            unknown_by_truth[truth] += 1
        else:
            state_valid += 1
            add_confusion(eye, truth == "CLOSED", row["eye_state"] == "CLOSED")
        add_confusion(alarm, row["frame"] in alarm_truth, row["alarm_active"])
        if row["sound_onset"]:
            # One emitted warning may match one eligible annotated closure, never multiple.
            matched = next((event for event in events if not event["matched"] and
                            event["eligible_s"] <= row["time_s"] < event["end_s"]), None)
            if matched is None:
                event_fp += 1
                early = next((event for event in events if event["start_s"] <= row["time_s"] < event["eligible_s"]), None)
                reason = "EARLY_WARNING" if early else "NO_UNMATCHED_ELIGIBLE_EVENT"
                warning_events.append(dict(time_s=row["time_s"], ground_truth=truth, result="FP", reason=reason))
            else:
                event_tp += 1
                matched["matched"] = True
                matched["delay_s"] = row["time_s"] - matched["eligible_s"]
                delays.append(matched["delay_s"])
                warning_events.append(dict(time_s=row["time_s"], ground_truth=truth, result="TP", reason="MATCHED"))
    event_fn = len(events) - event_tp
    eye_result = binary_metrics(eye)
    eye_result.update(unknown_predictions_by_truth=dict(unknown_by_truth),
                      recall_counting_unknown_as_miss=divide(eye["TP"], eye["TP"] + eye["FN"] + unknown_by_truth["CLOSED"]))
    return dict(
        eligible_labeled_frames=eligible, eligible_duration_s=observed_s,
        raw_measurement_coverage=divide(raw_valid, eligible),
        classified_frame_coverage=divide(state_valid, eligible),
        recorded_log_events=sum(row["alarm_onset"] for row in rows),
        ignored_frames=dict(ignored), eye_known_frames=eye_result,
        alarm_timeline=binary_metrics(alarm),
        alarm_events=dict(TP=event_tp, FP=event_fp, FN=event_fn,
                          precision=divide(event_tp, event_tp + event_fp),
                          recall=divide(event_tp, event_tp + event_fn),
                          f1=divide(2 * event_tp, 2 * event_tp + event_fp + event_fn),
                          false_alarms_per_annotated_hour=divide(event_fp * 3600, observed_s),
                          mean_delay_after_eligible_s=statistics.mean(delays) if delays else None,
                          ignored_onsets=ignored_onsets),
        closure_events=events,
        warning_events=warning_events,
    )


def write_csv(path, rows, fields=None):
    with Path(path).open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields or list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def pretty(value):
    return "N/A" if value is None else f"{value:.3f}"


def write_report(output, sessions, config, model):
    report = dict(
        purpose="Pilot evaluation of eye closure and Android baseline alarms, not proof of real drowsiness",
        config=asdict(config), model_sha256=sha256(model),
        android_source_sha256={name: sha256(ANDROID / name) for name in SOURCE_FILES},
        tool_sha256=sha256(Path(__file__)),
        versions={name: importlib.metadata.version(name) for name in ("mediapipe", "opencv-contrib-python")},
        participants=sorted({session["participant"] for session in sessions}),
        test_split_present=any(session["split"] == "test" for session in sessions),
        test_subjects_disjoint=(not ({session["participant"] for session in sessions if session["split"] == "test"} &
                                    {session["participant"] for session in sessions if session["split"] != "test"}))
                               if any(session["split"] == "test" for session in sessions) else None,
        sessions=sessions,
    )
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# DriverGuard — đánh giá pilot", "",
             "Đo nhắm mắt theo nhãn hiện có; không xác nhận buồn ngủ sinh lý hay độ an toàn khi lái xe.", "",
             f"Số người: **{len(report['participants'])}**. Có split test: **{report['test_split_present']}**.",
             f"Người ở test tách khỏi train/validation: **{report['test_subjects_disjoint']}** (None nếu chưa có test).",
             "Các split giữ theo manifest; chưa xác minh lại thủ công các nhãn cũ.", "",
             "| Người / phiên | Split | Frame chấm | Coverage EAR | Eye F1* | Alarm TP / FP / FN | Alarm recall |",
             "|---|---|---:|---:|---:|---|---:|"]
    compact = []
    for session in sessions:
        metrics = session["metrics"]
        event = metrics["alarm_events"]
        lines.append(f"| {session['participant']} / {session['session']} | {session['split']} | "
                     f"{metrics['eligible_labeled_frames']} | {pretty(metrics['raw_measurement_coverage'])} | "
                     f"{pretty(metrics['eye_known_frames']['f1'])} | {event['TP']} / {event['FP']} / {event['FN']} | {pretty(event['recall'])} |")
        compact.append(dict(participant=session["participant"], session=session["session"], split=session["split"],
                            eligible_frames=metrics["eligible_labeled_frames"],
                            eye_f1_known=metrics["eye_known_frames"]["f1"],
                            raw_coverage=metrics["raw_measurement_coverage"], **event))
    lines += ["", "*Eye F1 chỉ tính frame dự đoán OPEN/CLOSED. Xem coverage, UNKNOWN và recall tính UNKNOWN là bỏ sót trong JSON.",
              "", "## Ma trận trạng thái mắt (chỉ frame phân loại được)", "",
              "| Phiên | TP | FP | TN | FN | UNKNOWN khi CLOSED | UNKNOWN khi OPEN |",
              "|---|---:|---:|---:|---:|---:|---:|"]
    for session in sessions:
        eye = session["metrics"]["eye_known_frames"]
        counts, unknown = eye["counts"], eye["unknown_predictions_by_truth"]
        lines.append(f"| {session['session']} | {counts['TP']} | {counts['FP']} | {counts['TN']} | {counts['FN']} | {unknown.get('CLOSED', 0)} | {unknown.get('OPEN', 0)} |")
    lines += ["", "## Ma trận trạng thái cảnh báo theo frame", "",
              "| Phiên | TP | FP | TN | FN |", "|---|---:|---:|---:|---:|"]
    for session in sessions:
        counts = session["metrics"]["alarm_timeline"]["counts"]
        lines.append(f"| {session['session']} | {counts['TP']} | {counts['FP']} | {counts['TN']} | {counts['FN']} |")
    lines += ["", "## Cách đọc", "",
              f"- Alarm timeline: TP/FP/TN/FN theo frame sau khi nhãn CLOSED liên tục đủ {config.alarm_after_s:g} giây.",
              "- Alarm events: mỗi lần phát cảnh báo ghép tối đa một sự kiện nhắm kéo dài. Không định nghĩa TN theo sự kiện.",
              "- Sound onset theo cạnh bật trạng thái DROWSY của UI; alarm_onset trong frames.csv là sự kiện ghi log có cooldown, hai đại lượng khác nhau.",
              "- Delay tính từ thời điểm đủ điều kiện cảnh báo, không phải từ khi bắt đầu nhắm mắt.",
              "- Ghép sự kiện nghiêm ngặt: cảnh báo trước thời điểm đủ điều kiện dù đang CLOSED vẫn là FP (EARLY_WARNING), sự kiện đó vẫn có thể FN. Xem warning_events.csv.",
              "- Bỏ qua calibration và nhãn UNKNOWN/thiếu. Chúng không được tính thành TN.",
              "- Báo nhầm/giờ chuẩn hóa từ đoạn có nhãn; clip ngắn không đại diện một giờ chạy thật.",
              "- Python giữ baseline nhận diện ban đầu; không đánh giá điểm chuyến observation-rules-v1 hay toàn bộ lifecycle app mới.",
              "- Bộ trích Kotlin nội suy frame thiếu trong cửa sổ 13 frame; replay giữ hành vi này để đo hiện trạng.",
              "- 5 giây đầu phải nhìn thẳng, mắt mở. Xem frames.csv để kiểm tra; fallback không thay thế hiệu chỉnh hợp lệ.",
              "- Không gộp train và validation thành kết quả test. Các video cùng người không chứng minh khả năng trên người mới.",
              "", "## Cấu hình", "", "```json", json.dumps(asdict(config), indent=2), "```", ""]
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")
    write_csv(output / "summary.csv", compact)


def run(args):
    import cv2
    cv2.setNumThreads(1)
    config = Config(threshold_factor=args.threshold_factor, alarm_after_s=args.alarm_after)
    if not math.isfinite(config.threshold_factor) or not 0 < config.threshold_factor < 1:
        raise ValueError("threshold-factor must be between 0 and 1")
    if not math.isfinite(config.alarm_after_s) or config.alarm_after_s <= 0:
        raise ValueError("alarm-after must be positive")
    with args.manifest.open(encoding="utf-8-sig", newline="") as stream:
        entries = list(csv.DictReader(stream))
    if not entries:
        raise ValueError("Empty dataset manifest")
    seen = set()
    for entry in entries:
        for field in ("participant", "session"):
            if not re.fullmatch(r"[A-Za-z0-9_-]+", entry[field]):
                raise ValueError(f"Invalid {field}: {entry[field]}")
        key = (entry["participant"], entry["session"])
        if key in seen or entry["split"] not in {"train", "validation", "test"}:
            raise ValueError(f"Duplicate session or invalid split: {entry}")
        seen.add(key)
        for field in ("video", "labels"):
            if not (args.data_root / entry[field]).is_file():
                raise FileNotFoundError(args.data_root / entry[field])
    if args.output.exists():
        raise ValueError("Output directory exists; choose a new run directory to preserve previous results")
    args.output.mkdir(parents=True)
    (args.output / "manifest.csv").write_bytes(args.manifest.read_bytes())
    sessions = []
    for entry in entries:
        video, label_path = (args.data_root / entry[field] for field in ("video", "labels"))
        print(f"Processing {entry['participant']} / {entry['session']} ({entry['split']})", flush=True)
        rows, metadata = predict_video(video, args.model, config)
        labels = load_labels(label_path, len(rows))
        metrics = score(rows, labels, config)
        for row in rows:
            row["ground_truth"] = labels.get(row["frame"], "UNLABELED")
        session_dir = args.output / f"{entry['participant']}_{entry['session']}"
        session_dir.mkdir()
        write_csv(session_dir / "frames.csv", rows)
        write_csv(session_dir / "closure_events.csv", metrics["closure_events"],
                  ["start_s", "eligible_s", "end_s", "matched", "delay_s"])
        write_csv(session_dir / "warning_events.csv", metrics["warning_events"],
                  ["time_s", "ground_truth", "result", "reason"])
        sessions.append(dict(**entry, video_sha256=sha256(video), labels_sha256=sha256(label_path),
                             video_metadata=metadata, metrics=metrics))
        write_report(args.output, sessions, config, args.model)
        print(f"  Result: {metrics['alarm_events']}", flush=True)
    (args.output / "COMPLETE").write_text("All manifest sessions processed.\n", encoding="utf-8")
    print(f"Report: {(args.output / 'report.md').resolve()}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, default=Path(__file__).with_name("dataset.csv"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=MODEL)
    parser.add_argument("--threshold-factor", type=float, default=0.75)
    parser.add_argument("--alarm-after", type=float, default=2.0)
    arguments = parser.parse_args()
    try:
        run(arguments)
    except (ValueError, FileNotFoundError, KeyError) as error:
        parser.exit(1, f"Error: {error}\n")
