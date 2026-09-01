# SPEC-03 — View Models — TREI (Frontend)

> Refines doc 03 §2 into the final Signals-based VM contracts. Every VM is a TypeScript
> `interface`/`type` held in a private `signal<T>`/`computed<T>` inside a facade and exposed
> via `.asReadonly()` (doc 05 §1). VMs are always projected for **one** locale (the active
> one) — there is no merged bilingual node VM (ADR-018, INV-3).

## 1. Shared primitives

```ts
export type Locale = 'pt' | 'en';                         // authoring locale (api-contract §1)

export type SessionStatus =
  | 'idle'                // no session started → empty state
  | 'active'              // session live, awaiting caregiver turn
  | 'assistant-thinking'  // turn sent, waiting on trei-service (LLM proxy)
  | 'ended'               // session closed; drafts preserved
  | 'error';              // trei-service / Anthropic failure on the conversation

// Read-model of trei-service authoring sub-state (api-contract §2.6 subEstado)
export type NodeSubEstado = 'rascunho' | 'aguardando_aprovacao' | 'ativo' | 'descartado';
```

## 2. `TrainingSessionVM` — owned by `TrainingSessionFacade`

```ts
export interface ConversationTurnVM {
  id: string;
  author: 'assistant' | 'caregiver';
  text: string;
  at: string;                        // ISO-8601
}

export interface TrainingSessionVM {
  sessionId: string | null;          // null → no session (empty state, status = 'idle')
  patientId: string | null;          // scope of the session (from start response, api-contract §2.1)
  status: SessionStatus;
  activeLocale: Locale;              // single writer = TrainingSessionFacade (INV-3)
  llmModelId: string | null;         // disclosed model id (api-contract §2.1) — display/observability only
  aiDisclosed: boolean;              // ADR-017 disclosure flag from BE; true → banner shows "gerado por IA"
  turns: ConversationTurnVM[];       // working state, not a cache (doc 05 §3)
  hasPendingProposals: boolean;      // gates the "ended with pending drafts" notice (INV-4)
  error: AppError | null;            // present WHILE status = 'error' (retriable)
}
```

**UI events exposed** (facade methods, each maps to a doc 03 §3 event):
`startSession()` · `submitTurn(instrucao: string)` · `retryLastTurn()` ·
`switchLocale(locale: Locale)` · `endSession()`.

**Invariants:**
- INV-2 — the assistant turn is appended **only** when `trei-service` returns it; the FE never fabricates a `text` for an `author: 'assistant'` turn. The caregiver's own turn may render immediately (doc 05 §4).
- INV-3 — `activeLocale` has exactly one writer (`switchLocale`); `ProposalReviewFacade` reads it, never copies it.

## 3. `ProposedNodeVM` — owned by `ProposalReviewFacade` (projected for active locale)

```ts
export interface ProposedNodeVM {
  // identity: a proposal starts un-materialized (propostaId only), becomes a draft node on materialize
  propostaId: string | null;         // LLM suggestion id (api-contract §2.2) before materialization
  nodeId: string | null;             // conv_no_arvore_fala id (api-contract §2.3) once materialized
  locale: Locale;                    // the locale THIS projection is for
  tipo: 'frase' | 'categoria';       // only 'frase' is speakable downstream (api-contract §2.3)
  categoria: string;                 // parent category label (active locale)
  texto: string;                     // proposed phrase text (active locale)
  subEstado: NodeSubEstado;          // read-model of BE authoring state (null-mapped to 'rascunho' pre-node)
  caregiverApproved: boolean;        // conteudo[locale].confirmadoCuidadora — the ONLY flag TREI writes
  patientApproved: boolean;          // conteudo[locale].confirmadoPaciente — READ-ONLY (INV-1)
  editable: boolean;                 // true only WHILE !caregiverApproved (INV-5)
  clinicalFlag: boolean;             // api-contract §2.2 — LLM output flagged clinical; blocks approve until edited
  pending: boolean;                  // per-card optimistic/in-flight marker (doc 05 §4)
}
```

**UI events exposed** (facade methods):
`materializeOrEditProposal(input: {propostaId?; nodeId?; locale; texto})` ·
`approveCaregiverSide(input: {nodeId; locale})` ·
`discardDraft(input: {nodeId; locale})` ·
`refreshForLocale(locale: Locale)`.

**Invariants:**
- INV-1 — no method sets `patientApproved`; the VM carries it read-only from the BE node projection (api-contract §2.6).
- INV-5 — `editable` derives from `!caregiverApproved`; once the caregiver side is approved for a locale, the text is frozen for that locale.
- **Clinical gate (api-contract §2.2 / SEC-002):** WHILE `clinicalFlag` is true, `Aprovar` is disabled until the caregiver edits the text into the everyday-need register.

## 4. `ApprovalSummaryVM` — `computed` in `ProposalReviewFacade`

```ts
export interface ApprovalSummaryVM {
  awaitingCaregiver: number;   // caregiverApproved === false
  awaitingPatient: number;     // caregiverApproved && !patientApproved  (handed off to CONV/Tela 09)
  readyBothSides: number;      // both true → trei-service will index/synthesize (observed, never actuated)
  activeLocale: Locale;        // the locale this summary is computed for (INV-3)
}
```

Derived purely from `proposals()` + `activeLocale()`; recomputes on `LocaleSwitched` and
`AssistantTurnReceived` without a duplicated state tree (INV-3, doc 05 §1).

## 5. Criterios de aceite (EARS)

- WHILE no training session has started, THE SYSTEM SHALL present the session view model with `status = 'idle'` and SHALL render the empty-state CTA.
- WHEN the caregiver submits a turn, THE SYSTEM SHALL set `status = 'assistant-thinking'` and SHALL NOT append any assistant turn until `trei-service` returns it.
- IF a `trei-service` conversation call fails, THEN THE SYSTEM SHALL set `status = 'error'` with a retriable `error` and SHALL keep existing proposals visible.
- WHEN the active locale is switched, THE SYSTEM SHALL re-project the proposal list and the approval summary for the newly active locale and SHALL NOT alter the other locale's approval flags.
- THE SYSTEM SHALL expose `patientApproved` on each proposed node as read-only and SHALL provide no view-model path that sets the patient side.
- WHILE a proposed node has `caregiverApproved = true`, THE SYSTEM SHALL render it as non-editable for that locale.
- WHILE a proposed node carries `clinicalFlag = true`, THE SYSTEM SHALL disable caregiver approval until the caregiver edits the text.
- THE SYSTEM SHALL compute the approval summary (`awaitingCaregiver`, `awaitingPatient`, `readyBothSides`) from the active-locale projection only.
