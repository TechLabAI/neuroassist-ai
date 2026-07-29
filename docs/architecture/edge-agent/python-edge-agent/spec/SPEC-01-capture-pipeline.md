# SPEC-01 — Capture and Landmark Extraction Pipeline

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Frame capture (CaptureThread)

- THE SYSTEM SHALL capture frames from the webcam at `capture.fps` (default 15) using
  `cv2.VideoCapture(capture.device_index)`.
- THE SYSTEM SHALL set frame resolution to `capture.resolution` (default 640×480) via
  `cap.set(cv2.CAP_PROP_FRAME_WIDTH/HEIGHT)`.
- WHEN the frame queue (`asyncio.Queue(maxsize=5)`) is full, THE SYSTEM SHALL drop the
  incoming frame and SHALL NOT block the capture loop.
- WHEN `VideoCapture.read()` returns `False` (camera disconnected or failure),
  THE SYSTEM SHALL log CRITICAL and exit with code 1.
- THE SYSTEM SHALL NOT write any frame to disk, to a network socket, or to any log
  under any circumstances.

## 2. Landmark extraction (LandmarkExtractor)

- THE SYSTEM SHALL consume frames from the queue and pass each through
  `mediapipe.solutions.face_mesh.FaceMesh` in video mode (`static_image_mode=False`).
- WHEN MediaPipe detects a face in the frame, THE SYSTEM SHALL produce a `LandmarkSet`
  containing the 468 normalized landmark coordinates.
- WHEN no face is detected in the frame, THE SYSTEM SHALL produce no output and
  SHALL discard the frame silently (log at DEBUG level only).
- THE SYSTEM SHALL NOT log landmark coordinate values at any log level.
- THE SYSTEM SHALL process one frame at a time in the asyncio event loop; landmark
  extraction runs in the thread pool executor (non-blocking for asyncio).

## 3. LGPD invariants (enforced here and in SPEC-07)

- THE SYSTEM SHALL NOT store any `LandmarkSet` or partial landmark data in SQLite,
  in log files, or in any output channel other than in-memory pipeline state.
- THE SYSTEM SHALL NOT include any landmark coordinates in any event published to EVNT.
- WHEN a test or debug flag requests frame capture to disk, THE SYSTEM SHALL refuse
  and log a CRITICAL privacy-violation error.

## 4. Performance targets

- THE SYSTEM SHALL sustain 15 fps capture rate with < 1% frame drop under normal conditions
  on the host machine (the server computer where Java services run; MVP = integrated webcam).
- THE SYSTEM SHALL complete MediaPipe landmark extraction in < 20ms per frame on the host machine.
