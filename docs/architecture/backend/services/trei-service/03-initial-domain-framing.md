<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language

- **Treinamento (Training session)** — a caregiver-initiated, supervised authoring session in which the
  LLM proposes new tree content. Separate from CONV's daily use.
- **Nó da árvore de fala (`NoArvoreFala`)** — a node of the speech tree: a **category** (branch) or a
  **frase** (leaf). Shared concept with CONV; TREI authors it, CONV serves it.
- **Proposta (Proposal)** — a `NoArvoreFala` in `rascunho` state generated with LLM assistance, awaiting
  human approval.
- **Aprovação da cuidadora / Aprovação do paciente** — the two independent, mandatory RBAC approvals
  (`confirmadoCuidadora`, `confirmadoPaciente`) that together authorize publication.
- **Dupla autorização (Dual authorization)** — the invariant that both approvals must be `true` before a
  node is embedded, voiced and indexed.
- **Publicação (Publication)** — the pipeline that, on dual approval, produces the embedding, the cached
  audio, and the `arvore-fala` index document, and marks the node `ativo`.
- **Conteúdo por locale (Per-locale content)** — the text + embedding + audio_ref of a node in one
  language (EN or PT); authored and approved independently per ADR-018.
- **Embedding model-id (G5)** — the identifier of the self-hosted multilingual model TREI uses at index
  time; must equal CONV's query-time model id.
- **AudioRef** — the reference (path) to the cached synthesized audio file that CONV plays.

## Aggregates and Entities

- **`NoArvoreFala` (aggregate root).** Identity `no_id`; scoped by `empresa` + `paciente_id`. Holds tree
  position (parent / tipo `categoria|frase`), the two approval flags, overall `estado`, and a collection
  of **per-locale content** entities. A category node has no audio; a leaf (frase) node carries text +
  embedding + audio per published locale. *(Draft-level model; the specs layer confirms the exact
  aggregate/child split, coordinated with CONV's `conv_no_conteudo` shape.)*
- **`ConteudoLocale` (entity within the aggregate).** One per (`no_id`, `locale`): `texto`,
  `embeddingRef` (model-id + vector handle), `audioRef`, per-locale `estadoPublicacao`
  (`rascunho|publicando|ativo`). Approvals may be modeled per-locale (aligns with ADR-018 independence) —
  flagged as an open item in doc 06.
- **`SessaoTreinamento` (entity).** A training session: `empresa`, `paciente_id`, authoring `locale`,
  caregiver identity, LLM transcript reference, timestamps. Groups the proposals it produced.
- **`PropostaLlm` (value/audit record).** The raw LLM proposal (prompt/response reference) behind a draft
  node, retained for traceability (SEC-002).

Consumed by reference, **not** owned by TREI: `Paciente`, `Cuidador`, patient-with-session identity (all
ACS/PAC), and the CONV runtime read model.

## Domain Events

- **SpeechNodeProposed** (`Fala proposta`) — the LLM proposed a new category/phrase; a `rascunho` node
  was persisted. Carries `no_id`, `empresa`, `paciente_id`, `locale`, `tipo`.
- **CaregiverApprovalGranted** (`Cuidadora aprovou`) — `confirmadoCuidadora` set `true`.
- **PatientApprovalGranted** (`Paciente aprovou`) — `confirmadoPaciente` set `true`.
- **SpeechNodePublished** (`Fala publicada`, anchor) — dual approval satisfied and the publication
  pipeline completed for a locale: embedding + cached audio + `arvore-fala` index document exist and the
  node is `ativo`. This is the fact CONV depends on.
- **ProposalDiscarded** (`Proposta descartada`) — a caregiver manually discarded a draft.

These are **domain/audit** events emitted to HIST synchronously (no broker). "Event" here does not imply
Kafka — `messaging.enabled = false`.

## State Transitions

Per-node (and, where locales are independent, per-locale) lifecycle:

```
              LLM proposes                caregiver + patient
   (none) ───────────────▶ rascunho ───────────────────────────▶ publicando
                              │  (both approval flags true)          │
                              │                                      │ embedding + TTS + index OK
                              │ caregiver discards                   ▼
                              ▼                                    ativo  ──▶ (CONV retrievable)
                          descartado

   editar frase ativa ──▶ volta a rascunho (novo ciclo de aprovação, mesmo no_id, por locale)
```

- `rascunho → publicando`: only when `confirmadoCuidadora AND confirmadoPaciente` are `true`.
- `publicando → ativo`: only when embedding **and** cached audio **and** index write all succeed;
  failures retry via the local outbox, node stays `publicando` (CONV never sees it).
- `ativo → rascunho`: editing a published phrase re-opens a fresh approval cycle for that locale;
  the previously published locale content remains `ativo` until the new version is re-published (no gap
  in CONV availability).

## Invariants

1. **Dual authorization is mandatory.** No node/locale transitions past `rascunho` toward publication
   unless both approval flags are `true`. (Core ADR-016 invariant.)
2. **Publication atomicity.** A node/locale is `ativo` only if its embedding, cached audio file, and
   `arvore-fala` index document all exist and are mutually consistent. No partial publication is visible
   to CONV.
3. **Model-id coherence (G5).** The embedding recorded for a published node uses the same `model_id`
   agreed with CONV's query-time kNN; a mismatch is an invariant violation.
4. **Locale independence.** A locale is published only if that locale was approved; publishing PT never
   implies EN (ADR-018). Tree structure (position/`tipo`) is invariant across locales.
5. **Tenant + patient scoping.** Every node, approval, embedding and audio file is scoped to one
   `empresa` + `paciente_id`; nothing crosses tenants.
6. **No runtime generation leakage.** The LLM and ElevenLabs are only ever invoked inside TREI's
   authoring/publication flow, never on any read path CONV might share.
7. **Sensitivity guard.** Text sent to Claude/ElevenLabs stays in the everyday-need register; clinical /
   diagnostic content is never transmitted (SEC-002, ADR-017 PHI caveat).
