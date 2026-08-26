#!/usr/bin/env python3
"""
Testes do núcleo dos agentes de borda — E2E-033.

Asserts puros, sem pytest: este repositório não tem infraestrutura Python de teste, e introduzir
uma dependência para nove asserts custaria mais do que resolve. Roda com `python` e devolve
0 ou 1, que é o que o CI precisa.

Nada de mediapipe/opencv aqui — só a leitura do perfil publicado e a escolha de faixa.

Uso:
  python backend/scripts/test_edge_agent_core.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import edge_agent_core as core  # noqa: E402

FAILURES = []


def check(name, condition, detail=""):
    if condition:
        print(f"  ok    {name}")
    else:
        print(f"  FALHA {name} — {detail}")
        FAILURES.append(name)


def main() -> int:
    print("edge_agent_core — leitura do perfil publicado\n")

    # ── a propriedade que torna a adocao do perfil segura ────────────────────
    media = core.DEFAULT_BANDS["media"]
    check(
        "faixa media reproduz as constantes historicas",
        (media.ear_close, media.ear_open, media.ear_other_max, media.blink_min_frames,
         media.double_blink_window, media.bri_raise_on, media.bri_raise_off,
         media.bri_other_max, media.brow_min_frames)
        == (core.EAR_CLOSE, core.EAR_OPEN, core.EAR_OTHER_MAX, core.BLINK_MIN_FRAMES,
            core.DOUBLE_BLINK_WINDOW, core.BRI_RAISE_ON, core.BRI_RAISE_OFF,
            core.BRI_OTHER_MAX, core.BROW_MIN_FRAMES),
        "adotar o perfil mudaria comportamento em silencio",
    )

    # ── sentido da sensibilidade ────────────────────────────────────────────
    baixa, alta = core.DEFAULT_BANDS["baixa"], core.DEFAULT_BANDS["alta"]
    check(
        "alta detecta mais facil que baixa no EAR",
        baixa.ear_close < media.ear_close < alta.ear_close,
        "EAR cai ao fechar o olho: mais sensivel e limiar MAIOR",
    )
    check(
        "alta detecta mais facil que baixa no BRI",
        baixa.bri_raise_on > media.bri_raise_on > alta.bri_raise_on,
        "BRI sobe ao levantar a sobrancelha: mais sensivel e limiar MENOR",
    )
    check(
        "alta exige menos sustentacao",
        baixa.brow_min_frames > media.brow_min_frames > alta.brow_min_frames,
    )

    # ── leitura de um perfil real ───────────────────────────────────────────
    profile = {
        "version": 7,
        "thresholds": {
            "baixa": {"earClose": 0.19, "doubleBlinkWindowMs": 1500},
            "media": {"earClose": 0.22, "doubleBlinkWindowMs": 2000},
            "alta": {"earClose": 0.25, "doubleBlinkWindowMs": 2500},
        },
        "primitives": [
            {"tipoGestoId": "tg-piscar-duplo-001", "sensibilidade": "alta"},
            {"tipoGestoId": "tg-sobrancelha-dupla", "sensibilidade": "baixa"},
            {"tipoGestoId": "tg-piscar-esquerdo"},
        ],
    }

    th, band = core.thresholds_for(profile, "tg-piscar-duplo-001")
    check("gesto em alta usa o limiar de alta", (band, th.ear_close) == ("alta", 0.25))

    th, band = core.thresholds_for(profile, "tg-sobrancelha-dupla")
    check("gesto em baixa usa o limiar de baixa", (band, th.ear_close) == ("baixa", 0.19))

    th, band = core.thresholds_for(profile, "tg-piscar-esquerdo")
    check("gesto sem faixa declarada cai na padrao", (band, th.ear_close) == ("media", 0.22))

    th, _ = core.thresholds_for(profile, "tg-piscar-duplo-001")
    check(
        "janela chega em ms e e usada em segundos",
        th.double_blink_window == 2.5,
        f"recebido {th.double_blink_window}",
    )

    # Campo ausente na faixa nao zera o limiar — herda o valor da faixa padrao.
    th, _ = core.thresholds_for(profile, "tg-piscar-duplo-001")
    check(
        "campo ausente na faixa herda o padrao em vez de virar zero",
        th.brow_min_frames == core.BROW_MIN_FRAMES,
        f"recebido {th.brow_min_frames}",
    )

    # ── resposta POR DISPOSITIVO, com a faixa do paciente (E2E-034) ─────────
    # Mesma forma da resposta da empresa, mais pacienteId/catalogoVersao/origem. O core nao
    # precisou mudar nada para le-la — essa era a aposta do formato da E2E-033.
    por_dispositivo = {
        "version": 1,
        "pacienteId": "0cd405c6-49c5-45ff-864e-6cc2157f0c71",
        "catalogoVersao": 2,
        "catalogoDegradado": False,
        "etag": '"1-2"',
        "thresholds": profile["thresholds"],
        "primitives": [
            {"tipoGestoId": "tg-piscar-duplo-001", "sensibilidade": "alta", "origem": "PACIENTE"},
            {"tipoGestoId": "tg-sobrancelha-dupla", "sensibilidade": "media",
             "origem": "PERFIL_EMPRESA"},
        ],
    }

    th, band = core.thresholds_for(por_dispositivo, "tg-piscar-duplo-001")
    check("faixa calibrada para o paciente vence a da empresa", (band, th.ear_close) == ("alta", 0.25))
    check(
        "origem distingue calibrado de herdado",
        (core.origin_for(por_dispositivo, "tg-piscar-duplo-001"),
         core.origin_for(por_dispositivo, "tg-sobrancelha-dupla")) == ("PACIENTE", "PERFIL_EMPRESA"),
    )

    degradado = dict(por_dispositivo, catalogoDegradado=True, catalogoVersao=None,
                     primitives=[{"tipoGestoId": "tg-piscar-duplo-001", "sensibilidade": "media",
                                  "origem": "INDISPONIVEL"}])
    th, band = core.thresholds_for(degradado, "tg-piscar-duplo-001")
    check(
        "catalogo indisponivel detecta na faixa padrao, e diz que e indisponivel",
        (band, th.ear_close, core.origin_for(degradado, "tg-piscar-duplo-001"))
        == ("media", 0.22, "INDISPONIVEL"),
        "'media escolhida' e 'media por falta de resposta' nao podem parecer iguais",
    )
    check("origem de gesto ausente do perfil e None",
          core.origin_for(por_dispositivo, "tg-nao-existe") is None)

    # ── degradacao: o agente roda na casa do paciente ───────────────────────
    for label, payload in [
        ("perfil ausente", None),
        ("perfil sem thresholds", {"version": 1, "primitives": []}),
        ("thresholds nao e objeto", {"thresholds": "nao-e-dict"}),
        ("faixa com lixo dentro", {"thresholds": {"media": {"earClose": "muito"}}}),
        ("primitivo com faixa inventada",
         {"primitives": [{"tipoGestoId": "g", "sensibilidade": "turbo"}]}),
    ]:
        try:
            th, band = core.thresholds_for(payload, "g")
            ok = band == "media" and th.ear_close == core.EAR_CLOSE
        except Exception as exc:  # noqa: BLE001 — qualquer excecao aqui ja e a falha
            th, band, ok = None, None, False
            print(f"        excecao: {exc!r}")
        check(f"degrada para a padrao: {label}", ok, "parou de detectar em vez de degradar")

    print()
    if FAILURES:
        print(f"{len(FAILURES)} falha(s): {', '.join(FAILURES)}")
        return 1
    print("todos os casos passaram.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
