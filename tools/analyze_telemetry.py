#!/usr/bin/env python3
"""Summarize NeuroLab JSONL locally, using only the Python standard library."""

import argparse
import csv
from collections import deque
from html import escape
import json
import math
from pathlib import Path

METRICS = ("spikes", "synapticEvents", "realTimeFactor", "bodyForward", "pain", "reward")
NEURAL_METRICS = {"spikes", "synapticEvents"}
FIELDS = ("runId", "entityUuid", "dimension", "mode", "trialLabel", "trialStartedAt",
          "samples", "neuralSnapshots", "firstGameTick", "lastGameTick",
          "meanSpikes", "meanSynapticEvents", "meanRealTimeFactor", "minRealTimeFactor",
          "meanBodyForward", "meanPain", "meanReward", "reflexFraction",
          "meanHorizontalDisplacement", "meanVerticalDisplacement", "meanSpeed",
          "positionIntervals", "observedDistance", "observedGameTicks")
POSITION_METRICS = ("horizontalDisplacement", "verticalDisplacement", "actualSpeed")


def number(record, key):
    value = record.get(key)
    if isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value):
        return value
    return None


class Group:
    def __init__(self, key):
        self.key = key
        self.samples = self.neural_snapshots = self.reflex_samples = 0
        self.first = self.last = self.last_neural = None
        self.sums = {key: 0.0 for key in METRICS}
        self.sums.update({key: 0.0 for key in POSITION_METRICS})
        self.counts = {key: 0 for key in METRICS}
        self.counts.update({key: 0 for key in POSITION_METRICS})
        self.min_rtf = None
        self.movement_ticks = 0
        self.movement_distance = 0
        self.points = deque(maxlen=600)

    def add(self, record):
        tick = number(record, "gameTick")
        if tick is None:
            raise ValueError("sample has no finite gameTick")
        if self.last is not None and tick < self.last:
            raise ValueError("gameTick moved backwards within one run/entity/condition")
        previous_tick = self.last
        self.first = tick if self.first is None else self.first
        self.last = tick
        self.samples += 1
        neural_tick = number(record, "neuralTick")
        fresh = neural_tick is None or neural_tick != self.last_neural
        if neural_tick is not None:
            if self.last_neural is not None and neural_tick < self.last_neural:
                raise ValueError("neuralTick moved backwards within one run/entity/condition")
            if fresh:
                self.neural_snapshots += 1
            self.last_neural = neural_tick
        for key in METRICS:
            value = number(record, key)
            if value is not None and (key not in NEURAL_METRICS or fresh):
                self.sums[key] += value
                self.counts[key] += 1
        delta_ticks = number(record, "positionDeltaTicks")
        dx, dy, dz = (number(record, key) for key in ("deltaX", "deltaY", "deltaZ"))
        horizontal = vertical = speed = None
        continuous = record.get("positionDeltaValid") is True and delta_ticks is not None \
                and 0 < delta_ticks <= 200 and previous_tick is not None and tick - previous_tick == delta_ticks \
                and dx is not None and dy is not None and dz is not None \
                and all(number(record, k) is not None and number(self.points[-1], k) is not None
                        for k in ("x", "y", "z"))
        if continuous:
            horizontal = math.hypot(dx, dz)
            vertical = dy
            speed = math.sqrt(dx * dx + dy * dy + dz * dz) * 20 / delta_ticks
            self.movement_distance += math.hypot(dx, dy, dz)
            self.movement_ticks += delta_ticks
            for key, value in zip(POSITION_METRICS, (horizontal, vertical, speed)):
                self.sums[key] += value
                self.counts[key] += 1
        rtf = number(record, "realTimeFactor")
        if rtf is not None:
            self.min_rtf = rtf if self.min_rtf is None else min(self.min_rtf, rtf)
        self.reflex_samples += record.get("reflex") is True
        point = {key: record.get(key) for key in (*METRICS, "gameTick", "x", "y", "z")}
        point["positionContinuous"] = continuous
        self.points.append(point)

    def mean(self, key):
        return self.sums[key] / self.counts[key] if self.counts[key] else None

    def row(self):
        return dict(zip(FIELDS, (*self.key, self.samples, self.neural_snapshots,
                    self.first, self.last, self.mean("spikes"), self.mean("synapticEvents"),
                    self.mean("realTimeFactor"), self.min_rtf, self.mean("bodyForward"),
                    self.mean("pain"), self.mean("reward"), self.reflex_samples / self.samples,
                    self.mean("horizontalDisplacement"), self.mean("verticalDisplacement"),
                    self.movement_distance * 20 / self.movement_ticks if self.movement_ticks else None,
                    self.counts["actualSpeed"], self.movement_distance, self.movement_ticks)))


def analyze(path):
    groups, sessions, ended = {}, {}, set()
    warnings = set()
    with Path(path).open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                if not isinstance(record, dict):
                    raise ValueError("expected a JSON object")
                event = record.get("event", "sample")
                run = record.get("runId", "legacy")
                if event == "session_start":
                    sessions[run] = record
                elif event == "session_end":
                    ended.add(run)
                    if record.get("droppedRecords", 0):
                        warnings.add(f"Run {run}: {record['droppedRecords']} records were dropped.")
                elif event == "sample":
                    if record.get("schemaVersion", 1) not in (1, 2, 3, 4, 5, 6):
                        raise ValueError("unsupported telemetry schema")
                    if not record.get("entityUuid"):
                        raise ValueError("sample has no entityUuid")
                    if "runId" not in record:
                        warnings.add("Legacy log: separate sessions cannot be identified reliably.")
                    if number(record, "neuralTick") is None:
                        warnings.add("Missing neuralTick: neural means include repeated snapshots; unique count is unknown.")
                    key = (run, record["entityUuid"], record.get("dimension", "unknown"),
                           record.get("mode", "unknown"), record.get("trialLabel"), record.get("trialStartedAt"))
                    group = groups.setdefault(key, Group(key))
                    group.add(record)
            except (ValueError, TypeError) as problem:
                raise ValueError(f"{path}:{line_number}: {problem}") from problem
    for run in sessions.keys() - ended:
        warnings.add(f"Run {run} has no session_end; it may still be recording or have stopped unexpectedly.")
    for run in {group.key[0] for group in groups.values()} - sessions.keys():
        warnings.add(f"Run {run} has no session_start metadata.")
    return list(groups.values()), sessions, sorted(warnings)


def fmt(value):
    return "—" if value is None else f"{value:.4g}" if isinstance(value, float) else str(value)


def chart(group, metric):
    points = [(p["gameTick"], number(p, metric)) for p in group.points]
    valid = [(tick, value) for tick, value in points if value is not None]
    if not valid:
        return f"<figure><figcaption>{metric}</figcaption><p>No values recorded</p></figure>"
    x0, x1 = points[0][0], points[-1][0]
    ymax = max(1, max(value for _, value in valid))
    # Missing values break the trace instead of inventing a connection.
    paths, segment = [], []
    for tick, value in points:
        if value is None:
            if segment:
                paths.append(" ".join(segment))
            segment = []
            continue
        x = 40 + (tick - x0) / max(1, x1 - x0) * 680
        y = 130 - value / ymax * 110
        segment.append(f"{x:.2f},{y:.2f}")
    if segment:
        paths.append(" ".join(segment))
    lines = "".join(f'<polyline points="{p}"/>' for p in paths)
    return (f'<figure><figcaption>{metric} · mean {fmt(group.mean(metric))}</figcaption>'
            f'<svg viewBox="0 0 760 165" role="img" aria-label="{metric} by server game tick">'
            f'<text x="4" y="20">{ymax:.3g}</text><text x="14" y="134">0</text>'
            f'<path d="M40 15V130H725"/>{lines}'
            f'<text x="40" y="155">{x0:g}</text><text x="590" y="155">tick {x1:g}</text>'
            '</svg></figure>')


def position_runs(group, axes):
    """Keep missing records and discontinuities visible instead of connecting unobserved motion."""
    runs, current = [], []
    for point in group.points:
        coords = tuple(number(point, axis) for axis in axes)
        if any(value is None for value in coords):
            if current:
                runs.append(current)
            current = []
        elif point["positionContinuous"]:
            current.append((point["gameTick"], coords))
        else:
            if current:
                runs.append(current)
            current = [(point["gameTick"], coords)]
    if current:
        runs.append(current)
    return runs


def trajectory_chart(group):
    points = [(number(p, "x"), number(p, "z"), number(p, "gameTick")) for p in group.points]
    valid = [(x, z, tick) for x, z, tick in points if x is not None and z is not None and tick is not None]
    if len(valid) < 2:
        return "<figure><figcaption>Measured X/Z path</figcaption><p>Not enough position samples recorded</p></figure>"
    min_x, max_x = min(p[0] for p in valid), max(p[0] for p in valid)
    min_z, max_z = min(p[1] for p in valid), max(p[1] for p in valid)
    span_x, span_z = max(max_x - min_x, 0.5), max(max_z - min_z, 0.5)
    scale = min(520 / span_x, 240 / span_z)
    offset_x = (600 - span_x * scale) / 2
    offset_z = (280 - span_z * scale) / 2
    def project(x, z):
        return offset_x + (x - min_x) * scale, 20 + offset_z + (z - min_z) * scale
    segments = [" ".join(f"{project(x, z)[0]:.2f},{project(x, z)[1]:.2f}" for _, (x, z) in run)
                for run in position_runs(group, ("x", "z"))]
    lines = "".join(f'<polyline points="{path}"/>' for path in segments)
    start, finish = project(*valid[0][:2]), project(*valid[-1][:2])
    return ("<figure><figcaption>Measured mob path · world X/Z plane</figcaption>"
            '<svg viewBox="0 0 600 320" role="img" aria-label="Measured mob trajectory in world coordinates">'
            '<path d="M40 300H580M40 20V300"/><text x="540" y="315">X</text><text x="12" y="28">Z</text>'
            f"{lines}<circle cx=\"{start[0]:.2f}\" cy=\"{start[1]:.2f}\" r=\"6\" class=\"start\"/>"
            f"<circle cx=\"{finish[0]:.2f}\" cy=\"{finish[1]:.2f}\" r=\"6\" class=\"finish\"/>"
            f'<text x="40" y="315">X {min_x:.1f} to {max_x:.1f} · Z {min_z:.1f} to {max_z:.1f}</text>'
            "<text x=\"48\" y=\"18\" class=\"start\">START</text><text x=\"112\" y=\"18\" class=\"finish\">FINISH</text>"
            "</svg></figure>")


def altitude_chart(group):
    points = [(number(p, "gameTick"), number(p, "y")) for p in group.points]
    valid = [(tick, value) for tick, value in points if tick is not None and value is not None]
    if len(valid) < 2:
        return "<figure><figcaption>Measured altitude</figcaption><p>Not enough position samples recorded</p></figure>"
    lo = min(value for _, value in valid)
    hi = max(value for _, value in valid)
    x0, x1 = valid[0][0], valid[-1][0]
    span = max(hi - lo, 0.5)
    paths = [" ".join(f"{40 + (tick - x0) / max(1, x1 - x0) * 680:.2f},"
                      f"{130 - (coords[0] - lo) / span * 110:.2f}" for tick, coords in run)
             for run in position_runs(group, ("y",))]
    lines = "".join(f'<polyline points="{path}"/>' for path in paths)
    return ("<figure><figcaption>Measured world altitude · mean "
            f"{fmt(sum(v for _, v in valid) / len(valid))}</figcaption>"
            '<svg viewBox="0 0 760 165" role="img" aria-label="Mob world altitude over game ticks">'
            f"<text x=\"4\" y=\"20\">{lo + span:.3g}</text><text x=\"4\" y=\"130\">{lo:.3g}</text>"
            f"<path d=\"M40 15V130H725\"/>{lines}"
            f"<text x=\"40\" y=\"155\">{x0:g}</text><text x=\"590\" y=\"155\">tick {x1:g}</text>"
            "</svg></figure>")


def write_report(source, output):
    groups, sessions, warnings = analyze(source)
    if not groups:
        raise ValueError("No telemetry samples found; record a trial first")
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    with (output / "summary.csv").open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(group.row() for group in groups)
    sections = []
    for index, group in enumerate(groups, 1):
        row = group.row()
        title = f"{index}. {row['mode']} / {row['trialLabel'] or 'unlabeled'}"
        metadata = " · ".join(f"{key}: {fmt(row[key])}" for key in
                              ("runId", "entityUuid", "dimension", "trialStartedAt", "samples", "neuralSnapshots"))
        sections.append(f"<section><h2>{escape(title)}</h2><p>{escape(metadata)}</p>"
                        + "<div class='plots'>" + "".join(chart(group, m) for m in METRICS)
                        + trajectory_chart(group) + altitude_chart(group) + "</div></section>")
    notes = "".join(f"<li>{escape(w)}</li>" for w in warnings) or "<li>No session-integrity warnings detected.</li>"
    session_info = escape(json.dumps(sessions, indent=2, ensure_ascii=False))
    html = """<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" href="data:,"><title>NeuroLab experiment report</title>
<style>body{background:#101820;color:#e5eef5;font:16px system-ui;max-width:1280px;margin:auto;padding:24px}
h1,h2{color:#80d6ed}p,li{line-height:1.6;overflow-wrap:anywhere}section{border-top:1px solid #415160;margin-top:32px}
.plots{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,400px),1fr));gap:16px}
figure{margin:0;padding:16px;background:#192530;border-radius:8px}svg{width:100%;height:auto}
polyline{fill:none;stroke:#80d6ed;stroke-width:2}path{fill:none;stroke:#667988}text{fill:#b5c7d4;font-size:12px}
.start{fill:#72e7ac}.finish{fill:#ffb56c}
pre{white-space:pre-wrap;overflow-wrap:anywhere}@media print{body{background:white;color:black}section{break-before:page}}
</style><h1>NeuroLab experiment report</h1>"""
    html += f"<p>Source: {escape(str(source))}</p>"
    html += ("<p>Charts show the last 600 samples per condition; summaries use all valid samples. "
             "The x-axis is server game ticks, not elapsed wall-clock seconds. "
             "Body commands are requested actions, not measured displacement. "
             "World position, X/Z trajectory, and altitude are measured from server-side entity coordinates; "
             "the CSV reports mean horizontal and vertical displacement plus speed, excluding invalid or long gaps. "
             "Spikes and synaptic events describe sampled 50 ms neural steps; they are not run totals. "
             "Repeated neural ticks are counted once in neural means when tick IDs are available.</p>"
             f"<ul>{notes}</ul>" + "".join(sections)
             + f"<details><summary>Recorded session metadata</summary><pre>{session_info}</pre></details></html>")
    (output / "report.html").write_text(html, encoding="utf-8")
    return len(groups)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        count = write_report(args.log, args.output)
    except (OSError, ValueError) as problem:
        parser.exit(1, f"Analysis failed: {problem}\n")
    print(f"Wrote {count} condition groups to {args.output / 'report.html'} and summary.csv")


if __name__ == "__main__":
    main()
