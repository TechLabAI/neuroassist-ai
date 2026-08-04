#!/usr/bin/env python3
"""
scripts/paper/false_positive_experiment.py — False positive experiment for the NeuroAssist AI paper.

Runs the gesture detection loop for N minutes WITHOUT sending reports.
Records per-frame data comparing two algorithms:
  1. State machine (our system)   — process_frame() with tg-piscar-duplo-001
  2. Simple threshold (baseline)  — single-frame EAR average onset crossing

Protocol (from PAPER-PLAN.md §4.2):
  Participant sits naturally in front of webcam for the specified duration.
  They should NOT attempt to trigger the gesture — just talk, read, look around.

Usage:
  cd backend
  py -3.11 scripts/paper/false_positive_experiment.py --duration 300 --participant P1
  py -3.11 scripts/paper/false_positive_experiment.py --duration 300 --participant P2 --ear-close 0.20

Output: scripts/paper/data/fp_<participant>_<session_id>.csv
"""

import argparse
import csv
import importlib.util
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).parent.parent.parent
_spec = importlib.util.spec_from_file_location("edge_agent", ROOT / "poc-edge-agent-webcam.py")
_agent = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_agent)  # type: ignore[union-attr]

import cv2
import mediapipe as mp

DATA_DIR = Path(__file__).parent / "data"
DATA_DIR.mkdir(exist_ok=True)


def run(camera: int, duration_s: float, participant: str, ear_close_override: float | None) -> None:
    session_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_path = DATA_DIR / f"fp_{participant}_{session_id}.csv"

    # Simple threshold uses the same EAR_CLOSE as the state machine (fair comparison)
    ear_close_simple = ear_close_override if ear_close_override is not None else _agent.EAR_CLOSE

    fieldnames = [
        "frame", "elapsed_ms",
        "ear_left", "ear_right", "ear_avg",
        "bri_left", "bri_right",
        "sm_blink_count",
        "sm_triggered",
        "simple_triggered",
    ]

    cap = cv2.VideoCapture(camera)
    if not cap.isOpened():
        print(f"[ERRO] Câmera {camera} não disponível.")
        sys.exit(1)

    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print(f"\nParticipante: {participant}  |  Duração: {duration_s:.0f}s  |  Câmera: {camera} ({fw}×{fh})")
    print(f"EAR_CLOSE (ambos): {ear_close_simple}  |  BLINK_MIN_FRAMES: {_agent.BLINK_MIN_FRAMES}")
    print(f"Output: {csv_path}")
    print("\n⚠  INSTRUÇÃO: NÃO tente piscar duas vezes propositalmente.")
    print("   Fale, leia, olhe para os lados normalmente.\n")
    print("─" * 60)

    face_mesh = mp.solutions.face_mesh.FaceMesh(
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    sm_state   = _agent.DetectorState()
    active_ges = {"tg-piscar-duplo-001"}

    sm_trigger_count     = 0
    simple_trigger_count = 0
    frame_n              = 0
    simple_was_below     = False
    start_ts             = time.time()

    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        try:
            while True:
                elapsed = time.time() - start_ts
                if elapsed >= duration_s:
                    break

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

                ear_avg = (ear_l + ear_r) / 2.0

                # State machine
                fired_sm, _ = _agent.process_frame(
                    sm_state, ear_l, ear_r, bri_l, bri_r, active_ges, now
                )
                if fired_sm:
                    sm_state.trigger(now)
                    sm_trigger_count += 1

                # Simple threshold baseline (onset = first frame below threshold)
                currently_below  = ear_avg < ear_close_simple
                simple_triggered = currently_below and not simple_was_below
                simple_was_below = currently_below
                if simple_triggered:
                    simple_trigger_count += 1

                writer.writerow({
                    "frame":          frame_n,
                    "elapsed_ms":     f"{elapsed * 1000:.0f}",
                    "ear_left":       f"{ear_l:.4f}",
                    "ear_right":      f"{ear_r:.4f}",
                    "ear_avg":        f"{ear_avg:.4f}",
                    "bri_left":       f"{bri_l:.4f}",
                    "bri_right":      f"{bri_r:.4f}",
                    "sm_blink_count": len(sm_state.blink_times),
                    "sm_triggered":   1 if fired_sm else 0,
                    "simple_triggered": 1 if simple_triggered else 0,
                })
                frame_n += 1

                remaining = duration_s - elapsed
                cv2.putText(frame,
                            f"FP Experiment [{participant}]  {remaining:.0f}s restantes",
                            (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 1, cv2.LINE_AA)
                cv2.putText(frame,
                            f"SM: {sm_trigger_count} FP   Simple: {simple_trigger_count} FP",
                            (10, 55), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
                cv2.putText(frame,
                            "NAO dispare gestos — observacao natural",
                            (10, 78), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 165, 255), 1, cv2.LINE_AA)

                cv2.imshow(f"NeuroAssist AI — FP Experiment [{participant}]", frame)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    print("\nInterrompido.")
                    break

        finally:
            cap.release()
            cv2.destroyAllWindows()
            face_mesh.close()

    actual_duration = time.time() - start_ts
    duration_min    = actual_duration / 60.0

    print(f"\n{'─'*60}")
    print(f"Participante:  {participant}")
    print(f"Duração real:  {actual_duration:.1f}s  ({duration_min:.2f} min)  |  Frames: {frame_n}")
    print()
    print(f"{'Algoritmo':<25} {'Triggers':>8}  {'Rate/min':>10}")
    print(f"{'State Machine':<25} {sm_trigger_count:>8}  {sm_trigger_count / duration_min:>9.2f}")
    print(f"{'Simple Threshold':<25} {simple_trigger_count:>8}  {simple_trigger_count / duration_min:>9.2f}")
    print(f"\nCSV: {csv_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="NeuroAssist AI — False positive experiment")
    parser.add_argument("--camera",      type=int,   default=0)
    parser.add_argument("--duration",    type=float, default=300,
                        help="Duração em segundos (default: 300 = 5 min)")
    parser.add_argument("--participant", type=str,   default="P1",
                        help="ID do participante (ex: P1, P2)")
    parser.add_argument("--ear-close",   type=float, default=None,
                        help="Override EAR_CLOSE para o threshold simples (default: usa valor do edge agent)")
    args = parser.parse_args()

    run(args.camera, args.duration, args.participant, args.ear_close)


if __name__ == "__main__":
    main()
