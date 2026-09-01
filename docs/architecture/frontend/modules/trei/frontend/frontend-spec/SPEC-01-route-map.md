# SPEC-01 — Route Map — TREI (Frontend)

> Module **TREI** (Treinamento Assistido por IA) · lazy entry `@neuroassist/trei`
> (shared ADR-006) · hosted by the **caregiver shell** (`neuroassist-ai-web`), the
> opposite of CONV (patient-shell-only). TREI adds a **"Treinamento"** sidebar item via
> `TREI_MODULE_DESCRIPTOR`; the boundary `scope:web → scope:trei` is permitted
> (`nx-boundaries.md`). Consumes **only** `trei-service` behind GWY at `/api/v1/trei/**`.
> Source of truth: FE vision docs 01/02/04/05, api-contract (`trei-service`), ADR-005/007
> (local), shared ADR-006.

## 1. Shell placement decision

TREI's routes live **inside the caregiver shell** at a new top-level route `/treinamento`,
alongside the existing caregiver routes (`/historico`, `/calibracao`, `/onboarding`,
Status). The shell chrome (sidebar/topbar/breadcrumb) is **owned by `neuroassist-ai-web`**;
TREI only registers into it (ADR-006, doc 04 §2/§7). Every TREI route additionally renders
the **"Treinamento — não é o app do paciente"** affordance (INV-6, TECH-007, wireframe
§5.5) — a screen-level banner on the route subtree, not a menu concern.

The MVP is a **single screen (Tela 08)**: the training session and its proposal review
coexist on one route. Active locale (PT/EN) is **screen state** (a `LocaleTabs` toggle),
**not** a route param (doc 05 §6); promoting locale to a `:locale` route param is deferred
to a later specs decision.

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/treinamento` | `TreiRootComponent` | `libs/trei/shell-entry` | `caregiverSessionGuard`, `treiGuard` | "Treinamento" | [ Treinamento ] |
| `/treinamento` (redirect) | → `/treinamento/sessao` | `libs/trei/shell-entry` | — | — | — |
| `/treinamento/sessao` | `TrainingSessionPageComponent` | `libs/trei/feature-training-session` | `caregiverSessionGuard`, `treiGuard` | "Treinamento" | [ Treinamento › Sessão ] |
| `/treinamento/sessao/propostas` | `ProposalReviewPageComponent` | `libs/trei/feature-proposal-review` | `caregiverSessionGuard`, `treiGuard`, `treiProporGuard` | "Propostas" | [ Treinamento › Propostas ] |
| `**` (within TREI subtree) | → `/treinamento/sessao` | `libs/trei/shell-entry` | — | — | — |

Notes:
- The lazy TREI entry is loaded once: `loadChildren: () => import('@neuroassist/trei').then(m => m.TreiModule)`; the feature pages are `loadComponent`-lazy under it.
- `ProposalReviewPageComponent` is the deep-linkable review sub-view; in the MVP it may render inline within `TrainingSessionPageComponent`, but its own route exists so a caregiver can deep-link to review without an active conversation turn (doc 02 §3 edge case "trei-service failure → existing drafts remain approvable").
- **No patient route** (`/paciente/**`, CONV's speech-tree runtime) is ever a child of `/treinamento`; TREI renders no patient runtime surface (INV-6, route-absence check).

## 3. Guards

| Guard | Lib | Checks | On failure |
|---|---|---|---|
| `caregiverSessionGuard` | `libs/trei/access` | active **caregiver** JWT (ACS realm), attached by `@neuroassist/shared/access` | redirect to caregiver login (shared); TREI implements no login of its own |
| `treiGuard` | `libs/trei/access` | JWT `scope` contains `treinamento.sessao.conduzir` (the authoring/session authority) | redirect to caregiver home forbidden state; never expose a patient surface |
| `treiProporGuard` | `libs/trei/access` | JWT `scope` contains `treinamento.no.propor` (materialize/edit authority) | redirect to `/treinamento/sessao`; the read/review surface stays reachable |

- **Session revocation reflection (ADR-003/ADR-012):** a `401 session-revoked` from any `trei-service` call ends the caregiver TREI session via the shared access layer (re-auth / redirect), fail-closed — never a silent continue.
- `treinamento.no.aprovar.cuidadora` and `treinamento.no.descartar` are **not** route guards — they gate the *Aprovar* and *Descartar* actions inside the review surface (SPEC-05 RBAC), so a caregiver may review even when a specific action is denied.
- **No patient authority** (`treinamento.no.aprovar.paciente`) is ever referenced by any TREI guard — that scope belongs to the patient shell / CONV (INV-1, ADR-007).

## 4. Lazy-loading & lifecycle

- TREI entry loaded via `loadChildren` (ADR-006); `TrainingSessionPageComponent` and `ProposalReviewPageComponent` via `loadComponent`.
- **Session teardown on route leave:** leaving `/treinamento` clears the in-memory conversation `turns` and marks the session `ended`; **pending drafts are never discarded** (INV-4) — they live in `trei-service`, not the browser, so navigating away loses no authored content.
- **No media/camera** is opened by TREI (unlike CONV/GEST): there is no `MediaStream`, no device SDK, and no browser AI SDK to tear down (INV-2, ADR-005).

## 5. Criterios de aceite (EARS)

- THE SYSTEM SHALL expose the TREI routes only within the caregiver shell under `/treinamento` and SHALL NOT expose any patient runtime route within that subtree.
- WHEN a caregiver opens `/treinamento`, THE SYSTEM SHALL redirect to `/treinamento/sessao` as the default training route.
- IF the `caregiverSessionGuard` finds no active caregiver session, THEN THE SYSTEM SHALL redirect to the caregiver login and SHALL NOT render any TREI training surface.
- IF the `treiGuard` finds the JWT lacks scope `treinamento.sessao.conduzir`, THEN THE SYSTEM SHALL redirect to the caregiver forbidden state and SHALL NOT render the training session.
- IF the `treiProporGuard` finds the JWT lacks scope `treinamento.no.propor`, THEN THE SYSTEM SHALL keep the review surface reachable and SHALL NOT open the proposal materialize/edit route.
- WHILE a `trei-service` response reports the caregiver session revoked (401 session-revoked), THE SYSTEM SHALL end the TREI session fail-closed and SHALL NOT continue rendering authenticated training content.
- WHEN the caregiver navigates away from the training route, THE SYSTEM SHALL clear the in-memory conversation turns and SHALL NOT discard any pending draft proposal.
- WHERE the caregiver deep-links to `/treinamento/sessao/propostas`, THE SYSTEM SHALL render the pending drafts for review even when no conversation turn is in flight.
- THE SYSTEM SHALL render the "não é o app do paciente" authoring affordance on every TREI route.
