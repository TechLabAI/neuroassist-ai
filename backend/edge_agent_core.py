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
