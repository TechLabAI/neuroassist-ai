# SPEC-01 — Route Map — DASH (Frontend)

> Module: **DASH — Dashboard e Notificações** · Entry lib: `@neuroassist/dash` · Shell app: `neuroassist-ai-web` (`scope:web`)
> Sources: FE 02 §3, FE 04 §5, FE 05 §6, ADR-005 (local), ADR-006 (shared, module-as-library).
> DASH is the panel's **default landing** — `scope:web` maps `/` → DASH Home/Status.

## 1. Route ownership model

DASH ships a single lazy `NgModule` (`dash.module.ts`, importPath `@neuroassist/dash`, ADR-006). The shell
host (`neuroassist-ai-web`, `scope:web`) mounts it with `loadChildren` and makes DASH Home the default route
of the caregiver panel. All DASH sub-routes are `loadComponent` lazy children rendered **inside the shell
chrome**, except `/entrada` which renders **outside** the chrome (brand entry).

The **alarm overlay is not a route** — it is a global shell outlet owned by EVNT, mounted above the
router-outlet through the `ShellOutletRegistry` region DASH declares. It causes no URL change and no
breadcrumb (FE 04 §5, FE 05 §6).

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/entrada` | `EntradaPageComponent` | `libs/dash/feature-entrada` | (none — public brand entry) | "Entrar no painel" | (chromeless; no breadcrumb) |
| `/` | `HomeStatusPageComponent` | `libs/dash/feature-home` | `authGuard` | "Status" | `[Status]` |
| `/notificacoes` | `NotificationCenterPageComponent` | `libs/dash/feature-notifications` | `authGuard` | "Notificações" | `[Status, Notificações]` |

> `Status` is the **root** of the sidebar (FE 05 §6). The `Onboarding` sidebar entry is not a DASH route —
> it is an ACS-owned surface that DASH's nav config only *links to*, gated by `roleGuard` (hidden for the
> `cuidadora` role; visible to `coordenador_clinica`/condutor técnico). Cross-module navigation (e.g.
> quick-link → HIST "Histórico de hoje") is a shell router change; the target module owns its breadcrumb.

## 3. Guards

| Guard | Lib | Applies to | Behavior |
|---|---|---|---|
| `authGuard` | `@neuroassist/shared/access-decision` + `auth-session` | `/`, `/notificacoes` (shell subtree) | requires a valid ACS session; unauthenticated → redirect to `/entrada`. |
| `roleGuard` (nav-level) | `libs/dash/access` | `Onboarding` **nav item** (not a DASH route) | `WHILE` role = `cuidadora`, hide/disable the `Onboarding` link (admin/condutor only). |

DASH invents **no RBAC logic** — role/entitlement come from `@neuroassist/shared/access-decision`; the
backend re-enforces every authority. Guards here are UI affordances, not the security boundary.

## 4. Navigation state

- `libs/dash/navigation` feeds `BreadcrumbItem[]` for DASH's own routes (`Status`, `Notificações`).
- The alarm overlay contributes no breadcrumb (global interrupt, EVNT).
- Deep-links: `/notificacoes` is directly addressable (bell/inbox); on cold load behind `authGuard` an
  unauthenticated hit redirects to `/entrada` and returns after login.

## Critérios de aceite (EARS)

- WHEN an unauthenticated visitor requests `/` or `/notificacoes`, THE SYSTEM SHALL redirect to `/entrada`
  and preserve the requested return path.
- IF `authGuard` fails to resolve a valid caregiver session, THEN THE SYSTEM SHALL redirect to `/entrada`
  and SHALL NOT render the shell chrome.
- WHILE the authenticated caregiver's role is `cuidadora`, THE SYSTEM SHALL hide the `Onboarding` navigation
  item from the sidebar.
- WHEN the caregiver navigates to `/notificacoes`, THE SYSTEM SHALL render the Notification Center inside the
  shell chrome with breadcrumb `Status › Notificações`.
- WHERE the `/entrada` brand entry is rendered, THE SYSTEM SHALL render it outside the shell chrome (no
  sidebar, no topbar, no breadcrumb).
- THE SYSTEM SHALL render DASH Home/Status as the default landing route (`/`) of the caregiver panel.
