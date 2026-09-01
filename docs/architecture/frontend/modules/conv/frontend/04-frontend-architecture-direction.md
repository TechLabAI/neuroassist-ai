# 04 — Frontend Architecture Direction — CONV

## 1. Stack

- **Angular 21**, **Nx 22.x**, **TypeScript strict**.
- **RxJS 7.8 + Signals** — Signals for component/facade state; RxJS only at I/O edges.
- **Jest** (unit) + **Playwright** (`@nx/playwright`) for e2e.
- **Tailwind utilities + CSS-variable design tokens** (project design system).
- Browser APIs on-device: **MediaPipe Tasks Vision** (gesture channel, reused from
  GEST) and **Web Speech API** (voice fallback).
- Aligned with the existing prototype and the GEST/DASH modules already in production
  (same versions, same hexagonal-facade + signals conventions).

## 2. App boundary

Per shared **ADR-006** (module entry as library), CONV's lazy entry is a library
`libs/conv/shell-entry` with importPath **`@neuroassist/conv`** (tags `scope:conv`
`type:app`), exporting the barrel: `conv.contract`, `conv.module`, `conv.routes`,
`conv.permission-map`, `conv.feature-flags`, `conv.bootstrap`, `conv.nav.config`, and
`CONV_MODULE_DESCRIPTOR`. A thin host `apps/conv/` only does
`loadChildren: () => import('@neuroassist/conv').then(m => m.ConvModule)` and serves
as the buildable/serveable target + e2e web-server base.

**Shell placement:** unlike the admin modules, CONV is **not** registered into the
caregiver shell (`neuroassist-ai-web`). CONV's routes are hosted by a **separate,
restricted patient shell** (`libs/conv/patient-shell`) — see §5 and ADR-012/014 /
wireframe §6. Whether the patient shell is its own app (`apps/patient-web`) or a
guarded root within the web app is a specs decision; the invariant is *no caregiver
route is reachable from it*.

## 3. Library organization (Nx tags)

| Lib | tags | role |
|---|---|---|
| `libs/conv/shell-entry` | `scope:conv` `type:app` | lazy entry + descriptor (`@neuroassist/conv`) |
| `libs/conv/patient-shell` | `scope:conv` `type:feature` | restricted shell (topbar only) hosting Tela 06/09 |
| `libs/conv/access` | `scope:conv` `type:access` | patient-session guard(s), `conv.*` authorities |
| `libs/conv/data-access` | `scope:conv` `type:data-access` | HTTP adapters + signal facades |
| `libs/conv/feature-speech-tree` | `scope:conv` `type:feature` | Tela 06 components |
| `libs/conv/feature-tts` | `scope:conv` `type:feature` | TTSPlayer (cached audio + Web Speech fallback) |
| `libs/conv/feature-proposal-confirm` | `scope:conv` `type:feature` | Tela 09 components |
| `libs/conv/integration` | `scope:conv` `type:feature` | ConvShellIntegrationService; GEST-engine input bridge |
| `libs/conv/navigation` | `scope:conv` `type:util` | tree-level / breadcrumb state |
| `libs/conv/ui` (opt) | `scope:conv` `type:ui` | conv-local presentational components |

Boundaries (`nx-boundaries.md`): `scope:conv` imports only `scope:conv` +
`scope:shared`. The **GEST engine** (`scope:gest`) is composed at `scope:web` (the
patient shell/app), never imported directly by a `scope:conv` lib — the input bridge
in `libs/conv/integration` consumes an abstraction provided at the composition root.

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access` — HTTP base, patient-session token attachment.
- `@neuroassist/shared/ui-*` — design tokens, feedback (toasts/dialogs), theme.
- `@neuroassist/shared/error-model` — `ProblemDetail → AppError` mapping.
- `@neuroassist/shared/observability` — UX/event signals.
- GEST engine — consumed via an injected input-channel abstraction at composition
  root (not a compile-time `scope:conv → scope:gest` dependency).
- **Backend:** all via `conv-service` behind GWY — tree, semantic-search result,
  audio references, Tela 09 writes.

## 5. Routing strategy

- **Patient shell** hosts CONV; its route table contains **only** `arvore` (Tela 06)
  and `confirmar` (Tela 09) — no caregiver routes exist in this shell at all.
- CONV entry loaded via `loadChildren: () => import('@neuroassist/conv')`; sub-routes
  via `loadComponent`.
- Guards: `canActivate: [patientSessionGuard, convGuard]` — the session guard verifies
  an active `SessaoPaciente` JWT; `convGuard` checks `conv.arvore.navegar` /
  `conv.no.selecionar`.
- Default patient route → `arvore` (Tela 06). Tela 09 is reached from Tela 06 (a
  discreet "novas frases para confirmar" affordance), not from an admin menu.

## 6. State strategy

- **Signals-first, no NgRx.** Each facade owns a private `signal<T>` exposed via
  `.asReadonly()` (the prototype `data-access.ts` pattern, and the GEST FE
  `GestureTestFacade` VM-state-machine pattern surfaced by RAG).
- Facades: `SpeechTreeFacade` (tree levels + selected node), `TtsPlaybackFacade`
  (playback state + fallback), `SemanticAssistFacade` (search result), and
  `ProposalConfirmFacade` (Tela 09). A thin cross-feature orchestrator may live in
  `libs/conv/integration` if navigation ↔ playback coupling warrants it.
- The channel-agnostic selection is a single signal updated by both the gesture
  bridge and touch handlers (ADR-005) — the facade is the one writer.
- `computed()` for derived VMs (breadcrumb path, `hasAudio`, `pendingInLocale`);
  `effect()` only for side-effects like starting playback on leaf activation.

## 7. Tool contract strategy (`conv.contract.ts`)

- **Menu metadata:** CONV is a **patient-shell** tool, not a caregiver-menu tool — its
  descriptor marks it as not registered into the admin shell (or registered with a
  `patient-only` surface flag), so the module registry does not surface it in the
  caregiver sidebar.
- **Permissions:** `conv.arvore.navegar`, `conv.no.selecionar` (ADR-012). No caregiver
  authority is ever declared here.
- **Feature flags:** `semanticAssistEnabled` (fallback to manual navigation when off /
  ES down, OPS-003), `gestureChannelEnabled`, `ttsCacheEnabled` (fallback to Web
  Speech when off, OPS-004), `locale` default. Flags are read via the shared feature-
  flag service (mirrors EVNT's `FeatureFlagService` pattern surfaced by RAG).
