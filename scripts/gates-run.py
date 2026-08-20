#!/usr/bin/env python3
"""gates-run.py - run the toolchain and emit gate verdicts (D-366, Fase 1).

Closes the loop left open by D-317: `validator-score.py` is designed to RECEIVE
gate verdicts (`--gates gates.json`) and deliberately never runs the toolchain.
Until now nothing produced that JSON — the agent was the glue, running
`npx nx lint/test/build` (or `./gradlew ...`), eyeballing `tail -20`, and
translating to `--lint pass`. This script is that missing producer.

It is the ONE script that executes the toolchain (by design the validator does
not). It runs each requested gate, derives a verdict from the exit code, and
emits the flat `{"lint":"pass","test":"pass",...}` JSON that
`validator-score.py collect_gates` consumes verbatim.

Verdict rule (deterministic):
    tool missing       -> "skip"   (e.g. e2e not configured)
    exit code == 0     -> "pass"
    exit code != 0     -> "fail"

Usage:
    gates-run.py run --layer fe --project lead --gates lint,test,build,e2e
    gates-run.py run --layer be --project obras-service --gates test,build --output gates.json
    # then:
    validator-score.py score --issue LEAD-FE-001 --gates gates.json

Exit codes (R3): 0 success | 1 user error | 2 a gate FAILED (so CI can branch).
Note: a failing gate is exit 2; producing the JSON still succeeds (it is written).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

from cli_utils import force_utf8_stdio

_GATES = ("lint", "test", "build", "e2e")
_VERDICTS = ("pass", "fail", "skip")

# Per-layer mapping: gate -> toolchain command (project substituted at runtime).
# fe: Nx targets (`nx <target> <project>`). be: Gradle tasks (`:<project>:<task>`).
_FE_TARGET = {"lint": "lint", "test": "test", "build": "build", "e2e": "e2e"}
_BE_TASK = {"lint": "spotlessCheck", "test": "test", "build": "build", "e2e": "integrationTest"}


def _be_gradlew(cwd: Path) -> Path:
    """Locate gradlew in <cwd>/backend/ (monorepo layout)."""
    backend = cwd / "backend"
    return backend / ("gradlew.bat" if os.name == "nt" else "gradlew")


def gate_command(layer: str, gate: str, project: str, cwd: Path | None = None) -> list[str]:
    """Toolchain argv for one gate. Pure (no execution)."""
    if layer == "fe":
        return ["npx", "nx", _FE_TARGET[gate], project]
    root = cwd or Path.cwd()
    gradlew = str(_be_gradlew(root))
    return [gradlew, f":services:{project}:{_BE_TASK[gate]}"]


def verdict_for(returncode: int | None, found: bool) -> str:
    """Map an execution outcome to a gate verdict."""
    if not found:
        return "skip"
    return "pass" if returncode == 0 else "fail"


def _subprocess_runner(cmd: list[str], cwd: Path) -> tuple[int | None, bool, str]:
    """Run cmd; return (returncode, found, tail). found=False if the tool is absent."""
    try:
        proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                              encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return None, False, ""
    tail = (proc.stdout + proc.stderr)[-2000:]
    return proc.returncode, True, tail


def run_gates(layer: str, project: str, gates: list[str], cwd: Path, runner=_subprocess_runner) -> dict:
    """Run each gate and return {gate: verdict}. `runner` is injectable for tests."""
    # For BE layer, Gradle wrapper lives in <cwd>/backend/ — run from there.
    effective_cwd = (cwd / "backend") if layer == "be" else cwd
    verdicts: dict[str, str] = {}
    for gate in gates:
        cmd = gate_command(layer, gate, project, cwd)
        rc, found, tail = runner(cmd, effective_cwd)
        # Gradle exits non-zero when a task doesn't exist; treat as skip, not fail.
        if rc != 0 and found and "not found in project" in tail and gate == "e2e":
            verdicts[gate] = "skip"
        else:
            verdicts[gate] = verdict_for(rc, found)
    return verdicts


def _write_atomic(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def cmd_run(args: argparse.Namespace) -> int:
    gates = [g.strip() for g in args.gates.split(",") if g.strip()]
    bad = [g for g in gates if g not in _GATES]
    if bad:
        print(f"error: unknown gate(s) {bad}; valid: {', '.join(_GATES)}", file=sys.stderr)
        return 1
    cwd = Path(args.root).resolve() if args.root else Path.cwd()

    verdicts = run_gates(args.layer, args.project, gates, cwd)

    if args.output:
        _write_atomic(Path(args.output), verdicts)
        dest = args.output
    else:
        print(json.dumps(verdicts, indent=2))
        dest = "(stdout)"

    failed = [g for g, v in verdicts.items() if v == "fail"]
    if args.report:
        summary = " ".join(f"{g}={v}" for g, v in verdicts.items())
        print(f"gates [{args.layer}:{args.project}] {summary} -> {dest}", file=sys.stderr)
    if failed:
        print(f"error: gate(s) failed: {', '.join(failed)} (verdicts written)", file=sys.stderr)
        return 2
    print(f"OK: gates {verdicts} -> {dest}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="gates-run.py", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    pr = sub.add_parser("run", help="run toolchain gates and emit verdict JSON")
    pr.add_argument("--layer", required=True, choices=["fe", "be"])
    pr.add_argument("--project", required=True, help="Nx project (fe) or gradle service (be)")
    pr.add_argument("--gates", required=True, help="comma list: lint,test,build,e2e")
    pr.add_argument("--root", metavar="PATH", help="project root (default: cwd)")
    pr.add_argument("--output", metavar="PATH", help="write verdicts JSON (default: stdout)")
    pr.add_argument("--report", action="store_true", help="print a one-line summary to stderr")
    pr.set_defaults(func=cmd_run)
    return p


def main(argv: list[str] | None = None) -> int:
    force_utf8_stdio()
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
