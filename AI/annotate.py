"""Frame-by-frame labels for new participants. Never derive labels from EAR."""

import argparse
import csv
from pathlib import Path


def save(path, labels):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["frame", "label"])
        writer.writerows(sorted(labels.items()))
    temporary.replace(path)


def annotate(video, output, start_frame):
    import cv2
    from evaluate import load_labels

    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise ValueError(f"Cannot open {video}")
    total = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    labels = load_labels(output, total) if output.exists() else {}
    index = start_frame if start_frame is not None else next((i for i in range(total) if i not in labels), total)
    if index >= total:
        capture.release()
        print("All frames are labeled. Use --start-frame 0 to review existing decisions.")
        return
    if index < 0:
        capture.release()
        raise ValueError("start-frame must not be negative")
    previous = labels.get(index - 1)
    window = "DriverGuard labels: O / C / U | SPACE repeat | B back | Q save + exit"
    try:
        while index < total:
            capture.set(cv2.CAP_PROP_POS_FRAMES, index)
            ok, frame = capture.read()
            if not ok:
                raise ValueError(f"Cannot decode frame {index}")
            timestamp = capture.get(cv2.CAP_PROP_POS_MSEC) / 1000
            scale = min(1, 1100 / frame.shape[1], 750 / frame.shape[0])
            display = cv2.resize(frame, None, fx=scale, fy=scale)
            text = f"Frame {index}/{total-1} | {timestamp:.3f}s | label: {labels.get(index, 'UNLABELED')}"
            cv2.rectangle(display, (0, 0), (display.shape[1], 75), (0, 0, 0), -1)
            cv2.putText(display, text, (12, 28), cv2.FONT_HERSHEY_SIMPLEX, .55, (255, 255, 255), 1)
            cv2.putText(display, "O OPEN | C CLOSED | U UNKNOWN | SPACE repeat | B back | Q save", (12, 57),
                        cv2.FONT_HERSHEY_SIMPLEX, .45, (0, 255, 255), 1)
            cv2.imshow(window, display)
            while True:
                key = cv2.waitKey(100) & 0xFF
                if key in (ord("q"), ord("Q"), 27) or cv2.getWindowProperty(window, cv2.WND_PROP_VISIBLE) < 1:
                    return
                if key in (ord("b"), ord("B"), 8):
                    index = max(0, index - 1)
                    previous = labels.get(index - 1)
                    break
                label = {ord("o"): "OPEN", ord("c"): "CLOSED", ord("u"): "UNKNOWN"}.get(key)
                if key == 32:
                    label = previous
                if label is not None:
                    labels[index] = previous = label
                    index += 1
                    if index % 50 == 0:
                        save(output, labels)
                    break
    finally:
        capture.release()
        cv2.destroyAllWindows()
        save(output, labels)
        print(f"Saved {len(labels)} labels to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--start-frame", type=int)
    args = parser.parse_args()
    annotate(args.video, args.output, args.start_frame)
