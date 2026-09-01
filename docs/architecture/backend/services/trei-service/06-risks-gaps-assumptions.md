<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos

- **R1 — Model-id drift (G5) breaks CONV silently.** If TREI indexes with an embedding `model_id`
  different from the one CONV uses at query time, CONV's kNN recall degrades with no error. *Mitigation:*
  make `embedding_model_id` explicit in the ES document and a shared configuration owned jointly; add a
  coherence check. High impact.
- **R2 — Partial publication leaks to CONV.** If a node were marked `ativo` before its audio or index
  write completed, CONV would retrieve text with no cached audio and fall to Web-Speech (OPS-004) or,
  worse, retrieve nothing. *Mitigation:* gate `ativo` on full pipeline success; hold in `publicando` with
  outbox retry.
- **R3 — Collaborative flow degenerates to caregiver-only.** If the patient rarely manages to approve,
  the dual-authorization design becomes overhead and the product loses the patient's voice (ADR-016
  signal). *Mitigation:* measure patient-approval share; be ready to fall back to Opção 2 (schema already
  supports it — dropping the patient flag is a scope cut, not a migration).
- **R4 — ElevenLabs ToS / PHI exposure (SEC-002).** Sending clinical/diagnostic text as TTS input, or
  leaving voice data available for model training, risks account suspension and a sensitive-data breach.
  *Mitigation:* boundary guard keeps vocabulary in the everyday-need register; disable third-party
  training; document voice-clone consent (ADR-017 TODO[sponsor]).
- **R5 — LLM proposes unsafe/hallucinated content.** The LLM could propose phrases that misrepresent the
  patient or embed clinical content. *Mitigation:* dual human approval is precisely the guard; LLM output
  is never published unreviewed (TECH-007).
- **R6 — Single-node Elasticsearch + single server.** No HA for the index or audio store (OPS-002).
  *Mitigation:* authoring is off the critical path; PostgreSQL is the source of truth and both derived
  stores are re-buildable.

## Gaps Conhecidos

- **G-A — Aggregate/locale approval granularity.** Are the two approval flags per-node or per-locale?
  ADR-018 independence suggests per-locale; the draft model keeps them per-node with a note. *Resolve in
  specs.*
- **G-B — CONV↔TREI index document contract.** The exact `arvore-fala` field set (field names, dense_vector
  dims, `status`/`tipo` values, `audio_ref` format) must be fixed jointly with CONV's read side (CONV's
  sanctioned search body already names `no_id, texto, tipo, engine_feature_ref, audio_ref`). *Resolve in
  specs, co-owned with CONV.*
- **G-C — `engineFeatureRef` binding for authored phrases.** If an authored phrase is meant to be reached
  by a specific gesture, TREI must resolve/attach the shared vocabulary (PAC/GEST, ADR-013). Contract not
  yet defined. *Resolve in specs.*
- **G-D — LLM contract.** Concrete Claude/Anthropic model id/version, prompt/system-prompt contract,
  token budget, cost controls, and AI-disclosure UX are unspecified at the vision layer. *Resolve in
  specs.*
- **G-E — Outbox/publishing sub-state details.** The `publicando` sub-state, retry/backoff policy and
  idempotency of the embed→tts→index steps are described directionally, not specified. *Resolve in specs.*
- **G-F — LGPD retention windows / erasure cascade.** Exact retention and the patient-erasure cascade
  across PostgreSQL + ES + audio FS need the compliance owner's sign-off.

## Suposicoes a Validar

- **A1** — The patient's ElevenLabs voice clone already exists (product decision, outside Estágio 0);
  TREI only calls synthesis, never creates the clone.
- **A2** — The self-hosted multilingual embedding sidecar is (or will be) provisioned and its `model_id`
  is the shared, coherent identifier for both TREI (index) and CONV (query).
- **A3** — `messaging.enabled = false` holds for the MVP; the local outbox is an acceptable substitute
  for a broker at pilot volume (1 patient, tens of phrases).
- **A4** — HIST accepts synchronous audit writes for authoring/approval/publication facts.
- **A5** — The patient-with-session identity (ADR-012) is fully owned by ACS and exposes the
  `treinamento.no.aprovar.paciente` scope; TREI only validates the token.
- **A6** — Authored vocabulary stays in the everyday-need register (no clinical text), satisfying the
  ElevenLabs PHI constraint (SEC-002).

## Dependencias Criticas

- **ACS (`acs-service`)** — *hard / fail-closed.* Without valid caregiver + patient JWTs there is no
  authoring and no approval.
- **Self-hosted embedding sidecar + `model_id` coherence with CONV (G5)** — *hard for publication.*
- **Elasticsearch `arvore-fala` (write) + audio filesystem store** — *hard for publication*, but derived
  and re-buildable; CONV's read side is the ultimate consumer.
- **Claude/Anthropic API** — *soft.* Its absence blocks new proposals but not approval/publication of
  already-proposed nodes.
- **ElevenLabs API** — *soft at author time* (retried via outbox), but a published node is not `ativo`
  until its audio exists; downstream CONV has its own Web-Speech fallback (OPS-004).
- **MIG (`neuroassist-ai-migrations`)** — owns TREI's Flyway migrations (centralized).
- **HIST (`hist-service`)** — audit trail for health-grade authoring facts (SEC-002).
