<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent

TREI (`trei-service`) is the **authoring plane** of the NeuroAssist AI speech tree. It is the single,
supervised place where the content CONV serves at runtime is *created and grown* — an LLM
(Claude/Anthropic) converses with the caregiver and the patient to propose new categories/phrases, and
only content **approved by both humans** is embedded, voiced in the patient's cloned voice, and indexed
for CONV to retrieve. TREI exists so that the patient's daily communication runtime (CONV) can stay
retrieval-only and never hallucinate: all generation is quarantined here, off the critical path.

## Problem Statement

CONV can only speak phrases that already exist in the `arvore-fala` tree; it never generates text
(TECH-007, ADR-015). Someone therefore has to author and expand that tree over the course of a
progressive disease (ALS/MND), without (a) putting an LLM on the patient's live communication path and
(b) losing the patient's own voice/intent in what gets added. A caregiver-only authoring flow would
substitute the caregiver's interpretation for the patient's voice; a patient-only flow would break as
residual capacity declines. The problem is to grow the tree **collaboratively and safely**: LLM
proposes, two humans approve independently and asynchronously, and only then does the phrase become
retrievable and speakable in the patient's cached cloned voice.

## Personas

- **Cuidadora Familiar — Jamilly Amaral (primary).** Starts and drives the LLM training session in the
  admin/DASH shell, reviews proposals, and grants the caregiver approval
  (`treinamento.no.aprovar.cuidadora`) under her ACS identity.
- **Paciente com sessão — Márcio Amaral (co-primary).** Reviews the same pending proposals in his own
  restricted patient-shell, at his own pace, and grants the patient approval
  (`treinamento.no.aprovar.paciente`) under the patient-with-session identity owned by ACS (ADR-012).
  Both approvals are mandatory; neither persona alone can publish a node.
- **Coordenador de clínica (secondary, institutional tenant).** In a clinic tenant, the caregiver role
  may be a coordinator; the authoring flow is identical — the caregiver approval is the same RBAC action
  under a different ACS role.

## MVP Scope

1. LLM-assisted authoring session (caregiver-initiated) that proposes `NoArvoreFala` drafts scoped to one
   patient + tenant, in the session's authoring locale — LLM proposes only, never publishes.
2. Draft lifecycle: proposal persisted as `rascunho` with two independent approval gates
   (`confirmadoCuidadora`, `confirmadoPaciente`), no automatic expiry, manually discardable.
3. Dual-authorization gate: publication fires only when both gates are `true`; approvals are asynchronous
   and issued from different shells under different identities.
4. Publication pipeline owned by TREI: embedding (self-hosted multilingual model) + ElevenLabs voice
   synthesis (once, cached) + Elasticsearch index of `texto + embedding + audio_ref`, then node `ativo`.
5. Bilingual EN/PT authoring where each locale is proposed, approved and published independently; tree
   structure invariant across locales (ADR-018).
6. Consumption of the caregiver identity (ACS/DASH) and patient identity (patient-with-session, ADR-012)
   by reference.

## Out of Scope

- CONV runtime (navigation, semantic retrieval, cached-audio playback) — TREI only feeds it.
- Ownership/provisioning/revocation of the patient-with-session actor (lives in ACS, ADR-012).
- Creating the voice clone itself (pre-existing product asset; TREI only calls synthesis).
- GEST / EDGE / EVNT / HIST storage / DASH rendering / Angular i18n mechanism choice.
- Any async broker or object storage for audio (MVP: local filesystem, same store CONV reads, ADR-017).
- Unsupervised translation or any auto-publish of LLM output (SEC-002, TECH-007).

## Architectural Principles

- **Generation is quarantined.** The LLM lives only in TREI's authoring phase; CONV runtime stays
  retrieval-only. TREI never serves the patient's daily communication.
- **Human dual authorization is a hard gate.** No node is embedded, voiced or indexed until both the
  caregiver and the patient approve. The gate is the domain invariant, not a UI convenience.
- **TREI writes, CONV reads.** TREI is the sole writer of the `arvore-fala` index document and the cached
  audio file; CONV is a read-only consumer. The consistency contract lives here.
- **Model-id coherence with CONV (G5).** TREI's index-time embedding model MUST match CONV's query-time
  model, or CONV recall degrades silently.
- **Locale independence.** Each language is authored and approved on its own; nothing is auto-translated
  into production.
- **Health-grade sensitivity.** Authored text, cloned voice and generated audio are sensitive data
  (SEC-002, LGPD); never send clinical/diagnostic text to third-party APIs (ElevenLabs PHI caveat).
- **Hexagonal, independent microservice.** Same posture as the rest of the monorepo (shared
  `ADR-001-hexagonal-architecture`, `ADR-002-independent-microservices`).

## Success Signals

- **Zero LLM on runtime:** no Claude/Anthropic or ElevenLabs call is ever observed on the CONV runtime
  path — all generation traces originate in TREI.
- **Gate integrity:** no node reaches `ativo` / the index without both approval flags `true`.
- **Model-id coherence:** the index-time embedding `model_id` recorded by TREI equals CONV's query-time
  `model_id` for 100% of published nodes.
- **Locale integrity:** a node published in one locale is never silently exposed in the other; each
  locale carries its own approvals, embedding and audio.
- **Patient voice preserved:** the share of published nodes carrying real patient approval (not
  caregiver-only) stays high enough that the collaborative design is genuinely used (ADR-016 signal).
- **Voice-never-silent downstream:** every published node has a cached audio file present at the
  CONV-agreed path, so CONV's Web-Speech fallback (OPS-004) is the exception, not the rule.
