# TREI — Integration Contracts (external / infrastructure)

> Module **TREI** · External & infrastructure integrations. Internal platform services (ACS, PAC, HIST,
> GWY/REG) are in [`hub-integration.md`](hub-integration.md). TREI is the **only** place in the system
> where a third-party generative API is called — and it is **off** CONV's runtime path (ADR-016/ADR-002 local).
> EARS acceptance criteria per [`../../../standards/ears-notation.md`](../../../standards/ears-notation.md) (D-057).

---

## 1. Claude / Anthropic API (LLM) — authoring only

- **Nature:** assisted content generation during a training session (TECH-007). The **only** generative
  call in the whole system. Occasional (training sessions), never continuous, never on a read path.
- **Access:** through `LlmAuthoringPort` (domain port); the Anthropic client lives in `infrastructure`.
  Request-scoped to the active `trei_sessao_treinamento`.
- **Model:** the concrete Anthropic model id/version is configuration (`ANTHROPIC_MODEL_ID`) and is
  recorded on the session (`trei_sessao_treinamento.llm_model_id`) for traceability (audit, SEC-002).
- **Prompt contract:** system prompt constrains the LLM to **propose** everyday-need category/phrase text
  in the session locale; it never receives clinical/diagnostic input and never returns a publish action.
- **Disclosure:** the user MUST be told they are interacting with AI (ElevenLabs Use Policy, ADR-017);
  the API returns `aiDisclosed: true` so the FE surfaces it.
- **Failure posture:** **hard for authoring** — if unreachable, proposal turns fail `503`
  (`type: …/llm-unavailable`); no partial proposal is persisted. Does **not** affect published content or
  CONV.
- **PHI caveat (SEC-002):** never send clinical text as prompt input; the register stays everyday-need.

**EARS**
- WHEN a proposal turn runs, THE SYSTEM SHALL call `LlmAuthoringPort` scoped to the active session, persist
  every candidate before returning it, and never trigger publication from LLM output (TECH-007, ADR-003).
- THE SYSTEM SHALL record the concrete `ANTHROPIC_MODEL_ID` on the session so every proposal is traceable
  to a model version.
- IF the Anthropic API is unreachable, THEN THE SYSTEM SHALL fail the proposal with `503` and leave no
  partial `trei_proposta_llm` row.

---

## 2. Self-hosted multilingual embedding sidecar — index-time vector (G5)

- **Nature:** a self-hosted sentence-transformers model (ADR-015) that computes the **document** vector at
  index time. TREI is the only writer of embeddings for `arvore-fala`.
- **Access:** through `EmbeddingPort`; the sidecar HTTP client lives in `infrastructure`
  (`TREI_EMBED_SIDECAR_URL`).
- **G5 — model-id parity (hard operational invariant):** the model id (`TREI_EMBED_MODEL_ID`) **MUST equal**
  CONV's query-time `CONV_EMBED_MODEL_ID` and the ES inference `model_id` used by CONV's
  `query_vector_builder` ([CONV integration-contracts §1.1/§4](../../conv-service/contracts/integration-contracts.md);
  [`data-model.md`](data-model.md) §3). The vector is **768-d, L2-normalized** to match the shared mapping
  (`dense_vector, dims:768, similarity:cosine`, `dynamic:strict`).
- **Failure posture:** the `embed` outbox step retries with backoff; the locale stays `publicando` (never
  `ativo`) until the vector is produced and its model id recorded.

**EARS**
- WHEN the `embed` step runs, THE SYSTEM SHALL request a 768-d normalized vector from the sidecar and SHALL
  record `embedding_model_id = TREI_EMBED_MODEL_ID` on `trei_conteudo_autoria` (G5).
- IF the sidecar reports a runtime model id different from `TREI_EMBED_MODEL_ID`, THEN THE SYSTEM SHALL mark
  the step `falha` (fail-closed) and SHALL NOT index — preventing a silent CONV recall regression (risk R5).
- IF `TREI_EMBED_MODEL_ID` changes, THEN THE SYSTEM SHALL treat it as a full-corpus re-embed + coordinated
  CONV cutover, never a per-node mixed-model state ([`data-model.md`](data-model.md) §3.4).

---

## 3. ElevenLabs API (TTS) — cached voice synthesis

- **Nature:** synthesizes the patient's **cloned voice** once per approved node per locale (TECH-008);
  output cached as MP3/Opus 64–96 kbps on the local filesystem (ADR-017). Never called on CONV runtime.
- **Access:** through `TtsSynthesisPort` (ElevenLabs client in `infrastructure`); the patient's voice id is
  configuration/product data (`ELEVENLABS_VOICE_ID` per patient), not created by TREI (out of scope, doc 01).
- **PHI caveat (ToS §4h, ADR-017):** never send clinical/diagnostic text as synthesis input; prefer
  disabling third-party model-training on the voice data.
- **Failure posture:** the `tts` outbox step retries; a locale is not `ativo` until the file exists.

**EARS**
- WHEN the `tts` step runs, THE SYSTEM SHALL synthesize **once** per `(no_id, locale)` in the patient's
  cloned voice and cache the file; a retry over an existing file SHALL be an idempotent no-op (FIN-002).
- THE SYSTEM SHALL write only everyday-need text to ElevenLabs and SHALL NOT transmit clinical text (SEC-002).

---

## 4. Elasticsearch 9.4.1 — index side (via `elastic-stack-client-lib`)

- **Index:** `neuroassist-ai_arvore-fala` (slug `arvore-fala`, class **dimension**). Declared in
  `project-context.json` (`stack.elastic.indices[]`). Shape:
  [`../../conv-service/design/index-design-arvore-fala.md`](../../conv-service/design/index-design-arvore-fala.md).
- **Access:** **only** through the shared hexagonal port **`IndexPort`** from
  `integrations/elastic-stack/ports/IndexPort.java` (adapter `ElasticsearchAdapter` is the sole holder of
  `co.elastic.clients`). TREI imports the lib; it **never** instantiates its own ES client. TREI uses
  **write** operations (`IndexPort`); reads (`SearchPort`) are CONV's.
- **Document id:** deterministic `_id = "<no_id>_<locale>"` → idempotent upsert/replay (index-design §5).
- **Mandatory traceability:** every write MUST carry the **8-field traceability block** (D-269); the shared
  adapter rejects (DLQ) a write missing any — TREI populates them per the index-design §7 guidance
  (`source_file_id`=session id, `register_code`=`tipo`, `block`="CONV", `parse_status`="ok",
  `synthetic_dataset`=false, …).
- **Failure posture:** the `index` outbox step retries; the ES projection MAY lag PostgreSQL (authoritative),
  and CONV treats a miss as "not found" and degrades (OPS-003) — TREI never blocks on ES for authoring.

**EARS**
- WHEN the `index` step runs, THE SYSTEM SHALL upsert one document per `(no_id, locale)` with `_id =
  <no_id>_<locale>`, all 8 traceability fields, `texto`, the 768-d `embedding`, `audio_ref`, and
  `status='ativo'`.
- IF `IndexPort` raises `ElasticException`, THEN THE SYSTEM SHALL keep the outbox step `pendente` and retry;
  the locale SHALL NOT become `ativo` until the index write succeeds.
- THE SYSTEM SHALL keep the ES `embedding` dims (768) and `similarity` (cosine) consistent with the shared
  mapping and SHALL NOT mutate the mapping unilaterally (owned jointly with CONV).

---

## 5. Audio filesystem store — write side (`AudioStorePort`)

- **Nature:** pre-synthesized voice files on the single server's **local filesystem** (ADR-017); the same
  store CONV reads. No object storage in the MVP.
- **Layout & naming (shared with CONV, gap G6):**
  `${TREI_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`, `<ext>` ∈ `opus`|`mp3`. This path
  (relative to root) is the `conv_no_conteudo.audio_ref` value CONV reads. **`TREI_AUDIO_ROOT` MUST resolve
  to the same location as `CONV_AUDIO_ROOT`.**
- **Sensitivity:** cloned-voice audio is sensitive/biometric (SEC-002/LGPD); inherits REG-001/OPS-002
  safeguards. TREI writes; CONV reads; neither deletes except under LGPD erasure (coordinated, doc 05).

**EARS**
- WHEN the `tts` step completes, THE SYSTEM SHALL write the cached file at the shared convention path and
  set `conv_no_conteudo.audio_ref` + `audio_formato`, so CONV's presence-based fallback (OPS-004) resolves.
- THE SYSTEM SHALL write to the same root CONV reads (`TREI_AUDIO_ROOT == CONV_AUDIO_ROOT`).

---

## 6. What TREI does NOT integrate with (boundary guards)

| System | Why not |
|---|---|
| TechLab AI hub | `ai.hasAi = false`; TREI's LLM dependency is the **direct** Anthropic API, not the orchestration hub. No knowledge/document_composition/analytics. |
| Async broker (Kafka/RabbitMQ) | `messaging.enabled = false`; publication is an in-process outbox pipeline. |
| Object storage (S3/etc.) | MVP uses local filesystem (ADR-017). |
| CONV runtime / `SearchPort` | TREI writes via `IndexPort`; it never performs CONV's read queries. |
| Biometric/image/landmark ingress | Rejected at the API boundary with `400` (SEC-002); TREI authors text only. |

## 7. Configuration surface (env)

| Var | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL_ID` | LLM authoring; model id recorded on the session (§1). |
| `TREI_EMBED_SIDECAR_URL` | Self-hosted multilingual embedding sidecar endpoint (§2). |
| `TREI_EMBED_MODEL_ID` | **G5 anchor** — index-time model id; MUST equal CONV's `CONV_EMBED_MODEL_ID` (§2, [`data-model.md`](data-model.md) §3). |
| `ELEVENLABS_API_KEY` / `ELEVENLABS_VOICE_ID` | TTS; per-patient cloned voice id (§3). |
| `TREI_AUDIO_ROOT` | Filesystem root of the cached audio store; MUST equal `CONV_AUDIO_ROOT` (§5). |
| `TREI_ES_INDEX` | ES index name (default `neuroassist-ai_arvore-fala`). |
| `ELASTICSEARCH_*` | Provided by `elastic-stack-client-lib` config. |
