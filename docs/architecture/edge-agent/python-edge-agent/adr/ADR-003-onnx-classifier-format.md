# ADR-003 — ONNX as gesture classifier model format

> **SUPERSEDED (2026-08-22)** por `GEST ADR-007 — O browser é o único motor de detecção`.
> Pendente de equivalente no browser: `onnxruntime-web` ou blendshapes do MediaPipe (follow-up 6 da ADR-007).

## Status
Aceita

## Contexto
The gesture classifier maps a `LandmarkSet` (flattened landmark coordinates) to a
`TipoGesto` primitive label. The model must run on CPU, fit within 5 MB, and be
loadable by the Python runtime without a full training framework installed.
Options: ONNX Runtime, TFLite, PyTorch TorchScript, scikit-learn pickle.

## Decisão
**ONNX format via `onnxruntime` (CPU-only `onnxruntime` package, not `onnxruntime-gpu`).**
ONNX is the interchange format supported by all major training frameworks (PyTorch, TensorFlow,
scikit-learn via `sklearn-onnx`). The model can be trained in any framework and exported once;
inference uses the lightweight `onnxruntime` package (~15 MB installed). This decouples model
training (future ML team concern) from model serving (PEA concern).

Model spec: input = flattened 468×3 landmark array (1404 floats); output = softmax over
TipoGesto primitive codes. Target: < 5 MB, < 10ms inference on host machine CPU.

## Consequências
**Positivas:** framework-agnostic; small inference runtime; well-supported on ARM64 and x86-64;
model can be replaced without changing PEA code (only `model_path` config update).
**Negativas:** no hot-reload of model (requires restart); model quantization may be needed
to hit < 5 MB target for complex architectures.
**Trade-off:** accepted. TFLite would also work but locks the training pipeline to TensorFlow.
