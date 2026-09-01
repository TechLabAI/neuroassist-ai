# 03 — Domain Framing — TREI (Frontend)

> This document does **not** redefine the domain. The ubiquitous language, the
> `NoArvoreFala` model and the approval semantics are owned by the backend (CONV/TREI
> BE) and the BA amendments. This is the **frontend framing**: the view models, UI
> events, and client-side state/invariants TREI-FE needs. Source of truth: `_brief.md`,
> the CONV BE/FE doc 03, wireframe Tela 08 (§5), ADR-016/018.

## 1. Ubiquitous language (mirror of the domain — do not redefine)

| Term | Meaning (as used by TREI-FE) |
|---|---|
| **NoArvoreFala** | A speech-tree node (category or leaf phrase). In TREI it is seen only in `status = rascunho` (draft/proposal). Carries `conteudo.<locale>` per language. |
| **conteudo.\<locale\>** | The per-language content + approval flags of a node: text, `confirmadoCuidadora`, `confirmadoPaciente`. Independent per locale (ADR-018). |
| **Proposta** | A draft `NoArvoreFala` produced during a training session, awaiting dual approval. |
| **confirmadoCuidadora** | The caregiver-side approval flag for a locale. **The only flag TREI-FE writes.** |
| **confirmadoPaciente** | The patient-side approval flag for a locale. **Read-only in TREI**; written only by CONV/Tela 09. |
| **Sessão de treinamento** | An authoring session: a conversation with the assistant plus the drafts produced in it. Ending it does not discard drafts. |
| **Assistente / LLM** | The Claude/Anthropic model, reached **only** via `trei-service`. TREI-FE renders its turns; it never calls it (TECH-007). |
| **Locale ativo** | The currently selected language (PT/EN) driving which `conteudo.<locale>` is authored/reviewed. |
| **Indexação / síntese** | ES index + ElevenLabs audio, triggered by `trei-service` on dual approval. **Never** a TREI-FE action (ADR-015/017). |

## 2. View models (draft — refined at specs SPEC-03)

```ts
// The active training session as the caregiver sees it
interface TrainingSessionVM {
  sessionId: string | null;         // null → no session started (empty state)
  status: 'idle' | 'active' | 'assistant-thinking' | 'ended' | 'error';
  activeLocale: 'pt' | 'en';
  turns: ConversationTurnVM[];
  hasPendingProposals: boolean;     // gates the "ended with pending drafts" notice
}

interface ConversationTurnVM {
  id: string;
  author: 'assistant' | 'caregiver';
  text: string;
  at: string;                       // ISO timestamp
}

// One proposed node, projected for the ACTIVE locale only
interface ProposedNodeVM {
  nodeId: string;
  categoria: string;                // parent category label (active locale)
  frase: string;                    // proposed phrase text (active locale)
  locale: 'pt' | 'en';             // the locale this projection is for
  caregiverApproved: boolean;       // conteudo[locale].confirmadoCuidadora
  patientApproved: boolean;         // conteudo[locale].confirmadoPaciente (read-only)
  editable: boolean;                // false once caregiver-approved (or per specs)
}

// Derived: what still needs the caregiver / the patient, per active locale
interface ApprovalSummaryVM {
  awaitingCaregiver: number;        // caregiverApproved === false
  awaitingPatient: number;          // caregiverApproved && !patientApproved (handed off to CONV)
  readyBothSides: number;           // both true → BE will index/synthesize
}
```

> VMs are always projected for **one** locale (the active one). There is no "merged
> bilingual node" VM — locale independence (ADR-018) is modeled by projecting the same
> `NoArvoreFala` twice, never by collapsing the two languages into one view.

## 3. UI events (caregiver-driven → state change)

| Event | Trigger | Effect |
|---|---|---|
| `StartTrainingRequested` | CTA "Iniciar treinamento" on empty state | create session (`trei-service`), status → `active` |
| `CaregiverTurnSubmitted` | caregiver sends a chat message | append caregiver turn; status → `assistant-thinking`; request assistant reply via `trei-service` |
| `AssistantTurnReceived` | `trei-service` returns the assistant reply (+ any new drafts) | append assistant turn; refresh proposal list; status → `active` |
| `LocaleSwitched` | `LocaleTabs` PT ⇄ EN | flip active-locale signal; re-project proposals + summary for the new locale |
| `ProposalEditRequested` | "Editar" on a `ProposedNodeCard` | open edit affordance for the active-locale text (pre-approval only) |
| `CaregiverApprovalToggled` | "Aprovar" on a card | write `conteudo[activeLocale].confirmadoCuidadora = true` via `trei-service` |
| `TrainingSessionEnded` | "Encerrar sessão de treinamento" | status → `ended`; pending drafts preserved; show retention notice |
| `AssistantErrorRaised` | `trei-service`/LLM failure | status → `error`; surface retriable error; keep existing proposals visible |

## 4. State transitions (UI level)

**Training session:**
```
idle ──StartTrainingRequested──▶ active
active ──CaregiverTurnSubmitted──▶ assistant-thinking
assistant-thinking ──AssistantTurnReceived──▶ active
assistant-thinking ──AssistantErrorRaised──▶ error
error ──(retry)──▶ assistant-thinking
active ──TrainingSessionEnded──▶ ended
```

**Proposal (per node, per active locale):**
```
proposed ──CaregiverApprovalToggled──▶ caregiver-approved (awaiting patient, handed to CONV)
caregiver-approved ──(patient confirms in CONV/Tela 09)──▶ ready-both-sides ──(BE)──▶ indexed+synthesized
proposed ──(patient rejects in CONV, other locale)──▶ still proposed for this locale
```

> TREI-FE can drive a proposal only as far as **caregiver-approved**. The rest of the
> lifecycle (`ready-both-sides`, `indexed+synthesized`) is observed, never actuated,
> from TREI — the patient side is CONV's, and indexing/synthesis is the backend's.

## 5. Invariants (client-side)

- **INV-1 — TREI never writes the patient side.** No TREI code path sets
  `confirmadoPaciente`; the approval facade exposes only a caregiver-side write
  (ADR-007 local). Enforced structurally (no method exists) and asserted in e2e.
- **INV-2 — TREI never generates content locally.** Proposed text always originates from
  `trei-service`; the FE has no code path that fabricates a `frase`/`categoria`
  (ADR-005 local, TECH-007).
- **INV-3 — Locale isolation.** Every read/write is scoped to the active locale;
  approving/editing one locale never mutates the other's `conteudo` (ADR-018).
- **INV-4 — No-loss session end.** Ending a session never deletes drafts; pending
  proposals must survive to the next session.
- **INV-5 — Edit-before-approve only.** A proposal is editable only while
  `caregiverApproved === false` (or per the exact rule fixed at specs); once the caregiver
  side is approved, the text is frozen for that locale to keep the CONV/patient view
  stable.
- **INV-6 — Explicit AI framing is always present.** Every TREI route renders the
  "not the patient app" affordance; TREI never renders the patient runtime surface
  (route-absence, TECH-007).
- **INV-7 — Client validation is presentational, not authoritative.** Any client-side
  check (non-empty phrase, length caps) is a UX aid; `trei-service` remains the authority
  on what a valid proposal/approval is.
