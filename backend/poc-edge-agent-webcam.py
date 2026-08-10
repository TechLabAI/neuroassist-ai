#!/usr/bin/env python3
"""
PoC Edge Agent com Webcam — NeuroAssist AI
Detecta 6 tipos de gesto via MediaPipe Face Mesh e envia reports para evnt-service.

Gestos suportados:
  tg-piscar-duplo-001     — piscar ambos os olhos duas vezes em sequência (EAR médio)
  tg-piscar-esquerdo      — piscar só o olho esquerdo (direito permanece aberto)
  tg-piscar-direito       — piscar só o olho direito (esquerdo permanece aberto)
  tg-sobrancelha-dupla    — levantar as duas sobrancelhas (~0.2 s de hold)
  tg-sobrancelha-esquerda — levantar só a sobrancelha esquerda
  tg-sobrancelha-direita  — levantar só a sobrancelha direita

Pré-requisitos:
  pip install opencv-python mediapipe numpy requests

Uso:
  python poc-edge-agent-webcam.py
  python poc-edge-agent-webcam.py --camera 1
  python poc-edge-agent-webcam.py --no-pull    # pula pull do perfil, usa defaults
  python poc-edge-agent-webcam.py --debug      # mostra EAR/BRI por frame no terminal
"""

import argparse
import sys
import time
import uuid
from datetime import datetime, timezone

import cv2
import mediapipe as mp
import numpy as np
import requests

# ─────────────────────────────────────────────────────────────────────────────
# Configuração de endpoints e identidade
# ─────────────────────────────────────────────────────────────────────────────
GEST_BASE   = "http://localhost:8082"
EVNT_BASE   = "http://localhost:8083/api/v1"
PACIENTE_ID = "pac-marcio"
DEVICE_ID   = "webcam-local-poc"

# Mapeamento gesto → commandRef  (deve espelhar a configuração de Calibração da UI)
COMANDO_MAP = {
    "tg-piscar-duplo-001":    "cmd-ajuda",
    "tg-piscar-esquerdo":     "cmd-sapo",
    "tg-piscar-direito":      "cmd-desconhecido",
    "tg-sobrancelha-dupla":   "cmd-agua",
    "tg-sobrancelha-direita": "cmd-desconhecido",
    "tg-sobrancelha-esquerda":"cmd-desconhecido",
}

# ─────────────────────────────────────────────────────────────────────────────
# Thresholds de detecção
# ─────────────────────────────────────────────────────────────────────────────
# EAR (Eye Aspect Ratio) — 0 = fechado, ~0.30 = aberto normal
EAR_CLOSE        = 0.22   # abaixo disso = olho fechado
EAR_OPEN         = 0.27   # acima disso = claramente aberto
EAR_OTHER_MAX    = 0.30   # o "outro" olho deve ficar ACIMA disso para piscar individual
BLINK_MIN_FRAMES = 2      # frames consecutivos fechados para validar o blink

# Double blink
DOUBLE_BLINK_WINDOW = 2.0   # janela em segundos para o segundo blink

# BRI (Brow Raise Index) = (eye_top_y − brow_peak_y) / inter_eye_dist
# Valores típicos: neutro ≈ 0.18–0.24 | levantada ≈ 0.32–0.45
# Use --debug para ver os valores reais do seu rosto e ajustar se necessário.
BRI_RAISE_ON   = 0.30   # acima disso = sobrancelha levantada
BRI_RAISE_OFF  = 0.22   # abaixo disso = sobrancelha relaxada (histerese)
BRI_OTHER_MAX  = 0.25   # lado oposto deve ficar abaixo disso para levantamento individual
BROW_MIN_FRAMES = 5     # frames de hold necessários para disparar (~167 ms a 30 fps)

# Cooldown após qualquer disparo (todos os detectores bloqueiam)
COOLDOWN_SECONDS = 3.0

# Defaults para --no-pull
DEFAULT_PROFILE_VERSION = 1
DEFAULT_ACTIVE_GESTOS   = {"tg-piscar-duplo-001", "tg-sobrancelha-dupla", "tg-piscar-esquerdo"}

# ─────────────────────────────────────────────────────────────────────────────
# Índices de landmarks (MediaPipe Face Mesh 468 pontos)
# ─────────────────────────────────────────────────────────────────────────────
LEFT_EYE  = [33, 160, 158, 133, 153, 144]
RIGHT_EYE = [362, 385, 387, 263, 373, 380]

LEFT_BROW_PEAK  = 105   # pico da sobrancelha esquerda
RIGHT_BROW_PEAK = 334   # pico da sobrancelha direita
LEFT_EYE_TOP    = 159   # topo da pálpebra superior esquerda
RIGHT_EYE_TOP   = 386   # topo da pálpebra superior direita
LEFT_EYE_OUTER  = 33    # canto externo olho esquerdo (âncora horizontal)
RIGHT_EYE_OUTER = 263   # canto externo olho direito

# ─────────────────────────────────────────────────────────────────────────────
# Rótulos e cores
# ─────────────────────────────────────────────────────────────────────────────
GESTO_LABEL = {
    "tg-piscar-duplo-001":    "Piscar duplo",
    "tg-piscar-esquerdo":     "Piscar olho esq.",
    "tg-piscar-direito":      "Piscar olho dir.",
    "tg-sobrancelha-dupla":   "Sobrancelha dupla",
    "tg-sobrancelha-esquerda":"Sobrancelha esq.",
    "tg-sobrancelha-direita": "Sobrancelha dir.",
}

COLOR_GREEN  = (0, 255, 0)
COLOR_RED    = (0, 0, 255)
COLOR_YELLOW = (0, 255, 255)
COLOR_WHITE  = (255, 255, 255)
COLOR_CYAN   = (255, 255, 0)
COLOR_ORANGE = (0, 165, 255)
COLOR_DARK   = (30, 30, 30)


# ─────────────────────────────────────────────────────────────────────────────
# Pull do perfil ativo
# ─────────────────────────────────────────────────────────────────────────────

def pull_active_profile():
    """Retorna (profile_version: int, active_gestos: set[str])."""
    url = f"{GEST_BASE}/gest-service/detection-profile/active"
    print("Conectando ao gest-service...")
    try:
        resp = requests.get(url, timeout=5)
        resp.raise_for_status()
    except requests.exceptions.ConnectionError:
        print(f"\n[ERRO] gest-service não responde em {GEST_BASE}")
        print("       Inicie o gest-service antes de executar este agente.")
        sys.exit(1)
    except requests.exceptions.Timeout:
        print(f"\n[ERRO] Timeout ({url})")
        sys.exit(1)
    except requests.exceptions.HTTPError as exc:
        print(f"\n[ERRO] HTTP {exc.response.status_code}: {exc.response.text}")
        sys.exit(1)

    data             = resp.json()
    profile_version  = data.get("version", 1)
    contract_version = data.get("contractVersion", "?")
    primitives       = data.get("primitives", [])

    if not primitives:
        print("\n[ERRO] Perfil ativo não possui primitivos. Publique o perfil na UI.")
        sys.exit(1)

    active_gestos = {p["tipoGestoId"] for p in primitives if "tipoGestoId" in p}
    configured    = {g for g in active_gestos if COMANDO_MAP.get(g, "cmd-desconhecido") != "cmd-desconhecido"}

    print(f"  Perfil v{profile_version} (contract {contract_version})")
    print(f"  Primitivos no perfil: {', '.join(sorted(active_gestos))}")
    print(f"  Gestos com comando mapeado: {', '.join(sorted(configured)) or 'NENHUM — verifique COMANDO_MAP'}")

    return profile_version, active_gestos


# ─────────────────────────────────────────────────────────────────────────────
# Cálculos de sensor
# ─────────────────────────────────────────────────────────────────────────────

def _dist(a, b):
    return float(np.linalg.norm(np.array(a) - np.array(b)))


def compute_ear(landmarks, eye_idx, w, h):
    pts = [(landmarks[i].x * w, landmarks[i].y * h) for i in eye_idx]
    p1, p2, p3, p4, p5, p6 = pts
    v1 = _dist(p2, p6)
    v2 = _dist(p3, p5)
    hz = _dist(p1, p4)
    return (v1 + v2) / (2.0 * hz) if hz > 1e-6 else 0.3


def compute_bri(landmarks, side):
    """
    Brow Raise Index = (eye_top_y − brow_peak_y) / inter_eye_dist
    Positivo quando a sobrancelha está acima do topo do olho (estado normal).
    Valor maior = sobrancelha mais levantada.
    """
    if side == "left":
        brow_y = landmarks[LEFT_BROW_PEAK].y
        eye_y  = landmarks[LEFT_EYE_TOP].y
    else:
        brow_y = landmarks[RIGHT_BROW_PEAK].y
        eye_y  = landmarks[RIGHT_EYE_TOP].y

    inter = abs(landmarks[RIGHT_EYE_OUTER].x - landmarks[LEFT_EYE_OUTER].x)
    return (eye_y - brow_y) / inter if inter > 1e-6 else 0.0


# ─────────────────────────────────────────────────────────────────────────────
# Estado dos detectores
# ─────────────────────────────────────────────────────────────────────────────

class DetectorState:
    def __init__(self):
        self.cooldown_until = 0.0

        # ── Double blink (EAR médio, state-machine) ──
        self.db_frames_below = 0
        self.blink_times: list[float] = []
        self.blink_ears:  list[float] = []

        # ── Single blink esq/dir (dispara no reabrir) ──
        self.lb_frames = 0   # frames com olho E fechado e D aberto
        self.rb_frames = 0   # frames com olho D fechado e E aberto

        # ── Brow raise (onset: dispara ao atingir hold mínimo) ──
        self.brow_dual_frames  = 0; self.brow_dual_fired  = False
        self.brow_left_frames  = 0; self.brow_left_fired  = False
        self.brow_right_frames = 0; self.brow_right_fired = False

    def in_cooldown(self, now: float) -> bool:
        return now < self.cooldown_until

    def reset_all(self):
        """Reseta todos os detectores (chamado após cada disparo)."""
        self.cooldown_until   = 0.0  # caller define o valor após chamar reset_all
        self.db_frames_below  = 0
        self.blink_times.clear()
        self.blink_ears.clear()
        self.lb_frames        = 0
        self.rb_frames        = 0
        self.brow_dual_frames = 0; self.brow_dual_fired  = False
        self.brow_left_frames = 0; self.brow_left_fired  = False
        self.brow_right_frames= 0; self.brow_right_fired = False

    def trigger(self, now: float):
        """Chama após confirmação de disparo: seta cooldown e reseta estado."""
        self.reset_all()
        self.cooldown_until = now + COOLDOWN_SECONDS


# ─────────────────────────────────────────────────────────────────────────────
# Motor de detecção — roda uma vez por frame
# ─────────────────────────────────────────────────────────────────────────────

def process_frame(state: DetectorState, ear_l, ear_r, bri_l, bri_r,
                  active_gestos: set, now: float):
    """
    Retorna (tipoGestoId, confidence) se algum gesto disparou, ou (None, 0.0).
    Ordem de prioridade: double blink > piscar esq > piscar dir > sobrancelha dupla
                         > sobrancelha esq > sobrancelha dir.
    """
    if state.in_cooldown(now):
        return None, 0.0

    ear_avg     = (ear_l + ear_r) / 2.0
    both_closed = ear_avg < EAR_CLOSE

    # ── Double blink ─────────────────────────────────────────────────────────
    if "tg-piscar-duplo-001" in active_gestos:
        if both_closed:
            state.db_frames_below += 1
        else:
            if state.db_frames_below >= BLINK_MIN_FRAMES:
                state.blink_times.append(now)
                state.blink_ears.append(ear_avg)
                cutoff = now - DOUBLE_BLINK_WINDOW
                state.blink_times = [t for t in state.blink_times if t > cutoff]
                state.blink_ears  = state.blink_ears[-len(state.blink_times):]
                if len(state.blink_times) >= 2:
                    avg_e = sum(state.blink_ears[-2:]) / 2.0
                    conf  = max(0.0, min(1.0, 1.0 - avg_e / EAR_CLOSE))
                    return "tg-piscar-duplo-001", conf
            state.db_frames_below = 0

    # ── Piscar olho esquerdo (E fecha, D fica aberto) ─────────────────────────
    if "tg-piscar-esquerdo" in active_gestos:
        left_only = ear_l < EAR_CLOSE and ear_r > EAR_OTHER_MAX
        if left_only:
            state.lb_frames += 1
        else:
            # Dispara no reabrir, apenas se fechou frames suficientes e D ainda aberto
            if state.lb_frames >= BLINK_MIN_FRAMES and ear_r > EAR_OTHER_MAX:
                conf = max(0.0, min(1.0, 1.0 - ear_l / EAR_CLOSE))
                state.lb_frames = 0
                return "tg-piscar-esquerdo", conf
            state.lb_frames = 0

    # ── Piscar olho direito (D fecha, E fica aberto) ──────────────────────────
    if "tg-piscar-direito" in active_gestos:
        right_only = ear_r < EAR_CLOSE and ear_l > EAR_OTHER_MAX
        if right_only:
            state.rb_frames += 1
        else:
            if state.rb_frames >= BLINK_MIN_FRAMES and ear_l > EAR_OTHER_MAX:
                conf = max(0.0, min(1.0, 1.0 - ear_r / EAR_CLOSE))
                state.rb_frames = 0
                return "tg-piscar-direito", conf
            state.rb_frames = 0

    # ── Sobrancelha dupla (ambas levantadas, hold) ────────────────────────────
    if "tg-sobrancelha-dupla" in active_gestos:
        both_up = bri_l > BRI_RAISE_ON and bri_r > BRI_RAISE_ON
        if both_up:
            state.brow_dual_frames += 1
            if state.brow_dual_frames >= BROW_MIN_FRAMES and not state.brow_dual_fired:
                state.brow_dual_fired = True
                return "tg-sobrancelha-dupla", 0.85
        else:
            if bri_l < BRI_RAISE_OFF and bri_r < BRI_RAISE_OFF:
                state.brow_dual_frames = 0
                state.brow_dual_fired  = False

    # ── Sobrancelha esquerda (E levantada, D relaxada) ────────────────────────
    if "tg-sobrancelha-esquerda" in active_gestos:
        left_up = bri_l > BRI_RAISE_ON and bri_r < BRI_OTHER_MAX
        if left_up:
            state.brow_left_frames += 1
            if state.brow_left_frames >= BROW_MIN_FRAMES and not state.brow_left_fired:
                state.brow_left_fired = True
                return "tg-sobrancelha-esquerda", 0.80
        else:
            if bri_l < BRI_RAISE_OFF:
                state.brow_left_frames = 0
                state.brow_left_fired  = False

    # ── Sobrancelha direita (D levantada, E relaxada) ─────────────────────────
    if "tg-sobrancelha-direita" in active_gestos:
        right_up = bri_r > BRI_RAISE_ON and bri_l < BRI_OTHER_MAX
        if right_up:
            state.brow_right_frames += 1
            if state.brow_right_frames >= BROW_MIN_FRAMES and not state.brow_right_fired:
                state.brow_right_fired = True
                return "tg-sobrancelha-direita", 0.80
        else:
            if bri_r < BRI_RAISE_OFF:
                state.brow_right_frames = 0
                state.brow_right_fired  = False

    return None, 0.0


# ─────────────────────────────────────────────────────────────────────────────
# Envio de report
# ─────────────────────────────────────────────────────────────────────────────

def send_report(tipo_gesto_id: str, profile_version: int, confidence: float, debug: bool):
    comando_id = COMANDO_MAP.get(tipo_gesto_id, "cmd-desconhecido")
    payload = {
        "reportId":       str(uuid.uuid4()),
        "pacienteId":     PACIENTE_ID,
        "commandRef":     comando_id,
        "primitives":     [{"tipoGestoId": tipo_gesto_id, "confidence": round(confidence, 4)}],
        "confidence":     round(confidence, 4),
        "profileVersion": profile_version,
        "timingWindow":   {"startMs": 0, "endMs": 500, "durationMs": 500},
        "recognizedAt":   datetime.now(timezone.utc).isoformat(),
        "deviceId":       DEVICE_ID,
    }
    if debug:
        print(f"\n  [DEBUG] POST {EVNT_BASE}/reports  payload={payload}")
    try:
        resp = requests.post(f"{EVNT_BASE}/reports", json=payload, timeout=5)
        resp.raise_for_status()
        body      = resp.json()
        evento_id = body.get("eventoId", "N/A")
        tier      = body.get("notification", {}).get("tier", body.get("tier", "N/A"))
        label     = GESTO_LABEL.get(tipo_gesto_id, tipo_gesto_id)
        ts        = datetime.now().strftime("%H:%M:%S")
        print(f"[{ts}] DISPARADO  gesto={label}  comando={comando_id}  evento={evento_id}  tier={tier}")
        return True, f"OK: {label}"
    except requests.exceptions.ConnectionError:
        print(f"[AVISO] evnt-service offline. Report não enviado.")
        return False, "evnt offline"
    except requests.exceptions.HTTPError as exc:
        print(f"[AVISO] HTTP {exc.response.status_code}: {exc.response.text[:120]}")
        return False, f"HTTP {exc.response.status_code}"
    except Exception as exc:
        print(f"[AVISO] Erro: {exc}")
        return False, "erro de envio"


# ─────────────────────────────────────────────────────────────────────────────
# Overlay helpers
# ─────────────────────────────────────────────────────────────────────────────

def put_text(frame, text, pos, color=COLOR_WHITE, scale=0.55, thickness=1):
    font = cv2.FONT_HERSHEY_SIMPLEX
    cv2.putText(frame, text, (pos[0]+1, pos[1]+1), font, scale, COLOR_DARK, thickness+1, cv2.LINE_AA)
    cv2.putText(frame, text, pos,                  font, scale, color,      thickness,   cv2.LINE_AA)


def draw_landmarks(frame, lms, w, h, ear_l, ear_r, bri_l, bri_r):
    for idx in LEFT_EYE:
        lm = lms[idx]
        cv2.circle(frame, (int(lm.x*w), int(lm.y*h)), 2,
                   COLOR_RED if ear_l < EAR_CLOSE else COLOR_GREEN, -1)
    for idx in RIGHT_EYE:
        lm = lms[idx]
        cv2.circle(frame, (int(lm.x*w), int(lm.y*h)), 2,
                   COLOR_RED if ear_r < EAR_CLOSE else COLOR_GREEN, -1)
    for idx, bri in ((LEFT_BROW_PEAK, bri_l), (RIGHT_BROW_PEAK, bri_r)):
        lm = lms[idx]
        color = COLOR_YELLOW if bri > BRI_RAISE_ON else COLOR_CYAN
        cv2.circle(frame, (int(lm.x*w), int(lm.y*h)), 4, color, -1)


# ─────────────────────────────────────────────────────────────────────────────
# Loop principal
# ─────────────────────────────────────────────────────────────────────────────

def run_detection(camera_index: int, profile_version: int,
                  active_gestos: set, debug: bool):
    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        print(f"\n[ERRO] Não foi possível abrir a câmera (índice {camera_index}).")
        sys.exit(1)

    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"Câmera {camera_index} iniciada ({fw}×{fh}). Pressione 'q' para sair.\n")

    face_mesh = mp.solutions.face_mesh.FaceMesh(
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    state = DetectorState()
    status_msg   = "AGUARDANDO GESTO..."
    status_color = COLOR_GREEN
    feedback_until = 0.0
    feedback_msg   = ""
    feedback_ok    = True

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            now   = time.time()
            frame = cv2.flip(frame, 1)
            rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            result = face_mesh.process(rgb)

            ear_l = ear_r = 0.30
            bri_l = bri_r = 0.20
            face_found = False

            if result.multi_face_landmarks:
                face_found = True
                lms = result.multi_face_landmarks[0].landmark

                ear_l = compute_ear(lms, LEFT_EYE,  fw, fh)
                ear_r = compute_ear(lms, RIGHT_EYE, fw, fh)
                bri_l = compute_bri(lms, "left")
                bri_r = compute_bri(lms, "right")

                if debug:
                    print(
                        f"  EAR esq={ear_l:.3f} dir={ear_r:.3f}  "
                        f"BRI esq={bri_l:.3f} dir={bri_r:.3f}",
                        end="\r"
                    )

                draw_landmarks(frame, lms, fw, fh, ear_l, ear_r, bri_l, bri_r)

                fired, confidence = process_frame(
                    state, ear_l, ear_r, bri_l, bri_r, active_gestos, now
                )

                if fired:
                    label        = GESTO_LABEL.get(fired, fired)
                    status_msg   = f"DETECTADO: {label}"
                    status_color = COLOR_RED

                    # Mostra ENVIANDO por ao menos 1 frame antes do HTTP
                    cv2.putText(frame, "ENVIANDO ALARME...", (fw//2 - 130, fh//2),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.9, COLOR_RED, 2, cv2.LINE_AA)
                    cv2.imshow("NeuroAssist AI — Edge Agent", frame)
                    cv2.waitKey(1)

                    ok, msg = send_report(fired, profile_version, confidence, debug)
                    state.trigger(now)

                    status_msg   = f"ALARME: {label}" if ok else "FALHOU: sem envio"
                    status_color = COLOR_RED if ok else COLOR_ORANGE
                    feedback_msg   = msg
                    feedback_ok    = ok
                    feedback_until = now + 3.0

            # ── Painel de sensores (topo esquerdo) ────────────────────────────
            overlay = frame.copy()
            cv2.rectangle(overlay, (5, 5), (240, 115), COLOR_DARK, -1)
            cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)

            put_text(frame, f"EAR esq: {ear_l:.3f}", (12, 26),
                     color=COLOR_RED if ear_l < EAR_CLOSE else COLOR_GREEN)
            put_text(frame, f"EAR dir: {ear_r:.3f}", (12, 46),
                     color=COLOR_RED if ear_r < EAR_CLOSE else COLOR_GREEN)
            put_text(frame, f"BRI esq: {bri_l:.3f}", (12, 67),
                     color=COLOR_YELLOW if bri_l > BRI_RAISE_ON else COLOR_WHITE)
            put_text(frame, f"BRI dir: {bri_r:.3f}", (12, 88),
                     color=COLOR_YELLOW if bri_r > BRI_RAISE_ON else COLOR_WHITE)

            # Cooldown e progresso do double blink
            in_cd = state.in_cooldown(now)
            cd_label = f"Cooldown: {state.cooldown_until - now:.1f}s" if in_cd else "Pronto"
            put_text(frame, cd_label, (12, 108),
                     color=COLOR_ORANGE if in_cd else COLOR_GREEN)

            if "tg-piscar-duplo-001" in active_gestos:
                bc = len([t for t in state.blink_times if now - t <= DOUBLE_BLINK_WINDOW])
                put_text(frame, f"Blinks: {bc}/2", (12, 128),
                         color=COLOR_YELLOW if bc >= 1 else COLOR_WHITE)

            # ── Status central ────────────────────────────────────────────────
            (tw, _), _ = cv2.getTextSize(status_msg, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)
            put_text(frame, status_msg, ((fw - tw)//2, 38),
                     color=status_color, scale=0.7, thickness=2)

            if now < feedback_until:
                fcolor = COLOR_GREEN if feedback_ok else COLOR_ORANGE
                (fw2, _), _ = cv2.getTextSize(feedback_msg, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 1)
                put_text(frame, feedback_msg, ((fw - fw2)//2, 68), color=fcolor, scale=0.55)
            elif status_msg not in ("AGUARDANDO GESTO...",):
                status_msg   = "AGUARDANDO GESTO..."
                status_color = COLOR_GREEN

            if not face_found:
                put_text(frame, "Nenhum rosto detectado", (fw//2 - 110, fh//2),
                         color=COLOR_ORANGE, scale=0.65)

            # ── Rodapé: gestos ativos com comando mapeado ─────────────────────
            configured_labels = [
                GESTO_LABEL[g] for g in sorted(active_gestos)
                if g in GESTO_LABEL and COMANDO_MAP.get(g, "cmd-desconhecido") != "cmd-desconhecido"
            ]
            footer = "  |  ".join(configured_labels) + "   ('q' sair)"
            (iw, _), _ = cv2.getTextSize(footer, cv2.FONT_HERSHEY_SIMPLEX, 0.40, 1)
            cv2.rectangle(frame, (0, fh - 28), (fw, fh), COLOR_DARK, -1)
            put_text(frame, footer, ((fw - iw)//2, fh - 10), color=COLOR_CYAN, scale=0.40)

            cv2.imshow("NeuroAssist AI — Edge Agent", frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                print("\nEncerrando...")
                break

    finally:
        cap.release()
        cv2.destroyAllWindows()
        face_mesh.close()


# ─────────────────────────────────────────────────────────────────────────────
# Ponto de entrada
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="NeuroAssist AI — Edge Agent: detecta gestos faciais e envia reports."
    )
    parser.add_argument("--camera", type=int, default=0, help="Índice da câmera (default: 0)")
    parser.add_argument("--no-pull", action="store_true",
                        help="Pula pull do perfil; usa gestos default do seed")
    parser.add_argument("--debug", action="store_true",
                        help="Exibe EAR/BRI por frame no terminal para calibrar thresholds")
    args = parser.parse_args()

    print("NeuroAssist AI — Edge Agent (webcam)")
    print("=" * 40)

    if args.no_pull:
        print(f"Modo --no-pull: version={DEFAULT_PROFILE_VERSION}, gestos={DEFAULT_ACTIVE_GESTOS}")
        profile_version = DEFAULT_PROFILE_VERSION
        active_gestos   = DEFAULT_ACTIVE_GESTOS
    else:
        profile_version, active_gestos = pull_active_profile()

    if args.debug:
        print("\nModo --debug ativo. Use os valores de EAR/BRI no terminal para ajustar")
        print(f"  BRI_RAISE_ON={BRI_RAISE_ON}  BRI_RAISE_OFF={BRI_RAISE_OFF}  "
              f"EAR_CLOSE={EAR_CLOSE}  EAR_OTHER_MAX={EAR_OTHER_MAX}\n")

    print()
    run_detection(args.camera, profile_version, active_gestos, args.debug)


if __name__ == "__main__":
    main()
