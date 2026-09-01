# TREI — Event Catalog (`trei-service`)

> `messaging.enabled = false` — **no broker**. Entries below are **in-process domain events** and
> **synchronous HIST audit facts** (SEC-002). See [`../contracts/event-contracts.md`](../contracts/event-contracts.md).

| Name | Type | Trigger | Payload (ref) | Known consumers |
|---|---|---|---|---|
| `SpeechNodeProposed` ("Fala proposta") | domain (in-process) | LLM proposal materialized to `rascunho` | `sessaoId`, `propostaId`, `noId?`, `locale` | TREI gate eval; HIST audit (`proposal.created`) |
| `NodeApprovedByCaregiver` | domain (in-process) | caregiver approval (api §2.4) | `noId`, `locale`, `cuidadoraId` | TREI gate eval; HIST audit (`approval.caregiver`) |
| `NodeApprovedByPatient` | domain (in-process) | patient approval (api §2.5) | `noId`, `locale`, `pacienteSessaoSub` | TREI gate eval; HIST audit (`approval.patient`) |
| `DualAuthorizationGateCleared` | domain (in-process) | both flags `true` for `(no_id, locale)` | `noId`, `locale` | TREI publication pipeline (enqueue outbox) |
| `SpeechNodePublished` ("Fala publicada") | domain (in-process) | `embed`+`tts`+`index` all `ok`; locale → `ativo` | `noId`, `locale`, `embeddingModelId`, `audioPresent` | flips `conv_no_conteudo.status='ativo'`; HIST audit (`node.published`); **CONV reads state, not an event** |
| `NodeDiscarded` | domain (in-process) | draft discarded (api §2.7) | `noId`, `locale`, `by` | HIST audit (`node.discarded`) |

**Anchor events:** `SpeechNodePublished` (primary), `SpeechNodeProposed` (secondary) — doc 01 §Anchor.

**Notes**
- No event carries clinical/diagnostic text; prompts/responses and transcripts are referenced, not inlined
  (SEC-002).
- `SpeechNodePublished` is the moment a locale becomes retrievable by CONV — CONV observes it as
  `conv_no_conteudo.status='ativo'` + the ES document, **not** as a wire event (no broker).
- The publication pipeline steps (`embed`/`tts`/`index`) are outbox rows, not events; see
  [`../contracts/event-contracts.md`](../contracts/event-contracts.md) §3.
