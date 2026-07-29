#!/usr/bin/env python3
"""arch-conformance.py - static architecture-rule checker (D-369, Fase 2).

The validator and implement commands ask the agent to verify ~14-16 architecture
rules BY EYE, duplicated across be/fe. Most are grep-able. This runs them
deterministically and emits `conformance.json` the validator merges, so the
agent stops eyeballing static checks. Rule profiles are data, not `if layer`
scattered around.

  check --layer fe|be --target <dir> [--output conformance.json] [--report]

FE (*.component.ts / *.ts): OnPush, standalone, no HttpClient in component,
  no localStorage/sessionStorage, no console.log, no cross-project ../../../ import.
BE (*.java): no field @Autowired, no System.out, DDL not altered outside migrations.

Exit codes (R3): 0 clean | 1 user error | 2 blocking violation(s).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

from cli_utils import force_utf8_stdio

# ── FE patterns ───────────────────────────────────────────────────────────────
_ON_PUSH = re.compile(r"ChangeDetectionStrategy\s*\.\s*OnPush")
_STANDALONE = re.compile(r"standalone\s*:\s*true")
_HTTPCLIENT = re.compile(r"\bHttpClient\b")
_WEB_STORAGE = re.compile(r"\b(localStorage|sessionStorage)\b")
_CONSOLE_LOG = re.compile(r"\bconsole\.(log|debug)\s*\(")
_CROSS_PROJECT = re.compile(r"""from\s+['"](\.\./){3,}""")

# ── BE patterns ───────────────────────────────────────────────────────────────
_FIELD_AUTOWIRED = re.compile(r"@Autowired\s+(private|protected|public)\s+\w[\w<>,. ]*\s+\w+\s*;")
_SYSTEM_OUT = re.compile(r"\bSystem\.(out|err)\.")
_DDL = re.compile(r"\b(CREATE\s+TABLE|ALTER\s+TABLE|DROP\s+TABLE)\b", re.IGNORECASE)


def _iter_files(target: Path, suffixes: tuple[str, ...]) -> list[Path]:
    return [p for p in target.rglob("*") if p.is_file() and p.name.endswith(suffixes)]


def _scan(files: list[Path], pattern: re.Pattern, want: bool) -> list[str]:
    """want=True: every file MUST match (violation if absent).
       want=False: NO file may match (violation per match)."""
    violations: list[str] = []
    for f in files:
        try:
            text = f.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        found = pattern.search(text)
        if want and not found:
            violations.append(f"{f.name}: missing required pattern")
        elif not want and found:
            for i, line in enumerate(text.splitlines(), 1):
                if pattern.search(line):
                    violations.append(f"{f.name}:{i}: {line.strip()[:80]}")
    return violations


def check_fe(target: Path) -> list[dict]:
    components = _iter_files(target, (".component.ts",))
    all_ts = _iter_files(target, (".ts",))
    return [
        {"rule": "OnPush", "violations": _scan(components, _ON_PUSH, want=True)},
        {"rule": "standalone", "violations": _scan(components, _STANDALONE, want=True)},
        {"rule": "no-HttpClient-in-component", "violations": _scan(components, _HTTPCLIENT, want=False)},
        {"rule": "no-web-storage", "violations": _scan(all_ts, _WEB_STORAGE, want=False)},
        {"rule": "no-console-log", "violations": _scan(all_ts, _CONSOLE_LOG, want=False)},
        {"rule": "no-cross-project-import", "violations": _scan(all_ts, _CROSS_PROJECT, want=False)},
    ]


def check_be(target: Path) -> list[dict]:
    java = _iter_files(target, (".java",))
    return [
        {"rule": "no-field-autowired", "violations": _scan(java, _FIELD_AUTOWIRED, want=False)},
        {"rule": "no-system-out", "violations": _scan(java, _SYSTEM_OUT, want=False)},
        {"rule": "no-ddl-outside-migrations", "violations": _scan(java, _DDL, want=False)},
    ]


def run_check(layer: str, target: Path) -> dict:
    checks = check_fe(target) if layer == "fe" else check_be(target)
    for c in checks:
        c["ok"] = not c["violations"]
    return {
        "layer": layer,
        "target": str(target),
        "passed": all(c["ok"] for c in checks),
        "checks": checks,
    }


def cmd_check(args) -> int:
    target = Path(args.target)
    if not target.is_dir():
        print(f"error: target dir not found: {target}", file=sys.stderr)
        return 1
    result = run_check(args.layer, target)

    if args.output:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        tmp = Path(args.output).with_suffix(".json.tmp")
        tmp.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        os.replace(tmp, Path(args.output))
    if args.report or not args.output:
        for c in result["checks"]:
            mark = "ok " if c["ok"] else "FAIL"
            print(f"  [{mark}] {c['rule']} ({len(c['violations'])} violation(s))")
            for v in c["violations"][:10]:
                print(f"       {v}")

    if not result["passed"]:
        bad = [c["rule"] for c in result["checks"] if not c["ok"]]
        print(f"error: conformance FAILED -- {', '.join(bad)}", file=sys.stderr)
        return 2
    print(f"OK: arch-conformance clean ({args.layer}, {len(result['checks'])} rules)")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="arch-conformance.py", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    pc = sub.add_parser("check", help="run static architecture-rule checks")
    pc.add_argument("--layer", required=True, choices=["fe", "be"])
    pc.add_argument("--target", required=True, metavar="DIR")
    pc.add_argument("--output", metavar="PATH", help="write conformance.json")
    pc.add_argument("--report", action="store_true")
    pc.set_defaults(func=cmd_check)
    return p


def main(argv: list[str] | None = None) -> int:
    force_utf8_stdio()
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
