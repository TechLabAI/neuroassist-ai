# TREI — Event Contracts (`trei-service`)

> Module **TREI** · `messaging.enabled = false` — **there is no broker**. "Events" here are **in-process
> domain events** and **synchronous audit facts** sent to HIST by reference (SEC-002). The publication
> pipeline is a broker-less, outbox-driven in-process state machine (ADR-004).
> EARS acceptance criteria per [`../../../standards/ears-notation.md`](../../../standards/ears-notation.md) (D-057).

---

## 1. Messaging posture

- **No async broker** (`stack.messaging.enabled = false`). TREI publishes nothing to Kafka/RabbitMQ and
  consumes nothing from a broker.
- **Domain events** are raised inside the `application`/`domain` layers and handled synchronously (they
  drive the publication pipeline and the HIST audit calls). They are **not** wire events.
- **Cross-service facts** (audit) go to **HIST** over synchronous REST via Eureka+Gateway, guarded by a
  local outbox for retry (the same posture CONV uses).

---

## 2. Domain events (in-process)

| Event | Raised when | Primary handler(s) | Wire? |
|---|---|---|---|
| `SpeechNodeProposed` ("Fala proposta") | LLM proposal materialized into a `rascunho` node (api §2.3) | persist `trei_proposta_llm`→`materializada`; HIST audit | no (audit only) |
| `NodeApprovedByCaregiver` | caregiver approval set (api §2.4) | re-evaluate gate; HIST audit | no |
| `NodeApprovedByPatient` | patient approval set (api §2.5) | re-evaluate gate; HIST audit | no |
| `DualAuthorizationGateCleared` | both flags `true` for `(no_id, locale)` | enqueue outbox `embed/tts/index`; start pipeline | no |
| `SpeechNodePublished` ("Fala publicada") | `embed`+`tts`+`index` all `ok`; locale → `ativo` | flip `conv_no_conteudo.status='ativo'`; HIST audit | no (CONV reads state, not an event) |
| `NodeDiscarded` | draft discarded (api §2.7) | `sub_estado='descartado'`; HIST audit | no |

**Anchor events:** `SpeechNodePublished` (primary) and `SpeechNodeProposed` (secondary) — doc 01 §Anchor.

**EARS**
- WHEN `DualAuthorizationGateCleared` fires for `(no_id, locale)`, THE SYSTEM SHALL create exactly one set
  of outbox steps (`embed`,`tts`,`index`) and SHALL start the pipeline once, even under duplicate/replayed
  approval calls (idempotent, SPEC-05).
- THE SYSTEM SHALL raise `SpeechNodePublished` **only** after all three outbox steps are `ok`, so no
  observer (CONV, HIST, DASH) ever sees a partially-published locale (ADR-004).
- THE SYSTEM SHALL NOT emit any domain event that causes an LLM, ElevenLabs, embedding, or ES call while
  either approval flag is `false` (gate is the invariant, ADR-003).

---

## 3. Publication pipeline (outbox, broker-less) — ADR-004

The write-side of the CONV↔TREI contract. Triggered synchronously by `DualAuthorizationGateCleared`;
retried by a scheduled outbox drainer.

| Step (`passo`) | Port | Success writes | On failure |
|---|---|---|---|
| `embed` | `EmbeddingPort` | vector (768-d, cosine-normalized) → ES doc `embedding`; `trei_conteudo_autoria.embedding_model_id = TREI_EMBED_MODEL_ID` (**G5**) | outbox `pendente`+backoff; never `ativo` |
| `tts` | `TtsSynthesisPort` + `AudioStorePort` | ElevenLabs MP3/Opus (once) → `${TREI_AUDIO_ROOT}/<empresa>/<paciente>/<locale>/<no_id>.<ext>`; `audio_ref`,`audio_formato` set | outbox `pendente`+backoff |
| `index` | `IndexPort` | upsert ES doc `_id = <no_id>_<locale>` with the mandatory 8-field traceability block + `texto`,`embedding`,`audio_ref`,`status='ativo'` | outbox `pendente`+backoff |

On all three `ok`: `trei_conteudo_autoria.sub_estado='ativo'`, `publicado_em=now()`,
`conv_no_conteudo.status='ativo'`.

**EARS**
- WHEN the `embed` step runs, THE SYSTEM SHALL record `embedding_model_id = TREI_EMBED_MODEL_ID` on the
  authoring row and SHALL refuse the step (outbox `falha`) if the sidecar's runtime model id differs
  ([`data-model.md`](data-model.md) §3, G5 fail-closed).
- WHEN the `tts` step runs, THE SYSTEM SHALL synthesize the audio **once** per `(no_id, locale)` and cache
  it; a retried `tts` for an already-present file SHALL be an idempotent no-op (TECH-008, cost, FIN-002).
- WHEN the `index` step runs, THE SYSTEM SHALL populate **all 8** traceability fields
  ([index-design §6/§7](../../conv-service/design/index-design-arvore-fala.md)); an incomplete write is
  rejected by the shared `IndexPort` adapter (DLQ) and SHALL leave the locale non-`ativo`.
- IF a step exhausts its retry budget, THEN THE SYSTEM SHALL mark it `falha`, keep the locale `publicando`,
  and surface it via `GET /nos/{id}/publicacao` and health — never silently `ativo`.

---

## 4. Audit facts to HIST (synchronous, by reference)

TREI sends audit-worthy authoring facts to **HIST** synchronously (SEC-002); a HIST outage is buffered by
the same local-outbox posture and never blocks authoring.

| Fact | Payload (by reference — no clinical text) |
|---|---|
| `proposal.created` | `sessaoId`, `propostaId`, `noId?`, `locale`, `cuidadoraId`, `correlationId` |
| `approval.caregiver` | `noId`, `locale`, `cuidadoraId`, `at`, `correlationId` |
| `approval.patient` | `noId`, `locale`, `pacienteSessaoSub`, `at`, `correlationId` |
| `node.published` | `noId`, `locale`, `embeddingModelId`, `audioPresent`, `at`, `correlationId` |
| `node.discarded` | `noId`, `locale`, `by`, `at`, `correlationId` |

**EARS**
- THE SYSTEM SHALL audit each approval and each publication to HIST with actor id, `noId`, `locale`, and
  `correlationId`, carrying **no** clinical/diagnostic text (SEC-002).
- IF HIST is unreachable, THEN THE SYSTEM SHALL buffer the audit fact in the local outbox and retry, and
  SHALL NOT fail the authoring/approval request on the audit send.

---

## 5. Versioning

- Domain-event and audit-fact payloads are versioned additively; a breaking change to the HIST fact shape
  is coordinated with HIST (no broker schema registry exists — `messaging.enabled = false`).
- The ES document shape is the **shared CONV↔TREI contract** (index-design); its evolution is coordinated,
  never unilateral (mapping is `dynamic: strict`).
