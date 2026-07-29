#!/usr/bin/env python3
"""validator-decide.py - deterministic next-action engine for the exec loop (D-367, Fase 1).

The PLANNER / IMPLEMENTADOR / VALIDACAO / ENCERRAR decision is a state machine
the validator re-derives from prose every round, and the autopilot DUPLICATES in
its B2 step. Its inputs are already machine-readable JSON: `<ISSUE>.state.json`
(validator-score, D-317: checklist + score) and `<ISSUE>.plan-state.json`
(plan-state-extractor, D-319: prior plan + delta). This script reads them and
emits the decision so the agent CONFIRMS instead of recomputing (R5/D-315:
`needsAgentConfirm: true`).

Decision rule (deterministic):
    checklist 100% DONE  AND  score >= 80          -> ENCERRAR
    no current plan                                -> PLANNER
    plan exists, work remaining (undone/notStarted)-> IMPLEMENTADOR
    plan exists, all implemented but score < 80    -> VALIDACAO

Usage:
    validator-decide.py decide --issue LEAD-FE-001
    validator-decide.py decide --issue CAD-001 --layer be --report
    validator-decide.py decide --issue X --state s.json --plan-state p.json

Exit codes (R3): 0 success | 1 user error (state.json missing). A decision is
never an error; the score/checklist drive the action, not the exit code.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from cli_utils import force_utf8_stdio
from issue_state import find_root, resolve_issue

ENCERRAR, PLANNER, IMPLEMENTADOR, VALIDACAO = "ENCERRAR", "PLANNER", "IMPLEMENTADOR", "VALIDACAO"
SCORE_DONE_THRESHOLD = 80


def decide(state: dict, plan_state: dict | None) -> dict:
    """Pure decision over state.json (+ optional plan-state.json)."""
    checklist = state.get("checklist", {})
    total = int(checklist.get("total", 0) or 0)
    done = int(checklist.get("done", 0) or 0)
    score = int((state.get("score") or {}).get("total", 0) or 0)

    plan = (plan_state or {}).get("priorPlan", {}) if plan_state else {}
    plan_exists = bool(plan.get("exists", False))
    delta = (plan_state or {}).get("delta", {}) if plan_state else {}
    not_started = list(delta.get("notStarted", []) or [])

    if total > 0 and done == total and score >= SCORE_DONE_THRESHOLD:
        action, reason = ENCERRAR, (
            f"checklist {done}/{total} DONE e score {score} >= {SCORE_DONE_THRESHOLD}"
        )
    elif not plan_exists:
        action, reason = PLANNER, "sem plano vigente para esta issue"
    elif done < total or not_started:
        remaining = (total - done) if total else len(not_started)
        action, reason = IMPLEMENTADOR, (
            f"plano vigente com trabalho nao concluido ({remaining} item(ns) pendente(s))"
        )
    else:
        action, reason = VALIDACAO, (
            f"tudo implementado mas score {score} < {SCORE_DONE_THRESHOLD}; validar para fechar"
        )

    return {
        "nextAction": action,
        "reason": reason,
        "needsAgentConfirm": True,
        "inputs": {
            "done": done, "total": total, "score": score,
            "planExists": plan_exists, "notStarted": len(not_started),
        },
    }


def _sidecar(issue_file: Path, suffix: str) -> Path:
    return issue_file.with_name(f"{issue_file.stem}.{suffix}.json")


def _load_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def cmd_decide(args: argparse.Namespace) -> int:
    root = find_root(args.root)
    layer, issue_file = resolve_issue(args.issue, args.layer, args.issue_file, root)

    # Resolve sidecars: explicit flags win, else next to the issue file.
    if args.state:
        state_path = Path(args.state)
    elif issue_file is not None:
        state_path = _sidecar(issue_file, "state")
    else:
        print(f"error: issue {args.issue!r} not found and --state not given", file=sys.stderr)
        return 1

    state = _load_json(state_path)
    if state is None:
        print(f"error: state.json not found/readable: {state_path} "
              f"(run validator-score.py score first)", file=sys.stderr)
        return 1

    if args.plan_state:
        plan_path = Path(args.plan_state)
    elif issue_file is not None:
        plan_path = _sidecar(issue_file, "plan-state")
    else:
        plan_path = None
    plan_state = _load_json(plan_path) if plan_path else None

    result = decide(state, plan_state)
    result = {"issue": args.issue, "layer": layer, **result}

    out = json.dumps(result, indent=2, ensure_ascii=False)
    if args.output:
        Path(args.output).write_text(out + "\n", encoding="utf-8")
    if args.report or not args.output:
        print(out)
    print(f"OK: {result['nextAction']} -- {result['reason']}", file=sys.stderr)
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="validator-decide.py", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    pd = sub.add_parser("decide", help="emit next action from state.json (+ plan-state.json)")
    pd.add_argument("--issue", required=True, metavar="ISSUE_ID")
    pd.add_argument("--layer", choices=["fe", "be"], help="default: autodetect")
    pd.add_argument("--issue-file", metavar="PATH", help="override issue file resolution")
    pd.add_argument("--state", metavar="PATH", help="override <ISSUE>.state.json path")
    pd.add_argument("--plan-state", metavar="PATH", help="override <ISSUE>.plan-state.json path")
    pd.add_argument("--root", metavar="PATH", help="project root (default: walk up from cwd)")
    pd.add_argument("--output", metavar="PATH", help="write decision JSON (default: stdout)")
    pd.add_argument("--report", action="store_true", help="also print decision JSON to stdout")
    pd.set_defaults(func=cmd_decide)
    return p


def main(argv: list[str] | None = None) -> int:
    force_utf8_stdio()
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
