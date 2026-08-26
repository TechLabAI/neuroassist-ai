# ADR-002 — MediaPipe FaceMesh for landmark extraction

> **SUPERSEDED (2026-08-22)** por `GEST ADR-007 — O browser é o único motor de detecção`.
> A extração de landmarks passa a ser MediaPipe Tasks Vision no browser (WASM).

## Status
Aceita

## Contexto
The PEA needs to extract facial landmarks from webcam frames to feed the gesture classifier.
Options: OpenCV DNN (Haar cascades / DNN face detection), Dlib (68-point), MediaPipe FaceMesh
(468-point), custom PyTorch model.

Key constraints: CPU-only (no dedicated GPU assumed), < 20ms per frame at 15 fps, Python API,
no PII data retention, no cloud calls.

## Decisão
**MediaPipe FaceMesh (CPU mode, 468 landmarks).** Google's MediaPipe runs entirely on CPU via
XNNPACK delegates, achieves ~10ms per frame on host machine CPU, and provides a stable Python
API (`mediapipe 0.10+`). The 468-landmark model covers the full facial surface needed for
subtle involuntary-but-controlled gestures (blinks, eye twitches) characteristic of ALS/MND.

`mediapipe.solutions.face_mesh.FaceMesh(static_image_mode=False, max_num_faces=1)` is used
in video mode (tracking mode), which is faster than static mode for continuous streams.

## Consequências
**Positivas:** CPU-only, well-maintained, high landmark density for subtle gestures, no cloud
dependency, active Python community.
**Negativas:** MediaPipe package is large (~200 MB installed); version pinning required
(MediaPipe has had breaking API changes between minor versions).
**Trade-off:** accepted. Alternative (Dlib 68-point) is lighter but less accurate for the
target gesture set. Custom model is out of scope for MVP.
