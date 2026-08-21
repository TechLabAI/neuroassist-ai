# SPEC-01 — Route Map — HIST (`@neuroassist/hist`)

> Module: **HIST — Histórico e Auditoria** · Library import path: `@neuroassist/hist` · Shell route: `/historico`
> Traces FE ADR-005 (human-readable immutable ledger), vision doc 04 §5 (routing), doc 05 §6 (nav).
> Consumes **only** the History surface of `hist-service` (api-contract §3, §8). FE never calls the ingestion surface.

## 1. Route master table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/historico` | `HistoryTimelineComponent` | `libs/hist/feature-timeline` | `authGuard`, `histGuard` | "Histórico" | `Início › Histórico` |
| `/historico/:eventId` (post-MVP, flag `detailRoute`) | `EventDetailComponent` | `libs/hist/feature-timeline` | `authGuard`, `histGuard` | "Histórico — Evento" | `Início › Histórico › <evento>` |

- The Shell (`neuroassist-ai-web`) lazy-loads the library: `loadChildren: () => import('@neuroassist/hist').then(m => m.HistModule)`.
- Sub-routes resolve via `loadComponent`. `apps/hist` is a thin host whose `app.routes.ts` forwards to `@neuroassist/hist`.
- The `/historico/:eventId` detail route is gated behind the `detailRoute` feature flag (default off in MVP — gap G5).

## 2. Query parameters (state in the URL)

| Param | Values | Default | Meaning |
|---|---|---|---|
| `period` | `hoje` \| `semana` \| `mes` | `hoje` (flag `defaultPeriod`) | Period filter; maps to `from`/`to` bounds on the backend `GET /history` (api-contract §3.1). |
| `subjectType` | `paciente` \| `comando` \| `cuidador` \| `dispositivo` | (unset) | Optional subject-type facet. |
| `subjectId` | uuid | (unset) | Optional subject facet; **requires** `subjectType` (BE rejects `400 /errors/incomplete-subject-filter` otherwise). |

- `period` is authoritative in the URL so the view is shareable and the Home deep-link `"Ver histórico de hoje"` lands on `/historico?period=hoje`.
- Changing period rewrites the query param (replaceUrl) and resets pagination to page 0.

## 3. Guards

| Guard | Lib | Responsibility | Failure |
|---|---|---|---|
| `authGuard` | `@neuroassist/shared/access` | Valid authenticated session (bearer token present, not expired). | Redirect to `/login?returnUrl=/historico`. |
| `histGuard` | `libs/hist/access` | Caregiver↔patient vínculo present for the active `X-Empresa-Id` tenant (ACS-resolved). | Redirect to `/sem-acesso` with reason `vinculo-ausente`. |

- `X-Empresa-Id` is attached by the shared `access-http-base` interceptor; the FE never sets the tenant from user input. Tenant authority is the JWT `tenant` claim server-side (api-contract §1.1).
- `histGuard` fails **closed**: an unverifiable vínculo denies the route rather than showing an empty list.

## 4. Navigation & breadcrumb

- `libs/hist/navigation` produces `BreadcrumbItem[]`: `Início › Histórico` (+ `› <evento>` when detail route active).
- `HistShellIntegrationService` (`libs/hist/integration`) registers the "Histórico" sidebar entry (`hist.nav.config.ts`) and the lazy route with the Shell; the sidebar highlights "Histórico" while active.

## Criterios de aceite (EARS)

- WHEN a caregiver navigates to `/historico` without a `period` query param, THE SYSTEM SHALL apply the `defaultPeriod` flag value (`hoje`) and reflect it in the URL.
- WHEN the caregiver follows the Home link "Ver histórico de hoje", THE SYSTEM SHALL open `/historico?period=hoje` and load the today timeline.
- IF `authGuard` fails because the session is absent or expired, THEN THE SYSTEM SHALL redirect to `/login` carrying the return url `/historico`.
- IF `histGuard` cannot confirm the caregiver↔patient vínculo for the active tenant, THEN THE SYSTEM SHALL redirect to `/sem-acesso` and SHALL NOT render the timeline.
- WHEN a `subjectId` query param is present without `subjectType`, THE SYSTEM SHALL omit the subject facet from the backend query rather than sending an incomplete filter that the backend rejects with `400`.
- WHERE the `detailRoute` feature flag is disabled, THE SYSTEM SHALL treat `/historico/:eventId` as unavailable and keep navigation on the timeline list.
- THE SYSTEM SHALL keep the active `period` reflected in the URL query string so the view is shareable.
