#!/usr/bin/env python3
"""autopilot-state.py - autopilot checkpoint + arg detection + iteration cap (D-370, Fase 2).

The autopilots serialize their `state.json` checkpoint BY HAND every heavy step,
re-parse `$ARGUMENTS`, and increment the safety-cap counter in prose — the exact
"JSON manipulated manually" anti-pattern. This centralizes all three.

  detect "<ARGUMENTS>" [--layer be|fe]
      Parse the invocation into {module, id, kind, mode, pipeline, service}.
  load  --dir <ckpt-dir>
      Print the saved checkpoint JSON (or {} if none).
  save  --dir <ckpt-dir> --step <s> --next <n> [--module ..] [--service ..]
        [--pipeline ..] [--progress ..] [--notes ..] [--inc-iteration]
      Atomically write the checkpoint; --inc-iteration bumps the counter and
      flags `capReached` once it exceeds the safety cap (5).

Exit codes (R3): 0 success | 1 user error | 2 cap reached (on save --inc-iteration).
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
from pathlib import Path

from cli_utils import force_utf8_stdio

ITERATION_CAP = 5
_ISSUE_RE = re.compile(r"^(?P<prefix>[A-Z][A-Z0-9]*)-(?P<kind>FE|BE|INT)-(?P<num>\d+)$")
_SHORT_ISSUE_RE = re.compile(r"^(?P<prefix>[A-Z][A-Z0-9]*)-(?P<num>\d+)$")


def _now() -> str:
    return _dt.datetime.now().isoformat(timespec="seconds")


def _resolve_service(module: str) -> str | None:
    try:
        from be_context_helper import find_project_root, load_project_context
        root = find_project_root()
        if root is None:
            return None
        ctx = load_project_context(root / "project-context.json")
        m = ctx.module_for_prefix(module)
        return m.get("backendService") if m else None
    except Exception:
        return None


def detect(arguments: str, layer: str) -> dict:
    """Parse an autopilot invocation string into a structured plan."""
    tokens = arguments.split()
    is_resume = "--resume" in tokens
    positional = [t for t in tokens if not t.startswith("-")]
    head = positional[0] if positional else ""

    issue_id = None
    kind = None
    module = head
    pipeline = "architect"  # bare module -> architect; issue id -> implement

    m = _ISSUE_RE.match(head)
    if m:
        issue_id, module, kind = head, m.group("prefix"), m.group("kind")
        pipeline = "implement"
    else:
        m2 = _SHORT_ISSUE_RE.match(head)
        if m2:
            issue_id, module = head, m2.group("prefix")
            pipeline = "implement"

    return {
        "module": module,
        "id": issue_id or module,
        "kind": kind,
        "mode": "resume" if is_resume else "fresh",
        "pipeline": pipeline,
        "service": _resolve_service(module),
        "layer": layer,
    }


def _state_path(ckpt_dir: Path) -> Path:
    return ckpt_dir / "state.json"


def load_checkpoint(ckpt_dir: Path) -> dict:
    p = _state_path(ckpt_dir)
    if not p.is_file():
        return {}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def save_checkpoint(ckpt_dir: Path, updates: dict, inc_iteration: bool) -> dict:
    state = load_checkpoint(ckpt_dir)
    state.update({k: v for k, v in updates.items() if v is not None})
    if inc_iteration:
        state["iteration"] = int(state.get("iteration", 0)) + 1
    state["timestamp"] = _now()
    state["capReached"] = int(state.get("iteration", 0)) > ITERATION_CAP

    p = _state_path(ckpt_dir)
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(tmp, p)
    return state


def cmd_detect(args) -> int:
    print(json.dumps(detect(args.arguments, args.layer), indent=2, ensure_ascii=False))
    return 0


def cmd_load(args) -> int:
    print(json.dumps(load_checkpoint(Path(args.dir)), indent=2, ensure_ascii=False))
    return 0


def cmd_save(args) -> int:
    updates = {
        "id": args.module, "module": args.module, "service": args.service,
        "pipeline": args.pipeline, "currentStep": args.step, "nextStep": args.next,
        "progressUnit": args.progress, "notes": args.notes,
    }
    state = save_checkpoint(Path(args.dir), updates, args.inc_iteration)
    print(json.dumps(state, indent=2, ensure_ascii=False))
    if state.get("capReached"):
        print(f"error: safety cap atingido (iteration={state['iteration']} > {ITERATION_CAP})",
              file=sys.stderr)
        return 2
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="autopilot-state.py", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    pd = sub.add_parser("detect", help="parse $ARGUMENTS into a structured plan")
    pd.add_argument("arguments", help="the raw invocation string")
    pd.add_argument("--layer", default="be", choices=["be", "fe"])
    pd.set_defaults(func=cmd_detect)

    pl = sub.add_parser("load", help="print the saved checkpoint")
    pl.add_argument("--dir", required=True, metavar="CKPT_DIR")
    pl.set_defaults(func=cmd_load)

    ps = sub.add_parser("save", help="write the checkpoint atomically")
    ps.add_argument("--dir", required=True, metavar="CKPT_DIR")
    ps.add_argument("--step", required=True)
    ps.add_argument("--next", required=True)
    ps.add_argument("--module")
    ps.add_argument("--service")
    ps.add_argument("--pipeline")
    ps.add_argument("--progress")
    ps.add_argument("--notes")
    ps.add_argument("--inc-iteration", action="store_true")
    ps.set_defaults(func=cmd_save)

    return p


def main(argv: list[str] | None = None) -> int:
    force_utf8_stdio()
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
