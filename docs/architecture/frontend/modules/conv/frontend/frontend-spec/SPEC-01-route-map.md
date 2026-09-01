# SPEC-01 — Route Map — CONV (Frontend)

> Module **CONV** (Conversação por Árvore de Falas) · lazy entry `@neuroassist/conv`
> (shared ADR-006) · hosted by a **restricted patient shell**, never by the caregiver
> shell (`neuroassist-ai-web`). Isolation is by **absence of route**, not by a guard on
> a shared shell (ADR-012/014, vision doc 04 §5). Consumes `conv-service` behind GWY.

## 1. Shell placement decision (closes vision doc 04 §2 open TODO)

CONV's routes live under a **dedicated patient shell root** (`libs/conv/patient-shell`),
mounted as its own top-level route `/paciente` in the web app (`apps/neuroassist-ai-web`),
guarded so no child of it can reach a caregiver surface. The shell renders a topbar only
(no sidebar, no admin menu). This keeps a single deployable web app while giving the
patient surface its own guarded, sidebar-less root — the invariant *no caregiver route is
reachable from the patient shell* holds by that root exposing only `{ arvore, confirmar }`.

> The alternative (a separate `apps/patient-web`) is deferred to M6 promote/scaffold; the
> route contract below is identical either way (only the host app differs). Recorded as an
> open scaffold decision — see SPEC-06 §4 and doc 06 §2.

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/paciente` | `PatientShellComponent` | `libs/conv/patient-shell` | `patientSessionGuard` | "NeuroAssist" | [] |
| `/paciente` (redirect) | → `/paciente/arvore` | `libs/conv/shell-entry` | — | — | — |
| `/paciente/arvore` | `SpeechTreePageComponent` | `libs/conv/feature-speech-tree` | `patientSessionGuard`, `convNavegarGuard` | "Falar" | [ Falar ] |
| `/paciente/arvore/:categoriaId` | `SpeechTreePageComponent` | `libs/conv/feature-speech-tree` | `patientSessionGuard`, `convNavegarGuard` | "Falar" | [ Falar › «categoria» ] |
| `/paciente/confirmar` | `ProposalConfirmPageComponent` | `libs/conv/feature-proposal-confirm` | `patientSessionGuard`, `convNavegarGuard` | "Novas frases" | [ Falar › Novas frases ] |
| `**` (patient shell) | → `/paciente/arvore` | `libs/conv/patient-shell` | — | — | — |

Notes:
- The lazy CONV entry is loaded once: `loadChildren: () => import('@neuroassist/conv').then(m => m.ConvModule)`; the two feature pages are `loadComponent`-lazy under it.
- `:categoriaId` drives the active tree level (a `parentId` for `GET /tree`); the root level is `/paciente/arvore` with no param. Deep-linking to a category is supported but breadcrumbs never expose a path outside the patient surface.
- No caregiver route (`/historico`, `/calibracao`, `/onboarding`, login) is a child of `/paciente`; they do not exist in this shell's route table at all.

## 3. Guards

| Guard | Lib | Checks | On failure |
|---|---|---|---|
| `patientSessionGuard` | `libs/conv/access` | active `SessaoPaciente` JWT (`status = ativa`), attached by `@neuroassist/shared/access` | redirect to session-expired exit (shell teardown), never to a caregiver login |
| `convNavegarGuard` | `libs/conv/access` | JWT `scope` contains `conv.arvore.navegar` | redirect to `/paciente/arvore` empty/forbidden state; never expose a caregiver route |

- **Session revocation reflection (ADR-012, doc 06 §4):** a `401 session-revoked` from any `conv-service` call ends the patient shell (teardown, camera released) — the shell must reflect a revoked session by exiting, not by showing a caregiver surface.
- `conv.no.selecionar` scope is **not** a route guard — it gates the *select* action inside Tela 06 (see SPEC-05 RBAC), because navigation must load even if selection is later denied.

## 4. Lazy-loading & teardown

- CONV entry loaded via `loadChildren` (ADR-006); `SpeechTreePageComponent` and `ProposalConfirmPageComponent` via `loadComponent`.
- **Camera teardown on route leave (vision success signal "zero camera leaks"):** leaving `/paciente/arvore` must release any `MediaStream` opened by the GEST input bridge; no track stays active after navigation away (reuses GEST teardown discipline).

## 5. Criterios de aceite (EARS)

- THE SYSTEM SHALL expose under the patient shell exactly the routes `arvore` and `confirmar` and SHALL expose no caregiver route within that shell.
- WHEN the patient opens `/paciente`, THE SYSTEM SHALL redirect to `/paciente/arvore` (Tela 06) as the default route.
- IF the `patientSessionGuard` finds no active `SessaoPaciente` JWT, THEN THE SYSTEM SHALL end the patient shell session and SHALL NOT redirect to a caregiver login surface.
- IF the `convNavegarGuard` finds the JWT lacks scope `conv.arvore.navegar`, THEN THE SYSTEM SHALL redirect to the árvore forbidden state and SHALL NOT reveal any caregiver route.
- WHILE a `conv-service` response reports the patient session revoked (401 session-revoked), THE SYSTEM SHALL exit the patient shell and SHALL release any active camera track.
- WHEN the patient navigates away from the árvore route, THE SYSTEM SHALL release every active `MediaStream` camera track opened by the gesture channel.
- IF a request targets an unknown path within the patient shell, THEN THE SYSTEM SHALL redirect to `/paciente/arvore` and SHALL NOT render a caregiver surface.
