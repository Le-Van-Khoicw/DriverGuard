"""Plot measured EAR, independent labels, predictions and emitted warnings."""

import argparse
import csv
import json
from pathlib import Path


def plot_run(run):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    if not (run / "COMPLETE").is_file():
        raise ValueError("Run is incomplete; wait for COMPLETE before plotting")
    report = json.loads((run / "report.json").read_text(encoding="utf-8"))
    for session in report["sessions"]:
        directory = run / f"{session['participant']}_{session['session']}"
        with (directory / "frames.csv").open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        times = [float(row["time_s"]) for row in rows]
        ears = [float(row["ear"]) if row["ear"] else float("nan") for row in rows]
        truth = [{"OPEN": 0, "CLOSED": 1}.get(row["ground_truth"], -1) for row in rows]
        predicted = [{"OPEN": 0, "CLOSED": 1}.get(row["eye_state"], -1) for row in rows]
        fig, axes = plt.subplots(3, 1, figsize=(12, 8), sharex=True,
                                 gridspec_kw={"height_ratios": [2, 1, 1]}, layout="constrained")
        axes[0].plot(times, ears, color="#176b92", linewidth=1, label="EAR đo được")
        threshold = session["video_metadata"]["threshold"]
        if threshold is not None:
            axes[0].axhline(threshold, color="#d9700a", linestyle="--", label=f"Ngưỡng {threshold:.3f}")
        axes[0].set_ylabel("EAR")
        axes[0].legend(loc="upper right")
        axes[0].set_title(f"{session['participant']} / {session['session']} — {session['split']} — thử nghiệm một người")

        axes[1].step(times, truth, where="post", linewidth=2, color="#237746", label="Nhãn có sẵn")
        axes[1].step(times, predicted, where="post", linewidth=1, linestyle="--", color="#993c80", label="Dự đoán mắt")
        axes[1].set_yticks([-1, 0, 1], ["Không rõ", "Mở", "Nhắm"])
        axes[1].set_ylim(-1.4, 1.5)
        axes[1].legend(loc="upper right", ncol=2)

        axes[2].step(times, [int(row["alarm_active"] == "True") for row in rows], where="post",
                     color="#176b92", label="Trạng thái cảnh báo")
        for index, event in enumerate(session["metrics"]["closure_events"]):
            axes[2].axvspan(event["eligible_s"], event["end_s"], color="#57a36d", alpha=.2,
                           label="Khoảng cần cảnh báo theo nhãn" if index == 0 else None)
        for outcome, color in (("TP", "#237746"), ("FP", "#bd3a32")):
            events = [event for event in session["metrics"].get("warning_events", []) if event["result"] == outcome]
            if events:
                axes[2].scatter([event["time_s"] for event in events], [1.15] * len(events),
                                marker="v", color=color, label=f"Phát cảnh báo: {outcome}", zorder=5)
        axes[2].set_yticks([0, 1], ["Tắt", "Bật"])
        axes[2].set_ylim(-.1, 1.6)
        axes[2].legend(loc="upper right", fontsize=8, ncol=2)
        axes[2].set_xlabel("Thời gian video (giây) — không phải thời gian xử lý trên điện thoại")
        for axis in axes:
            axis.axvspan(times[0], report["config"]["calibration_s"], color="#888888", alpha=.15)
            axis.grid(alpha=.2)
        fig.savefig(directory / "timeline.png", dpi=150)
        plt.close(fig)
        print(directory / "timeline.png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    plot_run(parser.parse_args().run)
