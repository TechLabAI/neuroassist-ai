#!/usr/bin/env python3
"""
Guarda de drift dos agentes de borda — E2E-032.

QUATRO lugares descrevem os mesmos limiares. Três arquivos calculam gesto a partir da webcam:

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

E, desde a E2E-033, os mesmos números existem em mais dois lugares — o GEST passou a publicar as
faixas no perfil:

  backend/.../domain/model/DetectionThresholdTable.java   ← defaults() (Java)
  .../db/migration/empresa/V0401__gest_detection_thresholds.sql  ← backfill dos perfis antigos

Curar a duplicação em Python e criar duas cópias novas em Java e SQL teria sido trocar de doença.
As quatro são comparadas aqui.

COMO COMPARA: pelo AST normalizado, não pelo texto. Comentário, docstring, espaçamento e o
prefixo de `_dist` são irrelevantes; qualquer mudança de operação, de operando ou de valor de
limiar reprova. Nada é importado — mediapipe/opencv/numpy não precisam estar instalados.

Uso:
  python backend/scripts/check_edge_agent_drift.py     # 0 = alinhado, 1 = drift
"""

import ast
import io
import re
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

# Ordem posicional dos campos, idêntica nos três dialetos. A janela do piscar duplo é o único
# campo com unidade divergente — segundos em Python, milissegundos em Java e SQL — e é
# normalizada para ms antes de comparar.
BAND_FIELDS = [
    "earClose", "earOpen", "earOtherMax", "blinkMinFrames", "doubleBlinkWindowMs",
    "briRaiseOn", "briRaiseOff", "briOtherMax", "browMinFrames",
]
BANDS = ["baixa", "media", "alta"]

JAVA_DEFAULTS = (
    REPO / "backend" / "services" / "gest-service" / "src" / "main" / "java"
    / "com" / "neuroassist" / "gest" / "domain" / "model" / "DetectionThresholdTable.java"
)
SQL_BACKFILL = (
    REPO / "backend" / "services" / "neuroassist-ai-migrations" / "src" / "main" / "resources"
    / "db" / "migration" / "empresa" / "V0401__gest_detection_thresholds.sql"
)

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


def _python_bands(tree: ast.Module) -> dict:
    """
    DEFAULT_BANDS do módulo Python: {faixa: {campo: valor}}.

    Não dá para usar literal_eval — os valores são chamadas a Thresholds(). Os argumentos são
    posicionais e literais, então a ordem de BAND_FIELDS é o contrato. Janela convertida para ms.
    """
    for node in tree.body:
        if not (isinstance(node, ast.Assign)
                and isinstance(node.targets[0], ast.Name)
                and node.targets[0].id == "DEFAULT_BANDS"):
            continue
        bands = {}
        for key, value in zip(node.value.keys, node.value.values):
            args = [ast.literal_eval(a) for a in value.args]
            band = dict(zip(BAND_FIELDS, args))
            band["doubleBlinkWindowMs"] = round(band["doubleBlinkWindowMs"] * 1000)
            bands[ast.literal_eval(key)] = band
        return bands
    return {}


def _java_bands(text: str) -> dict:
    """As três chamadas a `new DetectionThresholds(...)` dentro de defaults(), na ordem declarada."""
    body = text[text.index("static DetectionThresholdTable defaults()"):]
    body = body[: body.index("}")]
    calls = re.findall(r"new DetectionThresholds\(([^)]*)\)", body)
    bands = {}
    for band_name, call in zip(BANDS, calls):
        values = [float(v.strip()) for v in call.split(",")]
        bands[band_name] = dict(zip(BAND_FIELDS, values))
    return bands


def _sql_bands(text: str) -> dict:
    """Os pares `'campo', valor` de cada faixa no backfill da V0401."""
    bands = {}
    for band_name in BANDS:
        match = re.search(
            r"'" + band_name + r"',\s*jsonb_build_object\((.*?)\)", text, re.DOTALL)
        if not match:
            continue
        pairs = re.findall(r"'(\w+)',\s*([0-9.]+)", match.group(1))
        bands[band_name] = {field: float(value) for field, value in pairs}
    return bands


def _compare_bands(problems, label_a, bands_a, label_b, bands_b):
    """Compara faixa a faixa, campo a campo. Números são comparados como float dos dois lados."""
    for band in BANDS:
        a, b = bands_a.get(band), bands_b.get(band)
        if a is None:
            problems.append(f"faixa '{band}': ausente em {label_a}")
            continue
        if b is None:
            problems.append(f"faixa '{band}': ausente em {label_b}")
            continue
        for field in BAND_FIELDS:
            va, vb = a.get(field), b.get(field)
            if va is None:
                problems.append(f"{band}.{field}: ausente em {label_a}")
            elif vb is None:
                problems.append(f"{band}.{field}: ausente em {label_b}")
            elif abs(float(va) - float(vb)) > 1e-9:
                problems.append(
                    f"{band}.{field}: {label_a}={va} != {label_b}={vb}")


def main() -> int:
    for path in (CORE, BRIDGE, ARTIFACT, JAVA_DEFAULTS, SQL_BACKFILL):
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

    # ── as tres faixas, nos tres dialetos (E2E-033) ─────────────────────────
    python_bands = _python_bands(core)
    java_bands = _java_bands(io.open(JAVA_DEFAULTS, encoding="utf-8").read())
    sql_bands = _sql_bands(io.open(SQL_BACKFILL, encoding="utf-8").read())

    _compare_bands(problems, "python", python_bands, "java", java_bands)
    _compare_bands(problems, "python", python_bands, "sql", sql_bands)

    # A faixa `media` E o bloco de constantes historico. Se as duas divergirem, adotar o perfil
    # passa a mudar comportamento em silencio — que e exatamente o que a faixa media existe para
    # impedir.
    media = python_bands.get("media", {})
    historic = {
        "earClose": core_consts.get("EAR_CLOSE"),
        "earOpen": core_consts.get("EAR_OPEN"),
        "earOtherMax": core_consts.get("EAR_OTHER_MAX"),
        "blinkMinFrames": core_consts.get("BLINK_MIN_FRAMES"),
        "doubleBlinkWindowMs": (core_consts.get("DOUBLE_BLINK_WINDOW") or 0) * 1000,
        "briRaiseOn": core_consts.get("BRI_RAISE_ON"),
        "briRaiseOff": core_consts.get("BRI_RAISE_OFF"),
        "briOtherMax": core_consts.get("BRI_OTHER_MAX"),
        "browMinFrames": core_consts.get("BROW_MIN_FRAMES"),
    }
    for field, expected in historic.items():
        actual = media.get(field)
        if expected is None or actual is None or abs(float(expected) - float(actual)) > 1e-9:
            problems.append(
                f"media.{field}: a faixa padrao ({actual}) nao reproduz a constante historica "
                f"({expected}) — adotar o perfil mudaria comportamento em silencio")

    if problems:
        print("DRIFT nos limiares de deteccao (E2E-032 / E2E-033):\n")
        for problem in problems:
            print(f"  - {problem}")
        print(f"\nFonte:    {CORE.relative_to(REPO)}")
        print(f"Artefato: {ARTIFACT.relative_to(REPO)}")
        print("\nO artefato e mantido autocontido de proposito (reproducao do experimento).")
        print("Alinhe os dois lados a mao, ou registre a divergencia como intencional neste script.")
        return 1

    print(f"OK — {len(SHARED_CONSTANTS)} limiares/indices e {len(SHARED_FUNCTIONS)} funcoes")
    print("     alinhados entre a fonte e o artefato congelado;")
    print(f"     {len(BANDS)} faixas x {len(BAND_FIELDS)} campos identicas em python/java/sql;")
    print("     a faixa 'media' reproduz as constantes historicas;")
    print("     edge-agent-ws.py importa da fonte sem redefinir nada.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
