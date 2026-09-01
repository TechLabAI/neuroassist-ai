# 04 — Frontend Architecture Direction — TREI

## 1. Stack

- **Angular 21**, **Nx 22.x**, **TypeScript strict**.
- **RxJS 7.8 + Signals** — Signals for component/facade state; RxJS only at I/O edges
  (HTTP; the streaming conversation channel if `trei-service` exposes SSE — see §6/ADR-005).
- **Jest** (unit) + **Playwright** (`@nx/playwright`) for e2e.
- **Tailwind utilities + CSS-variable design tokens** (project design system).
- **No on-device AI, no browser AI SDK** — unlike GEST (MediaPipe) / CONV (Web Speech),
  TREI has no on-device model; the Anthropic call is server-side only (ADR-005 local).
- Aligned with the caregiver-shell modules already in production (GEST/DASH/EVNT):
  same versions, same hexagonal-facade + signals conventions.

## 2. App boundary

Per shared **ADR-006** (module entry as library), TREI's lazy entry is a library
`libs/trei/shell-entry` with importPath **`@neuroassist/trei`** (tags `scope:trei`
`type:app`), exporting the barrel: `trei.contract`, `trei.module`, `trei.routes`,
`trei.permission-map`, `trei.feature-flags`, `trei.bootstrap`, `trei.nav.config`, and
`TREI_MODULE_DESCRIPTOR`. A thin host `apps/trei/` only does
`loadChildren: () => import('@neuroassist/trei').then(m => m.TreiModule)` and serves as
the buildable/serveable target + e2e web-server base.

**Shell placement:** TREI **is** registered into the **caregiver shell**
(`neuroassist-ai-web`) as a new sidebar item **"Treinamento"** (wireframe §7) — the
opposite of CONV, which is patient-shell-only. The caregiver shell already renders
Status · Histórico · Calibração · Onboarding; TREI adds "Treinamento" via
`TREI_MODULE_DESCRIPTOR` in `config/module-descriptors.ts` (boundary
`scope:web → scope:trei` is permitted).

## 3. Library organization (Nx tags)

| Lib | tags | role |
|---|---|---|
| `libs/trei/shell-entry` | `scope:trei` `type:app` | lazy entry + descriptor (`@neuroassist/trei`) |
| `libs/trei/access` | `scope:trei` `type:access` | caregiver-session guard(s), `trei.treinamento.*` authorities |
| `libs/trei/data-access` | `scope:trei` `type:data-access` | HTTP adapters + signal facades (conversation, proposals, session) |
| `libs/trei/feature-training-session` | `scope:trei` `type:feature` | Tela 08 — ChatConversationPanel, LocaleTabs, EndSessionButton |
| `libs/trei/feature-proposal-review` | `scope:trei` `type:feature` | ProposedNodeCard list, Aprovar/Editar, dual-confirmation indicator |
| `libs/trei/integration` | `scope:trei` `type:feature` | TreiShellIntegrationService (caregiver-shell registration) |
| `libs/trei/navigation` | `scope:trei` `type:util` | "Treinamento" nav / breadcrumb state |
| `libs/trei/ui` (opt) | `scope:trei` `type:ui` | trei-local presentational components (AI badge, chat bubble, status chip) |

Boundaries (`nx-boundaries.md`): `scope:trei` imports only `scope:trei` + `scope:shared`.
TREI has **no** cross-module compile-time dependency (no `scope:trei → scope:gest`, no
`scope:trei → scope:conv`); it collaborates only with `trei-service` over HTTP and with
shared libs. Caregiver-shell wiring is `scope:web → scope:trei` via the descriptor.

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access` — HTTP base, caregiver-session token attachment.
- `@neuroassist/shared/ui-*` — design tokens, feedback (toasts/dialogs), theme, form
  primitives for the chat input and edit affordance.
- `@neuroassist/shared/error-model` — `ProblemDetail → AppError` mapping (LLM/timeout
  failures rendered as retriable errors).
- `@neuroassist/shared/observability` — session/turn/approval UX signals.
- **Backend:** everything via `trei-service` behind GWY — conversation turns, draft
  `NoArvoreFala[]`, caregiver-side approval writes. **No** direct Anthropic/ElevenLabs/ES
  dependency, at compile time or runtime.

## 5. Routing strategy

- **Caregiver shell** hosts TREI; its route table gains **"Treinamento"** (Tela 08),
  alongside the existing caregiver routes.
- TREI entry loaded via `loadChildren: () => import('@neuroassist/trei')`; sub-routes via
  `loadComponent`.
- Guards: `canActivate: [caregiverSessionGuard, treiGuard]` — the session guard verifies
  an active caregiver JWT; `treiGuard` checks the authoring authority
  (`trei.treinamento.*`; exact scope names fixed at specs).
- Default TREI route → the training session screen (Tela 08). Sub-routes (if any, e.g. an
  isolated proposal-review deep link) are `loadComponent`-lazy.

## 6. State strategy

- **Signals-first, no NgRx.** Each facade owns a private `signal<T>` exposed via
  `.asReadonly()` (the prototype `data-access.ts` pattern and the CONV/GEST FE
  facade convention).
- Facades:
  - `TrainingSessionFacade` — session lifecycle + conversation turns + `assistant-thinking`
    state + active-locale signal (the single writer of active locale).
  - `ProposalReviewFacade` — draft `NoArvoreFala[]` projected for the active locale +
    the caregiver-side approval write (and **only** that write — INV-1/ADR-007).
- **Streaming vs request/response** for the conversation is decided in ADR-005 (local):
  if `trei-service` exposes SSE/streaming, the facade consumes it at the RxJS edge and
  pushes tokens into the `turns` signal; otherwise a simple request/response with an
  `assistant-thinking` loading state. Either way the component reads Signals only.
- `computed()` for derived VMs (`ApprovalSummaryVM`, `hasPendingProposals`, per-locale
  filtered proposal list); `effect()` only for genuine side-effects (e.g. auto-scroll the
  conversation on a new turn) — never for data mutation.
- The **active-locale signal** is the single selector that re-projects proposals; there is
  no per-locale duplicated state tree, only re-projection (ADR-018, INV-3).

## 7. Tool contract strategy (`trei.contract.ts`)

- **Menu metadata:** TREI **is** a caregiver-shell menu tool — its descriptor registers
  the **"Treinamento"** sidebar item into `neuroassist-ai-web` (unlike CONV, which is not
  in the caregiver menu). Icon/label per the design system; the badge "não é o app do
  paciente" is a *screen-level* affordance, not a menu concern.
- **Permissions:** `trei.treinamento.iniciar`, `trei.treinamento.propor`,
  `trei.treinamento.aprovar` (caregiver side) — provisional; final authority names fixed
  at specs against ACS. **No** patient authority and **no** `confirmadoPaciente` write is
  ever declared here (INV-1).
- **Feature flags:** `treinamentoEnabled` (module kill-switch), `streamingConversation`
  (SSE vs request/response, ADR-005), `voiceInputEnabled` (deferred caregiver voice input,
  default off). Flags read via the shared feature-flag service (mirrors the EVNT/GEST
  `FeatureFlagService` pattern).
