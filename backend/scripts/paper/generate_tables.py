#!/usr/bin/env python3
"""
scripts/paper/generate_tables.py — Generate LaTeX and Markdown tables from experiment data.

Reads (auto-detects latest files in data/ unless overridden):
  data/latency_<session>.csv       — from measure_latency.py
  data/alarm_log_<session>.json    — exported from Angular localStorage
  data/fp_<participant>_<session>.csv — from false_positive_experiment.py (one or more)

Usage:
  cd backend
  py -3.11 scripts/paper/generate_tables.py
  py -3.11 scripts/paper/generate_tables.py --latency latency_X.csv --alarm alarm_log_X.json
  py -3.11 scripts/paper/generate_tables.py --fp-glob "fp_*.csv"

Outputs (in data/):
  table_latency.md / table_latency.tex
  table_fp.md      / table_fp.tex
"""

import argparse
import csv
import json
import statistics
from datetime import datetime, timezone
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"


# ─── Stats ───────────────────────────────────────────────────────────────────

def compute_stats(values: list[float]) -> dict:
    if not values:
        return {"n": 0, "mean": 0.0, "std": 0.0, "median": 0.0, "min": 0.0, "max": 0.0}
    return {
        "n":      len(values),
        "mean":   statistics.mean(values),
        "std":    statistics.stdev(values) if len(values) > 1 else 0.0,
        "median": statistics.median(values),
        "min":    min(values),
        "max":    max(values),
    }


def f(v: float, d: int = 1) -> str:
    return f"{v:.{d}f}"


# ─── Table 1: Latency ─────────────────────────────────────────────────────────

def build_latency_table(latency_csv: Path, alarm_json: Path | None) -> tuple[str, str]:
    rows = list(csv.DictReader(latency_csv.open(encoding="utf-8")))

    g2h_values = [float(r["gesture_to_http_ms"]) for r in rows if r.get("gesture_to_http_ms")]
    e2a_values: list[float] = []

    if alarm_json and alarm_json.exists():
        alarm_log = json.loads(alarm_json.read_text(encoding="utf-8"))
        alarm_map = {e["eventId"]: int(e["alarmShownAtMs"]) for e in alarm_log}
        for row in rows:
            eid      = row.get("event_id", "")
            created  = row.get("event_created_iso", "")
            if eid and created and eid in alarm_map:
                t_created_ms = datetime.fromisoformat(
                    created.replace("Z", "+00:00")
                ).timestamp() * 1000.0
                e2a_values.append(alarm_map[eid] - t_created_ms)

    s_g2h = compute_stats(g2h_values)
    s_e2a = compute_stats(e2a_values) if e2a_values else None

    # ── Markdown ──
    md  = "### Table 1 — End-to-End Latency (ms)\n\n"
    md += "| Metric | N | Mean | SD | Median | Min | Max |\n"
    md += "|:---|---:|---:|---:|---:|---:|---:|\n"
    md += (f"| Gesture→HTTP response (gesture\\_to\\_http\\_ms) "
           f"| {s_g2h['n']} | {f(s_g2h['mean'])} | {f(s_g2h['std'])} "
           f"| {f(s_g2h['median'])} | {f(s_g2h['min'])} | {f(s_g2h['max'])} |\n")
    if s_e2a:
        md += (f"| Event created→Alarm visible (event\\_to\\_alarm\\_ms) "
               f"| {s_e2a['n']} | {f(s_e2a['mean'])} | {f(s_e2a['std'])} "
               f"| {f(s_e2a['median'])} | {f(s_e2a['min'])} | {f(s_e2a['max'])} |\n")
    else:
        md += "| Event created→Alarm visible | — | *alarm log not provided* | | | | |\n"

    # ── LaTeX ──
    def latex_row(label: str, s: dict) -> str:
        return (f"{label} & {s['n']} & {f(s['mean'])} & {f(s['std'])} "
                f"& {f(s['median'])} & {f(s['min'])} & {f(s['max'])} \\\\\n")

    latex  = "\\begin{table}[htbp]\n\\centering\n"
    latex += ("\\caption{End-to-end latency (ms). "
              "\\emph{Gesture$\\rightarrow$HTTP}: Python-side time from state machine "
              "firing to receiving HTTP 201 from evnt-service. "
              "\\emph{Event$\\rightarrow$Alarm}: server \\texttt{Instant.now()} to Angular "
              "overlay render (includes polling interval $\\leq$5\\,s).}\n")
    latex += "\\label{tab:latency}\n"
    latex += "\\begin{tabular}{lrrrrrr}\n\\toprule\n"
    latex += "Metric & $N$ & Mean & SD & Median & Min & Max \\\\\n\\midrule\n"
    latex += latex_row("Gesture$\\rightarrow$HTTP response", s_g2h)
    if s_e2a:
        latex += latex_row("Event created$\\rightarrow$Alarm visible", s_e2a)
    latex += "\\bottomrule\n\\end{tabular}\n\\end{table}\n"

    return latex, md


# ─── Table 2: False Positive Rate ────────────────────────────────────────────

def build_fp_table(fp_csvs: list[Path]) -> tuple[str, str]:
    participants = []

    for p in sorted(fp_csvs):
        # filename: fp_<participant>_<session>.csv
        parts = p.stem.split("_")
        pid   = parts[1] if len(parts) >= 2 else p.stem

        sm_count     = 0
        simple_count = 0
        max_elapsed  = 0.0

        for row in csv.DictReader(p.open(encoding="utf-8")):
            sm_count     += int(row.get("sm_triggered", 0))
            simple_count += int(row.get("simple_triggered", 0))
            max_elapsed   = max(max_elapsed, float(row.get("elapsed_ms", 0)))

        duration_min = max_elapsed / 60_000.0
        participants.append({
            "id":           pid,
            "duration_min": duration_min,
            "sm":           sm_count,
            "simple":       simple_count,
            "sm_rate":      sm_count     / duration_min if duration_min > 0 else 0.0,
            "simple_rate":  simple_count / duration_min if duration_min > 0 else 0.0,
        })

    if not participants:
        return "", "No FP data found.\n"

    sm_rates     = [p["sm_rate"]     for p in participants]
    simple_rates = [p["simple_rate"] for p in participants]
    s_sm     = compute_stats(sm_rates)
    s_simple = compute_stats(simple_rates)

    # ── Markdown ──
    md  = "### Table 2 — False Positive Rate (triggers/min, naturalistic facial movement)\n\n"
    md += "| Participant | Duration (min) | SM triggers | SM/min | Simple triggers | Simple/min |\n"
    md += "|:---|---:|---:|---:|---:|---:|\n"
    for p in participants:
        md += (f"| {p['id']} | {f(p['duration_min'])} | {p['sm']} | {f(p['sm_rate'], 2)} "
               f"| {p['simple']} | {f(p['simple_rate'], 2)} |\n")
    md += (f"| **Mean ± SD** | — | — | **{f(s_sm['mean'], 2)} ± {f(s_sm['std'], 2)}** "
           f"| — | **{f(s_simple['mean'], 2)} ± {f(s_simple['std'], 2)}** |\n")

    # ── LaTeX ──
    latex  = "\\begin{table}[htbp]\n\\centering\n"
    latex += ("\\caption{False positive rate (triggers/min) during 5 minutes of "
              "naturalistic facial movement per participant (no intentional gestures). "
              "\\emph{SM} = six-state deliberate gesture state machine. "
              "\\emph{Simple} = single-frame EAR average below threshold "
              f"(\\texttt{{EAR\\_CLOSE}}\\,=\\,{_agent_ear_close()}).}}\n")
    latex += "\\label{tab:fp}\n"
    latex += "\\begin{tabular}{lrrrrrr}\n\\toprule\n"
    latex += "Participant & Duration (min) & SM triggers & SM/min & Simple triggers & Simple/min \\\\\n"
    latex += "\\midrule\n"
    for p in participants:
        latex += (f"{p['id']} & {f(p['duration_min'])} & {p['sm']} & {f(p['sm_rate'], 2)} "
                  f"& {p['simple']} & {f(p['simple_rate'], 2)} \\\\\n")
    latex += "\\midrule\n"
    latex += (f"Mean $\\pm$ SD & --- & --- & {f(s_sm['mean'], 2)} $\\pm$ {f(s_sm['std'], 2)} "
              f"& --- & {f(s_simple['mean'], 2)} $\\pm$ {f(s_simple['std'], 2)} \\\\\n")
    latex += "\\bottomrule\n\\end{tabular}\n\\end{table}\n"

    return latex, md


def _agent_ear_close() -> str:
    """Read EAR_CLOSE from the edge agent source without importing MediaPipe."""
    import re
    src = (Path(__file__).parent.parent.parent / "poc-edge-agent-webcam.py").read_text(encoding="utf-8")
    m = re.search(r"EAR_CLOSE\s*=\s*([\d.]+)", src)
    return m.group(1) if m else "0.22"


# ─── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Generate paper tables from NeuroAssist AI experiment data")
    parser.add_argument("--latency", default=None, help="Latency CSV filename (in data/)")
    parser.add_argument("--alarm",   default=None, help="Alarm log JSON filename (in data/)")
    parser.add_argument("--fp-glob", default="fp_*.csv", help="Glob for FP experiment CSVs")
    args = parser.parse_args()

    DATA_DIR.mkdir(exist_ok=True)
    print(f"Data directory: {DATA_DIR}\n")

    # Auto-detect latest latency CSV
    latency_path: Path | None = None
    if args.latency:
        latency_path = DATA_DIR / args.latency
    else:
        candidates = sorted(DATA_DIR.glob("latency_*.csv"), reverse=True)
        if candidates:
            latency_path = candidates[0]
            print(f"Latency file:  {latency_path.name}")

    # Auto-detect latest alarm log
    alarm_path: Path | None = None
    if args.alarm:
        alarm_path = DATA_DIR / args.alarm
    else:
        candidates = sorted(DATA_DIR.glob("alarm_log_*.json"), reverse=True)
        if candidates:
            alarm_path = candidates[0]
            print(f"Alarm log:     {alarm_path.name}")

    # FP files
    fp_paths = sorted(DATA_DIR.glob(args.fp_glob))
    if fp_paths:
        print(f"FP files:      {[p.name for p in fp_paths]}")

    print()
    sep = "═" * 64

    # ── Latency table ──
    if latency_path and latency_path.exists():
        latex, md = build_latency_table(latency_path, alarm_path)
        print(sep); print("LATENCY — MARKDOWN"); print(sep); print(md)
        print(sep); print("LATENCY — LATEX");    print(sep); print(latex)
        (DATA_DIR / "table_latency.md").write_text(md,    encoding="utf-8")
        (DATA_DIR / "table_latency.tex").write_text(latex, encoding="utf-8")
        print(f"Saved: data/table_latency.md  data/table_latency.tex\n")
    else:
        print("No latency data found. Run measure_latency.py first.\n")

    # ── FP table ──
    if fp_paths:
        latex, md = build_fp_table(fp_paths)
        print(sep); print("FALSE POSITIVE — MARKDOWN"); print(sep); print(md)
        print(sep); print("FALSE POSITIVE — LATEX");    print(sep); print(latex)
        (DATA_DIR / "table_fp.md").write_text(md,    encoding="utf-8")
        (DATA_DIR / "table_fp.tex").write_text(latex, encoding="utf-8")
        print(f"Saved: data/table_fp.md  data/table_fp.tex\n")
    else:
        print("No FP data found. Run false_positive_experiment.py first.\n")


if __name__ == "__main__":
    main()
