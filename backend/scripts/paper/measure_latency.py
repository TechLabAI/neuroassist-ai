#!/usr/bin/env python3
"""
scripts/paper/measure_latency.py — End-to-end latency measurement for the NeuroAssist AI paper.

Records per-trial timestamps:
  gesture_completed_ts  — when process_frame() fires (time.time(), Python-side)
  report_sent_ts        — when POST /api/v1/reports returned HTTP 201 (time.time(), Python-side)
  event_created_iso     — server-side Instant.now() from the response JSON (createdAt field)

  gesture_to_http_ms    = (report_sent_ts - gesture_completed_ts) * 1000
  event_to_alarm_ms     — computed later by generate_tables.py using the Angular alarm log

After the session, export the Angular alarm log:
  1. Open browser DevTools → Console and run:
       JSON.stringify(JSON.parse(localStorage.getItem('_neuroassist_alarm_log') || '[]'), null, 2)
  2. Save the output to: scripts/paper/data/alarm_log_<same_session_id>.json

Usage:
  cd backend
  py -3.11 scripts/paper/measure_latency.py
  py -3.11 scripts/paper/measure_latency.py --trials 50 --camera 1
  py -3.11 scripts/paper/measure_latency.py --no-pull

Output: scripts/paper/data/latency_<session_id>.csv
"""

import argparse
import csv
import importlib.util
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

# ── Load poc-edge-agent-webcam.py as a module ─────────────────────────────────
# We import it to reuse the EXACT same detection logic and thresholds as production.
ROOT = Path(__file__).parent.parent.parent  # backend/
_spec = importlib.util.spec_from_file_location("edge_agent", ROOT / "poc-edge-agent-webcam.py")
_agent = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_agent)  # type: ignore[union-attr]

import cv2
import mediapipe as mp
import requests

DATA_DIR = Path(__file__).parent / "data"
DATA_DIR.mkdir(exist_ok=True)

EVNT_BASE = _agent.EVNT_BASE


# ─── HTTP send with timing ────────────────────────────────────────────────────

def send_report_timed(tipo_gesto_id: str, profile_version: int, confidence: float) -> dict:
    payload = {
        "reportId":       str(uuid.uuid4()),
        "pacienteId":     _agent.PACIENTE_ID,
        "commandRef":     _agent.COMANDO_MAP.get(tipo_gesto_id, "cmd-desconhecido"),
        "primitives":     [{"tipoGestoId": tipo_gesto_id, "confidence": round(confidence, 4)}],
        "confidence":     round(confidence, 4),
        "profileVersion": profile_version,
        "timingWindow":   {"startMs": 0, "endMs": 500, "durationMs": 500},
        "recognizedAt":   datetime.now(timezone.utc).isoformat(),
        "deviceId":       "measure-latency-script",
    }
    try:
        resp = requests.post(f"{EVNT_BASE}/reports", json=payload, timeout=10)
        resp.raise_for_status()
        body = resp.json()
        sent_ts = time.time()
        return {
            "ok":              True,
            "event_id":        str(body.get("eventoId", "")),
            "event_created_iso": body.get("createdAt", ""),
            "sent_ts":         sent_ts,
        }
    except Exception as exc:
        return {
            "ok": False, "event_id": "", "event_created_iso": "",
            "sent_ts": time.time(), "error": str(exc),
        }


# ─── Detection loop ───────────────────────────────────────────────────────────

def run(camera: int, profile_version: int, active_gestos: set, n_trials: int) -> None:
    session_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_path = DATA_DIR / f"latency_{session_id}.csv"

    fieldnames = [
        "trial", "event_id", "gesto",
        "gesture_completed_iso", "gesture_completed_ts",
        "report_sent_iso",       "report_sent_ts",
        "event_created_iso",
        "gesture_to_http_ms",
    ]

    cap = cv2.VideoCapture(camera)
    if not cap.isOpened():
        print(f"[ERRO] Câmera {camera} não disponível.")
        sys.exit(1)

    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"\nCâmera {camera} ({fw}×{fh})  |  Alvo: {n_trials} trials  |  'q' para sair\n")
    print(f"Output: {csv_path}\n{'─'*60}")

    face_mesh = mp.solutions.face_mesh.FaceMesh(
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    state = _agent.DetectorState()
    trials_done = 0

    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        try:
            while trials_done < n_trials:
                ret, frame = cap.read()
                if not ret:
                    break

                now   = time.time()
                frame = cv2.flip(frame, 1)
                rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = face_mesh.process(rgb)

                ear_l = ear_r = 0.30
                bri_l = bri_r = 0.20

                if result.multi_face_landmarks:
                    lms = result.multi_face_landmarks[0].landmark
                    ear_l = _agent.compute_ear(lms, _agent.LEFT_EYE,  fw, fh)
                    ear_r = _agent.compute_ear(lms, _agent.RIGHT_EYE, fw, fh)
                    bri_l = _agent.compute_bri(lms, "left")
                    bri_r = _agent.compute_bri(lms, "right")
                    _agent.draw_landmarks(frame, lms, fw, fh, ear_l, ear_r, bri_l, bri_r)

                    fired, confidence = _agent.process_frame(
                        state, ear_l, ear_r, bri_l, bri_r, active_gestos, now
                    )

                    if fired:
                        gesture_ts  = now
                        gesture_iso = datetime.fromtimestamp(gesture_ts, tz=timezone.utc).isoformat()

                        cv2.putText(frame, "ENVIANDO...", (fw // 2 - 70, fh // 2),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 0, 255), 2, cv2.LINE_AA)
                        cv2.imshow("NeuroAssist AI — Latency Measurement", frame)
                        cv2.waitKey(1)

                        res = send_report_timed(fired, profile_version, confidence)
                        state.trigger(now)

                        sent_ts   = res["sent_ts"]
                        sent_iso  = datetime.fromtimestamp(sent_ts, tz=timezone.utc).isoformat()
                        g2h_ms    = (sent_ts - gesture_ts) * 1000.0
                        trials_done += 1

                        writer.writerow({
                            "trial":                 trials_done,
                            "event_id":              res["event_id"],
                            "gesto":                 fired,
                            "gesture_completed_iso": gesture_iso,
                            "gesture_completed_ts":  f"{gesture_ts:.6f}",
                            "report_sent_iso":       sent_iso,
                            "report_sent_ts":        f"{sent_ts:.6f}",
                            "event_created_iso":     res["event_created_iso"],
                            "gesture_to_http_ms":    f"{g2h_ms:.1f}",
                        })
                        f.flush()

                        status = "OK" if res["ok"] else f"FALHOU ({res.get('error', '')})"
                        print(f"  Trial {trials_done:3d}/{n_trials}  gesture→http={g2h_ms:.0f}ms  {status}")

                remaining = n_trials - trials_done
                label = f"Trial {trials_done + 1}/{n_trials} — pisque duplo ({remaining} restantes)"
                cv2.putText(frame, label, (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 1, cv2.LINE_AA)
                cv2.putText(frame, f"EAR avg: {(ear_l + ear_r) / 2:.3f}", (10, 56),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)

                cv2.imshow("NeuroAssist AI — Latency Measurement", frame)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    print("\nInterrompido.")
                    break

        finally:
            cap.release()
            cv2.destroyAllWindows()
            face_mesh.close()

    print(f"\n{'─'*60}")
    print(f"Trials: {trials_done}/{n_trials}  |  CSV: {csv_path}")
    print()
    print("Próximo passo — exportar alarm log do Angular:")
    print("  1. Abra o browser com o prototype Angular")
    print("  2. F12 → Console → cole e execute:")
    print("     JSON.stringify(JSON.parse(localStorage.getItem('_neuroassist_alarm_log')||'[]'),null,2)")
    print(f"  3. Salve em: scripts/paper/data/alarm_log_{session_id}.json")
    print(f"  4. Execute: py -3.11 scripts/paper/generate_tables.py "
          f"--latency latency_{session_id}.csv --alarm alarm_log_{session_id}.json")


# ─── Entry point ─────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="NeuroAssist AI — Latency measurement for paper")
    parser.add_argument("--camera",  type=int, default=0)
    parser.add_argument("--trials",  type=int, default=50)
    parser.add_argument("--no-pull", action="store_true")
    args = parser.parse_args()

    print("NeuroAssist AI — Latency Measurement")
    print("=" * 40)

    if args.no_pull:
        pv  = _agent.DEFAULT_PROFILE_VERSION
        ges = _agent.DEFAULT_ACTIVE_GESTOS.copy()
    else:
        pv, ges = _agent.pull_active_profile()

    # Measure only double blink (primary gesture for the paper)
    ges = ges & {"tg-piscar-duplo-001"}
    if not ges:
        ges = {"tg-piscar-duplo-001"}

    print(f"\nGesto: tg-piscar-duplo-001  |  Profile v{pv}  |  Trials: {args.trials}")
    run(args.camera, pv, ges, args.trials)


if __name__ == "__main__":
    main()
