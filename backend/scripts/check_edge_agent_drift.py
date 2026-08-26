#!/usr/bin/env python3
"""
Guarda de drift dos limiares de detecção de gesto — GEST-INT-008 (atualizado P3).

QUATRO lugares descrevem os limiares canônicos:

  backend/.../domain/model/DetectionThresholdTable.java  ← fonte Java (defaults())
  backend/.../db/migration/empresa/V0401__gest_detection_thresholds.sql ← SQL backfill
  frontend/libs/shared/contracts/src/lib/detection-thresholds.ts       ← LIMIARES_PADRAO browser
  docs/paper/artifacts/poc-edge-agent-webcam.py          ← artefato congelado (Python)

Decisão ADR-007 + GEST-INT-008 (2026-08-25):

  O agente Python (edge_agent_core.py / edge-agent-ws.py) foi aposentado pela ADR-007.
  O browser passou a ser o ÚNICO motor de detecção. A comparação Python-vs-Python (fonte
  contra artefato de reprodução) foi APOSENTADA porque:

    1. edge_agent_core.py não é mais o executor de produção — é código morto.
    2. Manter a comparação ativa criaria a aparência de cobertura sem cobertura real:
       o CI ficaria verde exatamente enquanto o motor do browser divergia.

  O que este script vigia agora:
    A  browser (LIMIARES_PADRAO) === Java (defaults() faixa 'media')
    B  Java (defaults() todas as faixas) === SQL (V0401 todas as faixas)
    C  (opcional) artefato Python congelado — se o arquivo ainda existir, avisa
       que ele pode ter divergido mas não bloqueia o CI.

  Dois guardas Python que sobrevivem:
    - Funções compute_ear / compute_bri do artefato (aparato de reprodução do paper)
      ainda podem ser verificadas contra si mesmas por test_edge_agent_core.py.
    - Este script não toca mais os arquivos Python de produção.

Uso:
  python backend/scripts/check_edge_agent_drift.py     # 0 = alinhado, 1 = drift

Para provar que o guarda reprovaria com valores errados, executar:
  python backend/scripts/check_edge_agent_drift.py --self-test
  (este modo corrompe um valor no TS in-memory e verifica que o script acusa drift)
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

JAVA_DEFAULTS = (
    REPO / "backend" / "services" / "gest-service" / "src" / "main" / "java"
    / "com" / "neuroassist" / "gest" / "domain" / "model" / "DetectionThresholdTable.java"
)
SQL_BACKFILL = (
    REPO / "backend" / "services" / "neuroassist-ai-migrations" / "src" / "main" / "resources"
    / "db" / "migration" / "empresa" / "V0401__gest_detection_thresholds.sql"
)
TS_THRESHOLDS = (
    REPO / "frontend" / "libs" / "shared" / "contracts" / "src" / "lib"
    / "detection-thresholds.ts"
)
ARTIFACT_PY = REPO / "docs" / "paper" / "artifacts" / "poc-edge-agent-webcam.py"

BAND_FIELDS = [
    "earClose", "earOpen", "earOtherMax", "blinkMinFrames", "doubleBlinkWindowMs",
    "briRaiseOn", "briRaiseOff", "briOtherMax", "browMinFrames",
]
BANDS = ["baixa", "media", "alta"]


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


def _ts_limiares_padrao(text: str) -> dict:
    """
    Extrai os valores de LIMIARES_PADRAO do arquivo detection-thresholds.ts.

    Procura o bloco `export const LIMIARES_PADRAO: DetectionThresholds = { ... }` e
    extrai os pares campo:valor. Tolerante a espaços, comentários inline e `as const`.
    """
    match = re.search(
        r"export\s+const\s+LIMIARES_PADRAO\s*:\s*DetectionThresholds\s*=\s*\{([^}]*)\}",
        text, re.DOTALL
    )
    if not match:
        return {}
    body = match.group(1)
    # Remove comentários de linha e trailing commas
    body = re.sub(r"//[^\n]*", "", body)
    pairs = re.findall(r"(\w+)\s*:\s*([0-9.]+)", body)
    return {field: float(value) for field, value in pairs}


# Campos que o motor do BROWSER consome contra BLENDSHAPES do MediaPipe
# (browInnerUp, browOuterUpLeft/Right, eyeBlinkLeft/Right) — ativações 0..1 de uma
# rede neural.
#
# No backend os mesmos nomes são calibrados para BRI, que é OUTRA grandeza:
#
#     BRI = (eye_top_y - brow_peak_y) / inter_eye_dist       (edge_agent_core.py)
#
# uma razão geométrica normalizada pela distância interocular. Comparar os dois
# seria comparar escalas diferentes — e foi exatamente o que aconteceu: copiar o
# número do backend por igualdade de NOME mudou em silêncio a sensibilidade das
# sobrancelhas (0.40 -> 0.30 para disparar). Num canal de pedido de ajuda isso é
# mudança clínica, não ajuste de constante.
#
# Espelha CAMPOS_ESCALA_BLENDSHAPE em detection-thresholds.ts.
BLENDSHAPE_SCALE_FIELDS = ["earOtherMax", "briRaiseOn", "briRaiseOff", "briOtherMax"]


def _compare_media_vs_browser(problems, java_bands, ts_values):
    """
    Compara a faixa 'media' do Java contra LIMIARES_PADRAO do TypeScript — nos
    campos que são a MESMA grandeza nos dois motores.

    EAR é calculado igual dos dois lados (altura/largura do olho), e contagens de
    frame e janelas de tempo são adimensionais: esses comparam.

    Os campos de escala de blendshape NÃO comparam, e o guarda AFIRMA isso em vez
    de pular em silêncio — um guarda que ignora campos vira o "guarda que não
    guarda nada". Se algum deles passar a coincidir com o backend, é sinal de que
    alguém "alinhou" copiando número por nome, e o guarda reprova.
    """
    media = java_bands.get("media", {})

    for field in BAND_FIELDS:
        java_val = media.get(field)
        ts_val = ts_values.get(field)

        if java_val is None:
            problems.append(f"media.{field}: ausente em DetectionThresholdTable.java")
            continue
        if ts_val is None:
            problems.append(f"LIMIARES_PADRAO.{field}: ausente em detection-thresholds.ts")
            continue

        iguais = abs(float(java_val) - float(ts_val)) <= 1e-9

        if field in BLENDSHAPE_SCALE_FIELDS:
            # earOtherMax coincide hoje por acaso (0.30 dos dois lados) e não é
            # defeito — por isso só briRaise*/briOtherMax são afirmados como
            # divergentes. Ver o bloco acima.
            if field != "earOtherMax" and iguais:
                problems.append(
                    f"LIMIARES_PADRAO.{field}={ts_val} coincide com java_media — "
                    "este campo e consumido contra BLENDSHAPE no browser e contra BRI "
                    "no backend; igualdade aqui indica alinhamento por nome, nao por "
                    "grandeza. Ver CAMPOS_ESCALA_BLENDSHAPE."
                )
            continue

        if not iguais:
            problems.append(
                f"LIMIARES_PADRAO.{field}: browser={ts_val} != java_media={java_val}"
            )


def _compare_bands(problems, label_a, bands_a, label_b, bands_b):
    """Compara faixa a faixa, campo a campo."""
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
                    f"{band}.{field}: {label_a}={va} != {label_b}={vb}"
                )


def _self_test(java_text, sql_text, ts_text):
    """
    Prova por mutação: corrompe LIMIARES_PADRAO.earClose in-memory e verifica que
    o guarda acusa drift.

    Retorna 0 se o guarda realmente detecta a corrupção, 1 se falhou em detectá-la.
    """
    print("[self-test] Injetando mutação: LIMIARES_PADRAO.earClose := 0.99 (valor inválido)")
    mutated_ts = re.sub(
        r"(earClose\s*:\s*)([0-9.]+)",
        r"\g<1>0.99",
        ts_text,
        count=1,
    )
    ts_values = _ts_limiares_padrao(mutated_ts)
    java_bands = _java_bands(java_text)

    problems = []
    _compare_media_vs_browser(problems, java_bands, ts_values)

    if problems:
        print("[self-test] OK — guarda detectou a mutação corretamente:")
        for p in problems:
            print(f"  - {p}")
        return 0
    else:
        print("[self-test] FALHA — o guarda NÃO detectou a mutação. O guarda está quebrado!")
        return 1


def main(argv=None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    self_test_mode = "--self-test" in argv

    for path in (JAVA_DEFAULTS, SQL_BACKFILL, TS_THRESHOLDS):
        if not path.exists():
            print(f"FALHA: arquivo esperado nao existe: {path}")
            return 1

    java_text = JAVA_DEFAULTS.read_text(encoding="utf-8")
    sql_text = SQL_BACKFILL.read_text(encoding="utf-8")
    ts_text = TS_THRESHOLDS.read_text(encoding="utf-8")

    if self_test_mode:
        return _self_test(java_text, sql_text, ts_text)

    java_bands = _java_bands(java_text)
    sql_bands = _sql_bands(sql_text)
    ts_values = _ts_limiares_padrao(ts_text)

    if not ts_values:
        print("FALHA: nao foi possivel extrair LIMIARES_PADRAO de detection-thresholds.ts")
        print(f"  Arquivo: {TS_THRESHOLDS.relative_to(REPO)}")
        return 1

    problems = []

    # A — browser vs Java (faixa 'media')
    _compare_media_vs_browser(problems, java_bands, ts_values)

    # B — Java vs SQL (todas as faixas)
    _compare_bands(problems, "java", java_bands, "sql", sql_bands)

    # C — artefato Python congelado (aviso não-bloqueante, ADR-007 aposentou o agente)
    if ARTIFACT_PY.exists():
        print(
            f"[aviso] {ARTIFACT_PY.relative_to(REPO)} ainda existe. "
            "Este artefato foi isolado da comparação por decisão GEST-INT-008 (ADR-007 aposentou "
            "edge_agent_core.py como motor de produção). Para verificar coerência interna do "
            "artefato, executar: python backend/scripts/test_edge_agent_core.py"
        )
    else:
        print(f"[info] artefato Python ({ARTIFACT_PY.name}) nao encontrado — ignorado.")

    if problems:
        print("\nDRIFT nos limiares de deteccao (GEST-INT-008):\n")
        for problem in problems:
            print(f"  - {problem}")
        print(f"\nFonte Java:  {JAVA_DEFAULTS.relative_to(REPO)}")
        print(f"Fonte SQL:   {SQL_BACKFILL.relative_to(REPO)}")
        print(f"Fonte TS:    {TS_THRESHOLDS.relative_to(REPO)}")
        print(
            "\nLIMIARES_PADRAO deve ser identico a faixa 'media' de DetectionThresholdTable.defaults()."
        )
        print(
            "Edite detection-thresholds.ts para alinhar, ou ajuste os valores Java/SQL se a faixa media mudou."
        )
        return 1

    comparaveis = [f for f in BAND_FIELDS if f not in BLENDSHAPE_SCALE_FIELDS]
    fields_checked = len(BAND_FIELDS)
    print(
        f"OK — {len(comparaveis)} campos de LIMIARES_PADRAO alinhados com a faixa "
        f"'media' do Java ({', '.join(comparaveis)});"
    )
    print(
        f"     {len(BLENDSHAPE_SCALE_FIELDS)} campos em escala de BLENDSHAPE, "
        "divergentes POR DECISAO e afirmados como tal "
        f"({', '.join(BLENDSHAPE_SCALE_FIELDS)});"
    )
    print(
        f"     {len(BANDS)} faixas x {fields_checked} campos identicas em java/sql;"
    )
    print(
        "     decisao ADR-007/GEST-INT-008: Python (edge_agent_core.py) aposentado, "
        "comparacao Python-vs-Python removida."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
