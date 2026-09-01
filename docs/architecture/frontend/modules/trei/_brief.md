# TREI — Structured Brief (source for specs / backlog / scaffold)

> Module: **TREI** — Treinamento Assistido por IA (Autoria da Árvore de Falas) · Service: `trei-service`
> Stack: Java 21 / Spring Boot · Nature: **new module, retrofit into a production system**
> Closed product decisions carried in (BA/project level): ADR-016 (LLM authoring phase), ADR-017 (TTS
> ElevenLabs, cache), ADR-015 (index-time side of the semantic search), ADR-018 (bilingual EN/PT),
> ADR-012 (patient-with-session identity) — all `accepted`.

TREI is the **authoring plane** of the speech tree that CONV serves at runtime. CONV is retrieval-only
(ADR-015): it never generates text and never calls an LLM or ElevenLabs. Everything that *creates* or
*edits* tree content — the LLM conversation, human dual approval, embedding generation and voice
synthesis — lives here, off the patient's daily runtime path. TREI **writes** the artifacts CONV
**reads** (the `arvore-fala` Elasticsearch document + the cached audio file); it is the answer to the
open item CONV left in its ADR README: "Contrato de consistência CONV↔TREI (quem escreve o índice/áudio
e quando)".

## 1. Canonical name and prefix

- Prefix: `TREI` (uppercase)
- Canonical name: `trei` — service `trei-service`, frontend lib `trei`
- Package base: `com.neuroassist.trei`

## 2. Problem the tool solves (1–2 sentences)

CONV can only speak phrases that already exist in the tree; someone has to author and grow that tree
without hallucination risk on the patient's critical communication path. TREI provides a **separate,
supervised authoring phase** where an LLM (Claude/Anthropic) converses with the caregiver and the
patient to propose new categories/phrases per language, and only content **approved by both humans** is
embedded, voiced (patient's cloned voice via ElevenLabs), and indexed for CONV to retrieve.

## 3. Primary and secondary persona

- **Primary — Cuidadora Familiar (Jamilly Amaral):** starts and drives the LLM training session in the
  admin/DASH shell, reviews the LLM's proposals, and issues the caregiver approval
  (`treinamento.no.aprovar.cuidadora`) via her ACS-authenticated session.
- **Co-primary — Paciente com sessão (Márcio Amaral):** confirms or adjusts each pending proposal in his
  own patient-shell at his own pace, issuing the patient approval (`treinamento.no.aprovar.paciente`)
  under the patient-with-session identity owned by ACS (ADR-012). Both approvals are **required** and
  **asynchronous** (never the same screen at the same time) — this is what makes the collaborative flow
  robust against the disease's progression (ADR-016, Opção 1).

## 4. MVP scope (minimum viable)

1. **LLM-assisted authoring session:** caregiver-initiated conversation with Claude/Anthropic that
   proposes new categories/phrases (`NoArvoreFala` drafts) scoped to one patient + tenant (empresa), in
   the session's authoring locale. The LLM only *proposes*; it never publishes.
2. **Draft lifecycle:** a proposed node is persisted in state `rascunho` with two independent boolean
   gates `confirmadoCuidadora` / `confirmadoPaciente` (both default `false`); it lives indefinitely with
   no automatic expiry (ORG-001) and can be discarded manually.
3. **Dual-authorization gate:** publication fires **only** when both `confirmadoCuidadora AND
   confirmadoPaciente` are `true`. The two approvals are asynchronous and issued from different shells
   under different identities (caregiver via ACS/DASH; patient via patient-with-session, ADR-012).
4. **Publication pipeline (TREI owns the write side):** on both-approved, TREI (a) generates the
   embedding with the **self-hosted multilingual model** and (b) synthesizes audio once via ElevenLabs
   in the patient's cloned voice, caches the file, then (c) indexes `texto + embedding + audio_ref` into
   the `arvore-fala` Elasticsearch index and marks the node `ativo`. This is exactly the document/audio
   CONV consumes read-only.
5. **Bilingual independent authoring (EN/PT):** each locale is authored, approved and published
   **independently** — the LLM may suggest a translation but a human confirms each locale; ElevenLabs
   synthesizes once per locale; ES indexes each locale as its own document. Tree structure is invariant
   across locales (ADR-018).
6. **Consume the two human identities by reference:** caregiver via ACS JWT (DASH shell), patient via
   the patient-with-session JWT (ADR-012). TREI owns neither identity.

## 5. Out of MVP scope

- **CONV runtime** (navigation, semantic retrieval, cached-audio playback) — TREI never serves the
  patient's daily communication; it only feeds the index/audio store CONV reads.
- **Ownership of the patient-with-session actor** — schema, provisioning and revocation live in ACS
  (ADR-012). TREI consumes the token.
- **Creating the voice clone itself** — the patient's ElevenLabs voice already exists as a product
  decision, outside Estágio 0. TREI only *calls* synthesis for approved phrases.
- Gesture detection / computer vision (GEST), device pairing/heartbeat (EDGE), event/alarm generation
  (EVNT), immutable audit storage (HIST — TREI sends audit facts by reference), admin panel rendering
  (DASH shell is FE), Angular i18n mechanism choice (ADR-018 §5, a FE concern).
- Any async broker / event bus (`messaging.enabled = false`) and any object storage for audio (MVP uses
  a simple local filesystem, ADR-017 — same store CONV reads).
- Automatic (unsupervised) translation or any auto-publish of LLM output (SEC-002, TECH-007).

## 6. Mandatory external integrations

- **Claude / Anthropic API (LLM)** — the *only* place in the whole system where assisted content
  generation happens (TECH-007). Occasional use (training sessions only, not continuous). Concrete
  model id/version and prompt contract deferred to specs. Must disclose to the user that they are
  interacting with AI (ElevenLabs Use Policy, ADR-017 §research).
- **ElevenLabs API (TTS)** — synthesizes the patient's cloned voice **once per approved node per locale**
  (TECH-008); output cached as MP3/Opus 64–96 kbps on the local filesystem. Never called on CONV
  runtime. PHI caveat (ToS §4h): never send clinical/diagnostic text as input — vocabulary stays in the
  everyday-need register (SEC-002, ADR-017 TODO[sponsor]).
- **Elasticsearch 9.4.1** (index `arvore-fala`, index side) — TREI is the **writer** via
  `IndexPort`/embedding. **Shared operational invariant with CONV (G5):** TREI's index-time embedding
  `model_id` MUST equal the `model_id` CONV's query-time kNN uses, or CONV recall silently degrades
  (ADR-015). Reuse `elastic-stack-client-lib` ports.
- **Self-hosted multilingual embedding model (sidecar)** — sentence-transformers; TREI computes the
  document vector at index time. Same model that CONV references at query time (G5).
- **Audio filesystem store** — writes the file CONV later reads, at the CONV-defined convention
  `${AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` (file presence is CONV's Web-Speech
  fallback signal, OPS-004).
- **ACS (`acs-service`)** — JWT issuer/validator for both the caregiver and the patient-with-session
  actor; consumed by reference (shared realm), not owned.
- **HIST (`hist-service`)** — audit-worthy authoring facts (proposal created, each approval, publication)
  sent synchronously by reference (SEC-002 — authored content has the same health-data sensitivity).

## 7. TechLab AI hub dependencies / cross-module events

- **TechLab AI hub:** none (`ai.hasAi = false`). TREI's LLM dependency is the **direct** Claude/Anthropic
  API, not the TechLab AI orchestration hub. No knowledge/document_composition/analytics capability is
  consumed.
- **Messaging:** disabled (`stack.messaging.enabled = false`). The publication pipeline is an in-process
  state machine triggered when both approval flags flip `true`; the external ElevenLabs/ES steps are
  guarded by a **local outbox/retry** (project-context messaging comment) — no broker. All cross-service
  calls (ACS, HIST) are synchronous REST over Eureka (REG) + Spring Cloud Gateway (GWY).
- **Cross-module coupling (by reference, no events):** ACS (identity, hard/fail-closed), HIST (audit),
  and the CONV↔TREI shared contract (TREI writes the `arvore-fala` document + audio; CONV reads).

## 8. Anchor domain event

**"Fala publicada"** (SpeechNodePublished) — a proposed node clears the dual-authorization gate and the
publication pipeline (embedding + ElevenLabs synthesis + Elasticsearch index) completes for a given
locale, making it retrievable by CONV. Secondary anchor: **"Fala proposta"** (SpeechNodeProposed) — the
LLM proposes a new category/phrase into the draft (`rascunho`) state, awaiting the two human approvals.
