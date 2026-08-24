#!/usr/bin/env python3
"""
Guarda de drift dos agentes de borda — E2E-032.

Três arquivos calculam gesto a partir da webcam:

  backend/edge_agent_core.py                   ← a fonte
  backend/edge-agent-ws.py                     ← ponte de calibração; IMPORTA da fonte
  docs/paper/artifacts/poc-edge-agent-webcam.py ← aparato congelado; cópia autocontida

O segundo não pode divergir, porque importa. O terceiro pode — e é justamente ele que este
script vigia. Ele é mantido autocontido de propósito (um artefato de reprodução que dependesse
de um módulo do backend deixaria de ser reproduzível isoladamente), e o preço dessa escolha é
que a cópia precisa de um guarda.

Por que isso importa: o laço de calibração responde "este gesto funciona para este paciente?".
Se ele medir com limiares diferentes dos que a detecção sempre-ligada usa, a resposta não vale
para o caminho que dispara o alarme — e a divergência seria silenciosa até um alarme não sair.

COMO COMPARA: pelo AST normalizado, não pelo texto. Comentário, docstring, espaçamento e o
prefixo de `_dist` são irrelevantes; qualquer mudança de operação, de operando ou de valor de
limiar reprova. Nada é importado — mediapipe/opencv/numpy não precisam estar instalados.

Uso:
  python backend/scripts/check_edge_agent_drift.py     # 0 = alinhado, 1 = drift
"""

import ast
import io
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

CORE = REPO / "backend" / "edge_agent_core.py"
BRIDGE = REPO / "backend" / "edge-agent-ws.py"
ARTIFACT = REPO / "docs" / "paper" / "artifacts" / "poc-edge-agent-webcam.py"

# Limiares e índices que os dois lados têm de concordar.
SHARED_CONSTANTS = [
    "EAR_CLOSE", "EAR_OPEN", "EAR_OTHER_MAX", "BLINK_MIN_FRAMES", "DOUBLE_BLINK_WINDOW",
    "BRI_RAISE_ON", "BRI_RAISE_OFF", "BRI_OTHER_MAX", "BROW_MIN_FRAMES",
    "LEFT_EYE", "RIGHT_EYE", "LEFT_BROW_PEAK", "RIGHT_BROW_PEAK",
    "LEFT_EYE_TOP", "RIGHT_EYE_TOP", "LEFT_EYE_OUTER", "RIGHT_EYE_OUTER",
]

# COOLDOWN_SECONDS fica DE FORA por decisão: 1.5 s na calibração (teste de tiro único) contra
# 3.0 s no agente sempre-ligado. É divergência intencional, não drift.

SHARED_FUNCTIONS = ["dist", "compute_ear", "compute_bri"]

# O artefato chama a distância de `_dist`; a fonte, de `dist`. Normalizado antes de comparar.
ALIASES = {"_dist": "dist"}


class _Normalize(ast.NodeTransformer):
    """Aplica os aliases de nome para que a comparação enxergue a mesma função."""

    def visit_Name(self, node):
        node.id = ALIASES.get(node.id, node.id)
        return node

    def visit_FunctionDef(self, node):
        node.name = ALIASES.get(node.name, node.name)
        self.generic_visit(node)
        return node


def _parse(path: Path) -> ast.Module:
    return ast.parse(io.open(path, encoding="utf-8").read(), filename=str(path))


def _constants(tree: ast.Module) -> dict:
    """Atribuições literais de nível de módulo, por nome."""
    found = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            try:
                found[node.targets[0].id] = ast.literal_eval(node.value)
            except ValueError:
                pass  # não é literal — não é um limiar
    return found


def _functions(tree: ast.Module) -> dict:
    """Funções de nível de módulo, normalizadas e sem docstring, prontas para comparar."""
    tree = _Normalize().visit(tree)
    found = {}
    for node in tree.body:
        if isinstance(node, ast.FunctionDef):
            body = node.body
            if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) \
                    and isinstance(body[0].value.value, str):
                body = body[1:]  # docstring não é comportamento
            found[node.name] = "\n".join(ast.dump(stmt) for stmt in body)
    return found


def main() -> int:
    for path in (CORE, BRIDGE, ARTIFACT):
        if not path.exists():
            print(f"FALHA: arquivo esperado nao existe: {path}")
            return 1

    core, artifact = _parse(CORE), _parse(ARTIFACT)
    core_consts, artifact_consts = _constants(core), _constants(artifact)
    core_funcs, artifact_funcs = _functions(core), _functions(artifact)

    problems = []

    for name in SHARED_CONSTANTS:
        if name not in core_consts:
            problems.append(f"{name}: ausente em edge_agent_core.py (a fonte)")
        elif name not in artifact_consts:
            problems.append(f"{name}: ausente em poc-edge-agent-webcam.py")
        elif core_consts[name] != artifact_consts[name]:
            problems.append(
                f"{name}: core={core_consts[name]!r} != artefato={artifact_consts[name]!r}")

    for name in SHARED_FUNCTIONS:
        if name not in core_funcs:
            problems.append(f"{name}(): ausente em edge_agent_core.py (a fonte)")
        elif name not in artifact_funcs:
            problems.append(f"{name}(): ausente em poc-edge-agent-webcam.py")
        elif core_funcs[name] != artifact_funcs[name]:
            problems.append(f"{name}(): a matematica difere entre a fonte e o artefato")

    # A ponte importa — se ela voltar a definir um nome compartilhado, a importacao virou enfeite.
    bridge = _parse(BRIDGE)
    redefined = set(_constants(bridge)) & set(SHARED_CONSTANTS)
    redefined |= set(_functions(bridge)) & set(SHARED_FUNCTIONS)
    for name in sorted(redefined):
        problems.append(f"{name}: edge-agent-ws.py voltou a definir localmente em vez de importar")

    if problems:
        print("DRIFT entre os agentes de borda (E2E-032):\n")
        for problem in problems:
            print(f"  - {problem}")
        print(f"\nFonte:    {CORE.relative_to(REPO)}")
        print(f"Artefato: {ARTIFACT.relative_to(REPO)}")
        print("\nO artefato e mantido autocontido de proposito (reproducao do experimento).")
        print("Alinhe os dois lados a mao, ou registre a divergencia como intencional neste script.")
        return 1

    print(f"OK — {len(SHARED_CONSTANTS)} limiares/indices e {len(SHARED_FUNCTIONS)} funcoes alinhados;")
    print("     edge-agent-ws.py importa da fonte sem redefinir nada.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
