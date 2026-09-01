# CONV — Event Contracts (`conv-service`)

> Module **CONV** · `messaging.enabled = false` → there is **no async broker/event bus**. The "events"
> below are **audit facts delivered synchronously over REST to HIST** (`hist-service`), not bus
> messages. They are named as domain events because they are the durable record of what the patient did.
> Async messaging is a recorded *future* decision (RabbitMQ), not MVP (doc 04). See SPEC-09.

---

## 1. Transport

- **Channel:** synchronous HTTP `POST` from CONV to HIST's audit-ingest endpoint via `HistAuditPort`
  (routed by Eureka/GWY). No topic, no queue.
- **Delivery posture:** **best-effort, non-blocking.** A HIST outage MUST NOT block the patient's
  ability to communicate. On synchronous failure, the fact is written to `conv_audit_outbox`
  (data-model §2.4) and retried by a background sweep (SPEC-09). Selection/search still returns `200`.
- **Correlation:** every fact carries `correlationId` (from `X-Correlation-Id`) and `pacienteId`; the
  actor is always the **patient session** (never the device token, never the caregiver) — `ADR-003`.
- **Idempotency:** `SpeechNodeSelected` carries the `clientEventId` idempotency key; HIST and the CONV
  outbox both dedupe on it (SPEC-05).

---

## 2. Published facts (CONV → HIST)

### 2.1 `SpeechNodeSelected` (anchor)

Emitted when a patient selects a `frase` node ("Nó da árvore selecionado").

```json
{
  "tipo": "SpeechNodeSelected",
  "occurredAt": "2026-08-31T18:22:05Z",
  "correlationId": "c-8c1f…",
  "clientEventId": "9d1f…-uuid",
  "pacienteId": "p-1024",
  "empresa": "clinica-acme",
  "noId": "n-42",
  "locale": "en",
  "via": "gesto",
  "playbackMode": "audio-cacheado",
  "degraded": false
}
```

**EARS**

- WHEN a `frase` node is successfully selected, THE SYSTEM SHALL publish exactly one `SpeechNodeSelected` fact carrying `noId`, `pacienteId`, `locale`, `via`, and `playbackMode`.
- WHEN cached audio was absent and the frontend fell back to Web Speech, THE SYSTEM SHALL set `playbackMode: web-speech` and `degraded: true` in the fact (OPS-004).
- IF the synchronous send to HIST fails, THEN THE SYSTEM SHALL persist the fact to `conv_audit_outbox` and SHALL still return `200` to the caller.

### 2.2 `PhraseMatched`

Emitted when an assisted search resolves an intent to the closest node ("Frase encontrada por busca").

```json
{
  "tipo": "PhraseMatched",
  "occurredAt": "2026-08-31T18:21:40Z",
  "correlationId": "c-8c1f…",
  "pacienteId": "p-1024",
  "empresa": "clinica-acme",
  "intentHash": "sha256:…",
  "winningNoId": "n-42",
  "score": 0.91,
  "retrieval": "semantic",
  "degraded": false
}
```

> `intentHash` (not raw intent) is stored to avoid persisting free text unnecessarily (LGPD, SEC-002);
> the raw intent is never logged. May be sampled at higher volume.

**EARS**

- WHEN an assisted search returns at least one match, THE SYSTEM SHALL publish a `PhraseMatched` fact with `winningNoId`, `score`, and `retrieval`.
- WHILE serving in degraded (static) mode, THE SYSTEM SHALL set `retrieval: static` and `degraded: true` on the fact and MAY omit `winningNoId`/`score`.

### 2.3 `TreeNavigated` (lightweight, may be sampled)

Emitted when a category is opened / a navigation step is taken. Lower value; MAY be sampled or batched
into the outbox to reduce chatter.

```json
{ "tipo": "TreeNavigated", "occurredAt": "…", "correlationId": "…",
  "pacienteId": "p-1024", "fromNoId": "n-root", "toNoId": "n-7", "locale": "en" }
```

### 2.4 `ConvServedDegraded` (operational signal)

Emitted when CONV serves any request in a degraded mode (ES down → static, or audio absent). Useful for
alerting the caregiver/ops about "why the voice changed" (risk R4).

```json
{ "tipo": "ConvServedDegraded", "occurredAt": "…", "correlationId": "…",
  "pacienteId": "p-1024", "capability": "semantic-search", "reason": "elasticsearch-unreachable" }
```

`capability` ∈ `semantic-search` | `cached-audio`. `reason` is a short machine token.

---

## 3. Consumed events

**None.** CONV consumes no bus events (`messaging.enabled = false`) and no events from TechLab AI
(`ai.hasAi = false`). All inbound dependencies (ACS token/blacklist, PAC/GEST vocabulary, audio store,
ES index) are **pull** integrations (synchronous reads or filesystem/index lookups), documented in
[`integration-contracts.md`](integration-contracts.md) and [`hub-integration.md`](hub-integration.md).

> The TREI content-lifecycle events (node created/approved, audio synthesized, embedding indexed) are
> **TREI's** — CONV does not consume them; it observes their *effect* (an `ativo` row / an indexed doc /
> a present audio file) lazily on read. Named here only to mark the boundary (doc 03).

---

## 4. Payload versioning

- Each fact carries a `tipo` discriminator; additive fields are backward-compatible.
- A breaking change to a fact shape introduces a new `tipo` suffix (e.g. `SpeechNodeSelected.v2`) and
  keeps the prior one until HIST confirms migration — mirrors the API `/v2` policy (api-contract §1).
- No schema registry is needed at MVP scale (single writer, synchronous transport).
