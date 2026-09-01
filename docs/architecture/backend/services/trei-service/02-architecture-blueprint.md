<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary

`trei-service` is a **new, independent Spring Boot microservice** — the authoring plane for the speech
tree. It is **inside** its boundary: the LLM authoring session orchestration, the draft/approval state
machine, the dual-authorization gate, and the publication pipeline (embedding + TTS synthesis + index
write). It is **outside** its boundary: the CONV runtime (retrieval + playback), the patient/caregiver
identity stores (ACS), the immutable audit store (HIST), and the voice clone creation itself.

TREI is the **sole writer** of two shared artifacts that CONV reads: the `arvore-fala` Elasticsearch
document (text + embedding + `audio_ref`) and the cached audio file on the shared filesystem. No other
service writes them; CONV never writes them. This makes TREI the owner of the CONV↔TREI consistency
contract (the open item CONV deferred).

## External Actors and Systems

| Actor / System | Direction | Nature | Coupling |
|---|---|---|---|
| Cuidadora (via DASH shell, ACS JWT) | inbound | starts session, reviews, caregiver-approves | REST, fail-closed auth |
| Paciente com sessão (patient-shell, ADR-012 JWT) | inbound | patient-approves / adjusts proposals | REST, fail-closed auth |
| Claude / Anthropic API (LLM) | outbound | proposes categories/phrases (authoring only) | HTTPS, occasional, TECH-007 |
| Self-hosted embedding model (sidecar) | outbound | computes index-time document vector | in-cluster, G5 model-id coherent |
| ElevenLabs API (TTS) | outbound | synthesizes cloned voice once per node/locale | HTTPS, cached, TECH-008 |
| Audio filesystem store | outbound (write) | stores `<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` | local FS, ADR-017 |
| Elasticsearch 9.4.1 (`arvore-fala`) | outbound (write) | index text + embedding + audio_ref | `elastic-stack-client-lib` IndexPort |
| ACS (`acs-service`) | outbound | validate caregiver + patient JWT scopes | REST via GWY/REG, hard dep |
| HIST (`hist-service`) | outbound | record authoring/approval/publication facts | REST, synchronous, SEC-002 |
| CONV (`conv-service`) | (indirect) | reads what TREI writes | no direct call; shared index/FS |

## End-to-End Flows

**Golden path — propose → dual-approve → publish (per locale):**

1. Caregiver opens a training session in DASH for a patient + authoring locale (ACS JWT, scope
   `treinamento.sessao.conduzir`).
2. TREI orchestrates a conversation with Claude/Anthropic; the LLM returns one or more proposed
   `NoArvoreFala` drafts (category/phrase, text in the authoring locale). TREI persists each as
   `rascunho` with `confirmadoCuidadora=false, confirmadoPaciente=false`. Event: **SpeechNodeProposed**.
   Audit fact → HIST.
3. Caregiver reviews and approves → `confirmadoCuidadora=true` (`treinamento.no.aprovar.cuidadora`).
   Audit → HIST. Gate not yet satisfied.
4. Patient, later and in his own patient-shell, sees pending proposals
   (`confirmadoCuidadora=true, confirmadoPaciente=false`), confirms or adjusts →
   `confirmadoPaciente=true` (`treinamento.no.aprovar.paciente`). Audit → HIST.
5. Both flags `true` ⇒ the **publication pipeline** fires for that locale: (a) embed text with the
   self-hosted multilingual model; (b) synthesize audio once via ElevenLabs, write cached file; (c)
   index `texto + embedding + audio_ref` into `arvore-fala`; (d) mark node `ativo`. Event:
   **SpeechNodePublished**. Audit → HIST. CONV can now retrieve it.

**Alternative / degraded paths:**

- **Only one approval:** node stays `rascunho` indefinitely (no expiry, ORG-001); caregiver may discard.
- **ElevenLabs or embedding sidecar unavailable at publish time:** the external step is retried via a
  **local outbox** (no broker); the node holds in a `publicando` (publishing) sub-state until the
  pipeline completes. It is not marked `ativo` until index + audio both succeed, so CONV never sees a
  half-published node (it would otherwise fall to Web-Speech on missing audio, OPS-004 — avoided here by
  gating `ativo` on completion).
- **LLM unavailable:** the authoring session cannot propose new content, but existing drafts/approvals
  are unaffected; humans can still approve/publish already-proposed nodes.
- **Second locale:** authoring the same concept in EN vs PT is an independent proposal with its own
  approvals, embedding and audio (ADR-018); tree structure (the category/leaf position) is shared.

## Internal Boundaries

Hexagonal layering (shared `ADR-001-hexagonal-architecture`), package root `com.neuroassist.trei`:

- **api/** — REST controllers for the DASH authoring surface and the patient-shell approval surface;
  DTOs; mappers. No business rule.
- **application/** — use-case services: `ConduzirSessaoTreinamento`, `RegistrarPropostaLlm`,
  `AprovarNoCuidadora`, `AprovarNoPaciente`, `PublicarNo` (the pipeline orchestrator). `@Transactional`
  boundaries live here; the dual-authorization gate is enforced here, not in the controller.
- **domain/** — `NoArvoreFala` aggregate (draft state, per-locale content, two approval flags,
  invariants), value objects (`Locale`, `EstadoNo`, `AudioRef`, `EmbeddingRef`), domain events
  (SpeechNodeProposed, SpeechNodePublished), and **ports** (interfaces): `LlmAuthoringPort`,
  `EmbeddingPort`, `TtsSynthesisPort`, `AudioStorePort`, `IndexPort`, `AcsSessionPort`, `HistAuditPort`.
- **infrastructure/** — adapters: Claude/Anthropic client, ElevenLabs client, embedding sidecar client,
  filesystem audio adapter, `elastic-stack-client-lib` IndexPort adapter, ACS resource-server config +
  Feign, HIST Feign, JPA persistence, local outbox for retryable external steps, config.

Dependency rule: `domain` imports nothing outward; `application` depends on `domain`; `api` and
`infrastructure` depend inward.

## Cross-cutting Concerns

- **Security / RBAC.** Two distinct identities in one workflow (ADR-016): caregiver via ACS
  (`treinamento.sessao.conduzir`, `treinamento.no.aprovar.cuidadora`) and patient via
  patient-with-session (`treinamento.no.aprovar.paciente`, ADR-012). TREI is an OAuth2 resource server on
  the shared ACS realm, fail-closed; it honors the ACS blacklist for immediate patient-session
  revocation. Dual approval is a domain invariant, not merely two endpoints.
- **Multi-tenancy.** Schema-per-tenant (`empresa`), shared `ADR-003-multi-tenancy`; all drafts, approvals
  and published nodes are scoped by `empresa` + `paciente_id`. The `arvore-fala` index and audio paths
  are partitioned by `empresa`/`paciente_id`.
- **Data sensitivity / LGPD (SEC-002).** Authored text, cloned voice and generated audio are health-grade
  sensitive. Never send clinical/diagnostic text to Claude or ElevenLabs (PHI caveat, ADR-017 §research);
  disclose AI interaction to the user; prefer disabling third-party model-training on the voice data.
- **Observability.** Every authoring session and pipeline run is traced; audit-worthy facts go to HIST
  synchronously. LLM/ElevenLabs calls are counted (cost is per-synthesis, FIN-002) and must be
  attributable to TREI only (never CONV).
- **Resilience.** External generation steps (LLM, TTS, embedding) are retryable via a local outbox; the
  `ativo` transition is gated on full pipeline completion so downstream CONV never observes a partial
  node. No message broker (`messaging.enabled = false`).
