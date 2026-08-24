#!/usr/bin/env python3
"""
Edge Agent — Live-Test WS Bridge — NeuroAssist AI

Streams EdgeRecognitionSignal frames (GEST event-contracts.md §3.2, frozen
v1.0.0) to the browser's calibration screen over a localhost WebSocket, per
GEST-FE-007 (EdgeDetectionClient) / GEST-INT-004. Complements — does not
replace — poc-edge-agent-webcam.py, which posts recognized gestures straight
to evnt-service for the always-on production flow; this script exists only
for the interactive "test this gesture" calibration loop (PAC calibracao),
where the browser needs live, per-attempt feedback instead of a fire-and-forget
report.

Detection algorithm: thresholds and the EAR/BRI math live in edge_agent_core.py
and are imported from there (E2E-032). Only the gesture state machine and the
cooldown are local — the calibration loop is single-shot, so it recovers faster
than the always-on agent. Everything that decides WHETHER a gesture happened is
shared; only WHEN the bridge is ready for the next attempt is local.

Auth: PROVISIONAL. The localhost bridge handshake carries no real credential
yet — GEST-INT-004 (FE) / INT-GEST-007 (BE) track the joint mTLS-vs-OAuth2
decision with the EDGE team. This script accepts any connection; do not expose
this port beyond localhost.

Pré-requisitos:
  pip install opencv-python mediapipe numpy websockets

Uso:
  python edge-agent-ws.py
  python edge-agent-ws.py --camera 1 --port 8765
  python edge-agent-ws.py --debug
"""

import argparse
import asyncio
import json
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from queue import Empty, Queue

import cv2
import mediapipe as mp
import websockets

# Thresholds and sensor math: single source (E2E-032). sys.path[0] is this script's own
# directory, so the import resolves regardless of the working directory.
from edge_agent_core import (
    DEFAULT_BAND,
    DEFAULT_BANDS,
    LEFT_EYE,
    RIGHT_EYE,
    compute_bri,
    compute_ear,
    thresholds_for,
)

CONTRACT_VERSION = "1.0.0"

# Deliberately NOT shared with edge_agent_core: the calibration loop is a single-shot test,
# so it frees up sooner than the always-on agent (3.0 s there). The drift guard excludes this
# name for that reason.
COOLDOWN_SECONDS = 1.5


class DetectorState:
    """Per-session detector state — reset whenever the watched gesture changes."""

    def __init__(self):
        self.cooldown_until = 0.0
        self.db_frames_below = 0
        self.blink_times: list[float] = []
        self.lb_frames = 0
        self.rb_frames = 0
        self.brow_dual_frames = 0
        self.brow_left_frames = 0
        self.brow_right_frames = 0

    def in_cooldown(self, now: float) -> bool:
        return now < self.cooldown_until


def process_frame(state: DetectorState, ear_l, ear_r, bri_l, bri_r, watched: str, now: float, th):
    # INTERIM: EAR/BRI detector — not ONNX
    # Detection uses Eye Aspect Ratio (EAR) + Brow Raise Index (BRI) thresholds.
    # The production PEA will use an ONNX classifier (gesture_v1.onnx) — tracked in
    # INT-GEST-004 / INT-GEST-007. This interim is bounded to the pre-PEA period.
    # See ADR-006-ear-bri-interim-detector.md for rationale and safeguards.
    """
    Detect exactly the single `watched` tipoGestoId. Returns (fired: bool, confidence).

    `th` are the thresholds for THIS gesture, from the published profile (E2E-033) — the same
    gesture can be watched at a different sensitivity than its neighbour in the same profile.
    """
    if state.in_cooldown(now):
        return False, 0.0

    ear_avg = (ear_l + ear_r) / 2.0
    both_closed = ear_avg < th.ear_close

    if watched == "tg-piscar-duplo-001":
        if both_closed:
            state.db_frames_below += 1
        else:
            if state.db_frames_below >= th.blink_min_frames:
                state.blink_times.append(now)
                cutoff = now - th.double_blink_window
                state.blink_times = [t for t in state.blink_times if t > cutoff]
                if len(state.blink_times) >= 2:
                    conf = max(0.0, min(1.0, 1.0 - ear_avg / th.ear_close))
                    return True, conf
            state.db_frames_below = 0

    elif watched == "tg-piscar-esquerdo":
        left_only = ear_l < th.ear_close and ear_r > th.ear_other_max
        if left_only:
            state.lb_frames += 1
        else:
            if state.lb_frames >= th.blink_min_frames and ear_r > th.ear_other_max:
                conf = max(0.0, min(1.0, 1.0 - ear_l / th.ear_close))
                state.lb_frames = 0
                return True, conf
            state.lb_frames = 0

    elif watched == "tg-piscar-direito":
        right_only = ear_r < th.ear_close and ear_l > th.ear_other_max
        if right_only:
            state.rb_frames += 1
        else:
            if state.rb_frames >= th.blink_min_frames and ear_l > th.ear_other_max:
                conf = max(0.0, min(1.0, 1.0 - ear_r / th.ear_close))
                state.rb_frames = 0
                return True, conf
            state.rb_frames = 0

    elif watched == "tg-sobrancelha-dupla":
        if bri_l > th.bri_raise_on and bri_r > th.bri_raise_on:
            state.brow_dual_frames += 1
            if state.brow_dual_frames >= th.brow_min_frames:
                return True, 0.85
        elif bri_l < th.bri_raise_off and bri_r < th.bri_raise_off:
            state.brow_dual_frames = 0

    elif watched == "tg-sobrancelha-esquerda":
        if bri_l > th.bri_raise_on and bri_r < th.bri_other_max:
            state.brow_left_frames += 1
            if state.brow_left_frames >= th.brow_min_frames:
                return True, 0.80
        elif bri_l < th.bri_raise_off:
            state.brow_left_frames = 0

    elif watched == "tg-sobrancelha-direita":
        if bri_r > th.bri_raise_on and bri_l < th.bri_other_max:
            state.brow_right_frames += 1
            if state.brow_right_frames >= th.brow_min_frames:
                return True, 0.80
        elif bri_r < th.bri_raise_off:
            state.brow_right_frames = 0

    return False, 0.0


class CameraWorker(threading.Thread):
    """Runs OpenCV + MediaPipe in a background thread; pushes fired detections
    onto a thread-safe queue the asyncio WS handler drains. Camera opens lazily
    on the first watched gesture and releases when idle (courtesy to other
    consumers of the same device, e.g. a browser preview)."""

    def __init__(self, camera_index: int, out_queue: Queue, debug: bool):
        super().__init__(daemon=True)
        self.camera_index = camera_index
        self.out_queue = out_queue
        self.debug = debug
        self._watched: str | None = None
        self._thresholds = DEFAULT_BANDS[DEFAULT_BAND]
        self._lock = threading.Lock()
        self._stop = threading.Event()

    def set_watched(self, tipo_gesto_id: str | None, thresholds=None) -> None:
        with self._lock:
            self._watched = tipo_gesto_id
            self._thresholds = thresholds or DEFAULT_BANDS[DEFAULT_BAND]

    def stop(self) -> None:
        self._stop.set()

    def run(self) -> None:
        cap = None
        face_mesh = None
        state = DetectorState()
        last_watched: str | None = None

        try:
            while not self._stop.is_set():
                with self._lock:
                    watched = self._watched
                    thresholds = self._thresholds

                if watched is None:
                    if cap is not None:
                        cap.release()
                        cap = None
                    time.sleep(0.1)
                    continue

                if cap is None:
                    cap = cv2.VideoCapture(self.camera_index)
                    if not cap.isOpened():
                        self.out_queue.put(("camera_error", None))
                        cap = None
                        time.sleep(1.0)
                        continue
                    face_mesh = mp.solutions.face_mesh.FaceMesh(
                        max_num_faces=1, refine_landmarks=True,
                        min_detection_confidence=0.5, min_tracking_confidence=0.5,
                    )

                if watched != last_watched:
                    state = DetectorState()
                    last_watched = watched

                ret, frame = cap.read()
                if not ret:
                    continue

                now = time.time()
                frame = cv2.flip(frame, 1)
                fh, fw = frame.shape[:2]
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = face_mesh.process(rgb)

                if not result.multi_face_landmarks:
                    continue

                lms = result.multi_face_landmarks[0].landmark
                ear_l = compute_ear(lms, LEFT_EYE, fw, fh)
                ear_r = compute_ear(lms, RIGHT_EYE, fw, fh)
                bri_l = compute_bri(lms, "left")
                bri_r = compute_bri(lms, "right")

                if self.debug:
                    print(f"  watching={watched} EAR={ear_l:.3f}/{ear_r:.3f} "
                          f"BRI={bri_l:.3f}/{bri_r:.3f}", end="\r")

                fired, confidence = process_frame(
                    state, ear_l, ear_r, bri_l, bri_r, watched, now, thresholds
                )
                if fired:
                    state.cooldown_until = now + COOLDOWN_SECONDS
                    self.out_queue.put(("recognized", (watched, confidence)))
        finally:
            if cap is not None:
                cap.release()
            if face_mesh is not None:
                face_mesh.close()


async def handle_client(websocket, camera_index: int, debug: bool):
    peer = websocket.remote_address
    print(f"[connect] {peer}")

    signal_queue: Queue = Queue()
    worker = CameraWorker(camera_index, signal_queue, debug)
    worker.start()

    session = {"commandDraftRef": None, "watching": None, "band": DEFAULT_BAND}

    async def send_signal(kind: str, primitives=None, degraded=False):
        primitives = primitives or []
        payload = {
            "type": "signal",
            "signal": {
                "contractVersion": CONTRACT_VERSION,
                "signalKind": kind,
                "commandDraftRef": session["commandDraftRef"] or "",
                "primitives": primitives,
                "timingWindow": {"startMs": 0, "endMs": 500, "durationMs": 500},
                "degraded": degraded,
                "emittedAt": datetime.now(timezone.utc).isoformat(),
            },
        }
        await websocket.send(json.dumps(payload))

    async def drain_queue():
        while True:
            try:
                kind, data = signal_queue.get_nowait()
            except Empty:
                await asyncio.sleep(0.05)
                continue
            if kind == "camera_error":
                await send_signal("degraded", degraded=True)
            elif kind == "recognized":
                tipo_gesto_id, confidence = data
                if tipo_gesto_id == session["watching"]:
                    await send_signal(
                        "recognized",
                        primitives=[{"engineFeatureRef": tipo_gesto_id, "confidence": round(confidence, 4)}],
                    )
                    print(f"[recognized] {tipo_gesto_id} conf={confidence:.2f} -> {peer}")

    drain_task = asyncio.create_task(drain_queue())

    try:
        await websocket.send(json.dumps({
            "type": "hello",
            "contractVersion": CONTRACT_VERSION,
            "detectorType": "ear_bri",  # ADR-006: interim EAR/BRI detector — not ONNX
            # Send the active detection profile in start_session and this bridge will calibrate
            # with the same thresholds the always-on detection uses (E2E-033). Omit it and it
            # falls back to the `media` band.
            "acceptsProfile": True,
            "defaultBand": DEFAULT_BAND,
        }))

        async for raw in websocket:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue

            if msg.get("type") == "start_session":
                session["commandDraftRef"] = msg.get("commandDraftRef")
                session["watching"] = msg.get("tipoGestoId")

                # The calibration screen may hand us the published detection profile (E2E-033).
                # It is the browser that carries it, not this bridge: the screen is already
                # authenticated against GEST, and this localhost bridge holds no credential of
                # its own (see the PROVISIONAL note at the top). No profile — the browser did
                # not send one, or GEST is down — falls back to the historical constants, which
                # is precisely the `media` band. Calibration degrades; it never stops.
                thresholds, band = thresholds_for(msg.get("profile"), session["watching"])
                session["band"] = band
                worker.set_watched(session["watching"], thresholds)
                print(f"[watch] {peer} -> {session['watching']} "
                      f"(draft={session['commandDraftRef']} sensibilidade={band} "
                      f"earClose={thresholds.ear_close})")
            elif msg.get("type") == "stop_session":
                session["watching"] = None
                session["band"] = DEFAULT_BAND
                worker.set_watched(None)

    except websockets.ConnectionClosed:
        pass
    finally:
        drain_task.cancel()
        worker.stop()
        print(f"[disconnect] {peer}")


async def main_async(camera_index: int, port: int, debug: bool):
    async def handler(websocket):
        await handle_client(websocket, camera_index, debug)

    print("NeuroAssist AI — Edge Agent (live-test WS bridge)")
    print("=" * 50)
    print(f"Listening on ws://localhost:{port}/live-test (localhost only)")
    print("PROVISIONAL auth — see GEST-INT-004 / INT-GEST-007 (no auth enforced yet)")
    async with websockets.serve(handler, "localhost", port):
        await asyncio.Future()  # run forever


def main():
    parser = argparse.ArgumentParser(description="NeuroAssist AI — Edge Agent live-test WS bridge")
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--debug", action="store_true")
    args = parser.parse_args()

    try:
        asyncio.run(main_async(args.camera, args.port, args.debug))
    except KeyboardInterrupt:
        print("\nEncerrando...")
        sys.exit(0)


if __name__ == "__main__":
    main()
