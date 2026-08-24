#!/usr/bin/env python3
"""
Edge Agent Core — NeuroAssist AI

Fonte única dos limiares e da matemática de sensor usados na detecção de gesto por webcam
(E2E-032).

Antes existiam duas cópias: `edge-agent-ws.py` (ponte do laço de calibração) e
`poc-edge-agent-webcam.py` (detecção sempre-ligada). O cabeçalho da primeira admitia a duplicação
e explicava a causa — "not imported: hyphens" —, porque o hífen no nome do arquivo impede `import`
em Python. Daí o underscore neste nome: ele existe para ser importado.

Por que a duplicação importava: o laço de calibração responde "este gesto funciona para este
paciente?". Medir com limiares diferentes dos que a detecção sempre-ligada usa torna a resposta
inválida justamente para o caminho que dispara o alarme — e a divergência seria silenciosa.

LIMITAÇÃO CONHECIDA, registrada em E2E-032: estes limiares são constantes de código, iguais para
todo paciente. O perfil publicado pelo GEST (`DetectionProfilePrimitive`) carrega `tipoGestoId` e
`engineFeatureRef`, mas nenhuma sensibilidade — ele diz QUAIS gestos estão ativos, não COM QUE
sensibilidade detectá-los. O PAC tem `sensibilidade` por comando, e ela nunca chega até aqui. ALS
afeta cada pessoa de forma diferente; um limiar único para todos é uma simplificação que o sistema
ainda não superou.
"""

from dataclasses import dataclass

import numpy as np

# ─────────────────────────────────────────────────────────────────────────────
# Limiares — EAR (Eye Aspect Ratio)
# ─────────────────────────────────────────────────────────────────────────────

EAR_CLOSE = 0.22
"""Abaixo disto o olho é considerado fechado."""

EAR_OPEN = 0.27
"""Acima disto o olho é considerado claramente aberto (histerese contra tremor)."""

EAR_OTHER_MAX = 0.30
"""Num piscar individual, o OUTRO olho tem de ficar acima disto — senão é piscar duplo."""

BLINK_MIN_FRAMES = 2
"""Frames consecutivos com olho fechado para contar como piscada, não ruído de frame."""

DOUBLE_BLINK_WINDOW = 2.0
"""Janela, em segundos, para duas piscadas contarem como piscar duplo."""

# ─────────────────────────────────────────────────────────────────────────────
# Limiares — BRI (Brow Raise Index)
# ─────────────────────────────────────────────────────────────────────────────

BRI_RAISE_ON = 0.30
"""Acima disto a sobrancelha é considerada levantada."""

BRI_RAISE_OFF = 0.22
"""Abaixo disto ela é considerada relaxada. A folga entre ON e OFF é a histerese."""

BRI_OTHER_MAX = 0.25
"""Num levantamento individual, o lado oposto tem de ficar abaixo disto."""

BROW_MIN_FRAMES = 5
"""Frames de sustentação (~0,2 s) — sobrancelha sobe e desce naturalmente ao falar."""

# ─────────────────────────────────────────────────────────────────────────────
# Índices de landmark do MediaPipe Face Mesh
# ─────────────────────────────────────────────────────────────────────────────

LEFT_EYE = [33, 160, 158, 133, 153, 144]
RIGHT_EYE = [362, 385, 387, 263, 373, 380]

LEFT_BROW_PEAK = 105
RIGHT_BROW_PEAK = 334
LEFT_EYE_TOP = 159
RIGHT_EYE_TOP = 386
LEFT_EYE_OUTER = 33
RIGHT_EYE_OUTER = 263
"""Cantos externos: âncora horizontal. A distância entre eles normaliza o BRI, para que
aproximar ou afastar o rosto da câmera não mude o índice."""


# ─────────────────────────────────────────────────────────────────────────────
# Matemática de sensor
# ─────────────────────────────────────────────────────────────────────────────


def dist(a, b):
    """Distância euclidiana entre dois pontos 2D."""
    return float(np.linalg.norm(np.array(a) - np.array(b)))


def compute_ear(landmarks, eye_idx, w, h):
    """
    Eye Aspect Ratio — razão entre as aberturas vertical e horizontal do olho.

    Cai perto de zero quando o olho fecha, e é invariante à distância da câmera porque o
    denominador é a própria largura do olho. Sem rosto (hz≈ 0) devolve 0.3 — o valor de olho
    aberto, para que a ausência de sinal nunca seja lida como olho fechado.
    """
    pts = [(landmarks[i].x * w, landmarks[i].y * h) for i in eye_idx]
    p1, p2, p3, p4, p5, p6 = pts
    v1 = dist(p2, p6)
    v2 = dist(p3, p5)
    hz = dist(p1, p4)
    return (v1 + v2) / (2.0 * hz) if hz > 1e-6 else 0.3


def compute_bri(landmarks, side):
    """
    Brow Raise Index = (eye_top_y − brow_peak_y) / inter_eye_dist.

    Positivo quando a sobrancelha está acima do topo do olho — o estado normal. Valor maior
    significa sobrancelha mais levantada. Normalizado pela distância interocular pela mesma razão
    que o EAR: independência da distância até a câmera.
    """
    if side == "left":
        brow_y = landmarks[LEFT_BROW_PEAK].y
        eye_y = landmarks[LEFT_EYE_TOP].y
    else:
        brow_y = landmarks[RIGHT_BROW_PEAK].y
        eye_y = landmarks[RIGHT_EYE_TOP].y

    inter = abs(landmarks[RIGHT_EYE_OUTER].x - landmarks[LEFT_EYE_OUTER].x)
    return (eye_y - brow_y) / inter if inter > 1e-6 else 0.0


# ─────────────────────────────────────────────────────────────────────────────
# Faixas de sensibilidade — E2E-033
# ─────────────────────────────────────────────────────────────────────────────
# Até aqui os limiares acima eram TUDO: constantes de código, iguais para todo paciente. O GEST
# passou a publicá-los no perfil, em três faixas, e o agente escolhe a faixa por gesto.
#
# `media` é, valor por valor, o bloco de constantes deste arquivo — de propósito. Adotar o perfil
# não muda comportamento nenhum até alguém escolher outra faixa.
#
# Sentido da sensibilidade: EAR CAI quando o olho fecha, então detectar mais fácil é um limiar
# MAIOR; BRI SOBE quando a sobrancelha levanta, então detectar mais fácil é um limiar MENOR. O
# GEST valida essa monotonicidade no publish (DetectionThresholdTable).
#
# Estes números têm espelho em DetectionThresholdTable.defaults() (Java) e no backfill da
# V0401__gest_detection_thresholds.sql. check_edge_agent_drift.py compara os quatro lados.

@dataclass(frozen=True)
class Thresholds:
    """Conjunto completo de limiares de uma faixa. Imutável — ninguém ajusta no meio do vídeo."""

    ear_close: float
    ear_open: float
    ear_other_max: float
    blink_min_frames: int
    double_blink_window: float  # segundos (o perfil transporta ms)
    bri_raise_on: float
    bri_raise_off: float
    bri_other_max: float
    brow_min_frames: int


DEFAULT_BANDS = {
    "baixa": Thresholds(0.19, 0.24, 0.27, 3, 1.5, 0.34, 0.26, 0.29, 7),
    "media": Thresholds(0.22, 0.27, 0.30, 2, 2.0, 0.30, 0.22, 0.25, 5),
    "alta": Thresholds(0.25, 0.30, 0.33, 2, 2.5, 0.26, 0.18, 0.21, 3),
}

DEFAULT_BAND = "media"


def _band_from_json(raw):
    """Uma faixa do JSON do perfil. Campo ausente cai no valor da faixa padrão."""
    fallback = DEFAULT_BANDS[DEFAULT_BAND]
    window_ms = raw.get("doubleBlinkWindowMs")
    return Thresholds(
        ear_close=float(raw.get("earClose", fallback.ear_close)),
        ear_open=float(raw.get("earOpen", fallback.ear_open)),
        ear_other_max=float(raw.get("earOtherMax", fallback.ear_other_max)),
        blink_min_frames=int(raw.get("blinkMinFrames", fallback.blink_min_frames)),
        double_blink_window=(
            float(window_ms) / 1000.0 if window_ms is not None else fallback.double_blink_window
        ),
        bri_raise_on=float(raw.get("briRaiseOn", fallback.bri_raise_on)),
        bri_raise_off=float(raw.get("briRaiseOff", fallback.bri_raise_off)),
        bri_other_max=float(raw.get("briOtherMax", fallback.bri_other_max)),
        brow_min_frames=int(raw.get("browMinFrames", fallback.brow_min_frames)),
    )


def bands_from_profile(profile):
    """
    As três faixas do payload de `GET /gest-service/detection-profile/active`.

    Perfil ausente, sem `thresholds`, ou malformado devolve os padrões — o agente de borda roda na
    casa do paciente e não pode parar de detectar porque o GEST está fora do ar. Degradar para o
    comportamento histórico é a única falha aceitável aqui.
    """
    if not isinstance(profile, dict):
        return dict(DEFAULT_BANDS)
    raw = profile.get("thresholds")
    if not isinstance(raw, dict):
        return dict(DEFAULT_BANDS)

    bands = {}
    for name in DEFAULT_BANDS:
        entry = raw.get(name)
        try:
            bands[name] = _band_from_json(entry) if isinstance(entry, dict) else DEFAULT_BANDS[name]
        except (TypeError, ValueError):
            bands[name] = DEFAULT_BANDS[name]
    return bands


def sensitivity_by_gesture(profile):
    """
    `{tipoGestoId: faixa}` a partir dos primitivos do perfil.

    Gesto sem faixa declarada, ou com faixa desconhecida, recebe a padrão — um nome de faixa
    inventado não pode virar KeyError a 30 fps.
    """
    if not isinstance(profile, dict):
        return {}
    result = {}
    for primitive in profile.get("primitives") or []:
        if not isinstance(primitive, dict):
            continue
        gesture = primitive.get("tipoGestoId")
        if not gesture:
            continue
        band = primitive.get("sensibilidade")
        result[gesture] = band if band in DEFAULT_BANDS else DEFAULT_BAND
    return result


def thresholds_for(profile, tipo_gesto_id):
    """Os limiares que valem para UM gesto — a única função que os agentes precisam chamar."""
    bands = bands_from_profile(profile)
    band = sensitivity_by_gesture(profile).get(tipo_gesto_id, DEFAULT_BAND)
    return bands.get(band, bands[DEFAULT_BAND]), band


def origin_for(profile, tipo_gesto_id):
    """
    De onde veio a faixa deste gesto — `PACIENTE`, `PERFIL_EMPRESA` ou `INDISPONIVEL` (E2E-034).

    Só a resposta por dispositivo (`/detection-profile/devices/{id}`) traz isto; a resposta da
    empresa não. Serve para o agente registrar em log com que autoridade está detectando: "media
    porque a cuidadora escolheu media" e "media porque o PAC não respondeu" produzem exatamente a
    mesma detecção e significam coisas opostas.
    """
    if not isinstance(profile, dict):
        return None
    for primitive in profile.get("primitives") or []:
        if isinstance(primitive, dict) and primitive.get("tipoGestoId") == tipo_gesto_id:
            return primitive.get("origem")
    return None
