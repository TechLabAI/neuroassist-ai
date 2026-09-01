# 06 — Risks, Gaps, Assumptions — TREI (Frontend)

## 1. Risks

- **R-1 — Client-side generation leak (product-critical).** TREI is the only generative
  surface; the whole product's "runtime never generates" invariant (CONV/TECH-007) breaks
  if any Anthropic SDK or model credential reaches the browser bundle. *Mitigation:* the
  Anthropic call is server-side only (ADR-005 local); enforce with a dependency/lint check
  (no `@anthropic-ai/*` in `libs/trei/*` or `apps/trei/*`) **and** an e2e network
  assertion that no request to `api.anthropic.com` ever leaves the browser.
- **R-2 — Approval-side confusion.** A bug that lets TREI write `confirmadoPaciente` would
  bypass the patient's consent and auto-publish content to his runtime. *Mitigation:*
  structurally no method exists to write the patient side (INV-1, ADR-007 local); e2e
  asserts zero patient-side writes from any TREI route.
- **R-3 — LLM latency / failure UX.** Model calls can be slow or fail; a poor loading/error
  design would strand the caregiver mid-sentence. *Mitigation:* explicit
  `assistant-thinking` and retriable `error` states; existing drafts stay approvable during
  a conversation error (doc 05 §5).
- **R-4 — Locale cross-contamination.** Approving/editing one language accidentally
  mutating the other would publish unreviewed content. *Mitigation:* single active-locale
  selector, per-locale projection, no merged bilingual VM (INV-3, ADR-018); e2e asserts PT
  approval never flips EN.
- **R-5 — No prototype → design drift.** TREI has no BA prototype clone; the scaffold must
  compose caregiver-shell chrome + shared UI without inventing a new theme. *Mitigation:*
  reference only design-system tokens present in the app `theme.css` (Learning:
  fe-architect-promote token-parity finding); no ad-hoc colors/fonts; token-parity check
  at scaffold.
- **R-6 — Bundle bloat from chat UI.** A heavy chat/markdown/streaming library could
  regress the caregiver shell bundle. *Mitigation:* prefer shared UI primitives; justify
  any chat/markdown dependency by SPEC; lazy-load the feature.
- **R-7 — Sensitive content in the browser.** Proposed phrases and the patient's name
  appear in the conversation (LGPD/SEC-002). *Mitigation:* transient display only, nothing
  persisted client-side; conversation/proposals live in `trei-service`.
- **R-8 — Stale-draft approval race.** Two caregiver tabs (or a re-proposal) can make an
  approval act on stale data. *Mitigation:* server 409 on conflict → refetch + re-confirm
  (doc 05 §4).

## 2. Known gaps

- **G-1 — `trei-service` contract not yet specified.** Endpoints for session/turns
  (streaming or not), proposal projection by locale, and the caregiver-approval write are
  assumed here and must be pinned at `/fe-architect-specs` against the BE `trei-service`
  vision (which has not run yet).
- **G-2 — Streaming decision open in shape.** ADR-005 (local) fixes the *boundary* (server
  proxies the LLM) but leaves *SSE vs request/response* to the `trei-service` capability —
  behind the `streamingConversation` flag until the BE contract is known.
- **G-3 — Angular i18n mechanism undecided** (`@angular/localize` vs transloco) — ADR-018
  §5 TODO[arquiteto]; TREI fixes the EN/PT requirement, not the mechanism.
- **G-4 — Exact ACS authority names** for the authoring scopes (`trei.treinamento.*`) are
  provisional; must be reconciled with the ACS RBAC model at specs.
- **G-5 — "Editar" semantics.** Whether the caregiver edits free text or picks from
  assistant-suggested variants (and the exact freeze rule after approval, INV-5) needs a UX
  decision at specs.
- **G-6 — No BA mock data for Tela 08** (wireframe §5.4 marks mock as TODO) — specs/backlog
  need a fixture for `trei-service` proposals to build/e2e against.

## 3. Assumptions to validate

- **A-1** — `trei-service` exists (or will) as the single backend collaborator and owns
  the Anthropic + ElevenLabs + ES integrations; the FE talks only to it via GWY.
- **A-2** — The caregiver operates TREI on a full browser in the caregiver shell (desktop
  or tablet), not on the restricted patient device — so no motor-tolerance constraints of
  the patient shell apply here (text input is acceptable).
- **A-3** — Proposal volume per session is modest (tens, not thousands) → no-cache
  re-fetch on locale/turn is acceptable for MVP (doc 05 §3).
- **A-4** — The dual-approval handoff to the patient is **asynchronous** (no synchronous
  UI handoff between caregiver and patient devices) — the caregiver approves and leaves;
  the patient confirms later in CONV (ADR-016 §"handoff de UI").
- **A-5** — Ending a session preserving drafts matches the BA intent (wireframe §5.3
  `EndSessionButton`) — drafts are never discarded.

## 4. Critical dependencies

- **`trei-service` (BE)** — session/conversation, proposal projection, caregiver-approval
  write, and the LLM/ES/ElevenLabs side effects. Everything TREI-FE does depends on this
  contract (G-1).
- **ACS (`acs-service`)** — caregiver JWT + authoring RBAC (G-4).
- **CONV (Tela 09)** — the async downstream that consumes caregiver-approved drafts for
  patient confirmation; TREI's handoff target (not a direct call).
- **Shared libs** — `@neuroassist/shared/access`, `/ui-*`, `/error-model`,
  `/observability`; and the project **design-system tokens** (`design-system-tokens.md`)
  as the single theme source (R-5).
- **Shared ADR-006** (module entry as library) and the FE **standards/** (`module-pattern`,
  `nx-boundaries`, `access-layer-contracts`, `design-system-tokens`).
