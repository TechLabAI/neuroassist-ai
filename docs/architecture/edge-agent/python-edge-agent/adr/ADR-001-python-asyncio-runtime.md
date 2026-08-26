# ADR-001 — Python + asyncio as PEA runtime

> **SUPERSEDED (2026-08-22)** por `GEST ADR-007 — O browser é o único motor de detecção`.
> O runtime Python sai do caminho do produto.

## Status
Aceita

## Contexto
The PEA runs on the same server/PC as the Java services (MVP: integrated webcam). It needs:
- Real-time frame capture (OpenCV — C bindings, Python wrapper)
- Lightweight ML inference (ONNX Runtime — Python API)
- Async HTTP (to EVNT, ACS, GEST, PAC, EDGE — all localhost, low latency)
- Async SQLite (outbox drain loop runs concurrently with detection)
- Single-process deployment (no orchestration overhead)

Alternatives considered: Node.js/TypeScript, Go, Rust.

## Decisão
**Python 3.11+ with asyncio.** The detection pipeline (OpenCV + MediaPipe + ONNX Runtime) is
mature and well-supported only in Python. The two-thread model (CaptureThread + asyncio loop)
handles the blocking I/O boundary cleanly. All dependencies have async-native wrappers
(`httpx`, `aiosqlite`). The Java team already uses Python for scripts; a Python edge agent
is within the team's stack.

## Consequências
**Positivas:** best-in-class ML/CV library support; asyncio handles concurrent I/O without
threads; small binary footprint (pip + venv); fast iteration.
**Negativas:** GIL limits true parallelism (mitigated by two-thread model + C extensions
releasing GIL during computation); higher memory baseline than Go/Rust (~80 MB resident).
**Trade-off:** accepted — ML library support outweighs performance overhead at MVP scale.
