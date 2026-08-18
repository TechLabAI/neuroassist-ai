# 02 — Architecture Blueprint — DASH (Frontend)

## 1. System boundary

DASH-FE is a client of the API gateway via `@neuroassist/shared/access-http-base`
(the interceptor injects `X-Empresa-Id`). It reads a single **home summary aggregate**
from `dash-service` and a **notification-dispatch list** (dispatch history from EVNT's
`NotificationDispatch`, exposed via `dash-service` or `evnt-service`). It makes **no
direct calls to legacy systems** and **no cross-module HTTP fan-out** for the calm home
(ADR-005). It composes — at the shell (`scope:web`) — signals re-exported by other
modules (EVNT `activeEvent`, EDGE device status). DASH itself performs **no domain
writes**; it is a read/compose surface.

## 2. External actors and systems

- **Authenticated browser** — session established by the shell via MSAL / `auth-session`
  (owned by ACS). DASH reads the session and role; it does not authenticate.
- **Backend via `@neuroassist/shared/access-http-base`** — `dash-service`
  (`GET /dashboard/summary`, `GET /notifications`), plus signals from EVNT/EDGE facades.
- **No 3rd-party widgets.** No AI panel (`ai.hasAi = false`).

## 3. End-to-end user flows

### Golden path — "Está tudo bem?" (Tela 00 → Tela 01)

```text
[Entrada]  "Um gesto que você não vai perder."   [ Entrar no painel ]
    │  (CTA → auth session já válida → entra no shell)
    ▼
┌──────────┬──────────────────────────────────────────────────────┐
│ Status ● │  Márcio Amaral            🟢 Dispositivo online       │  ← topbar (EDGE badge)
│ Histórico│──────────────────────────────────────────────────────│
│ Calibração│            ✓ (grande, verde)                         │  ← StatusCard (calm)
│          │         Márcio está bem                               │
│          │   Última verificação: 08h12 · sincronizado agora      │
│          │        [ Ver histórico de hoje ] ───────────────► HIST│
└──────────┴──────────────────────────────────────────────────────┘
```

### Alternative — emergency pre-empts Home (owned by EVNT)

```text
activeEvent(emergencia, confirmadoEm=null)  ──►  EVNT AlarmOverlay mounts in
DASH global outlet (z: interrupt), full-bleed, above router-outlet.
DASH Home never renders "está bem" while activeEvent is an unconfirmed emergency.
```

### Alternative — routine notification (owned by EVNT, hosted by DASH)

```text
activeEvent(rotina)  ──►  EVNT toast renders in DASH shell outlet (discreet, dismissible).
DASH notification center appends the dispatch to its recent-list (review later).
```

### Edge cases

- **Device offline** — topbar badge turns amber (EDGE); Home **stays calm** (offline is
  not an alarm; alarm is reserved for `emergencia`).
- **Summary fetch fails** — StatusCard shows a clear non-technical error + retry; chrome
  stays intact.
- **Role = cuidadora** — sidebar hides `Onboarding` (admin/condutor técnico only).

## 4. Internal boundaries (lib structure — module-pattern / ADR-006)

```
libs/dash/shell-entry/       (type:app, scope:dash)   # importPath @neuroassist/dash (ADR-006)
└── src/lib/
    ├── dash.module.ts                 # único NgModule (lazy entry)
    ├── dash.contract.ts               # MODULE_KEY 'dash' + ModuleMenuMetadata + permissions + flags
    ├── dash.bootstrap.ts              # init/destroy lifecycle
    ├── dash.routes.ts                 # DASH_ROUTES[] (/ home, /entrada, /notificacoes)
    ├── dash.permission-map.ts         # ModulePermissionMap
    ├── dash.feature-flags.ts          # ModuleFeatureFlags (defaultState)
    ├── dash.nav.config.ts             # navegação/breadcrumb (Status = raiz)
    └── dash.descriptor.ts             # DASH_MODULE_DESCRIPTOR (consumido pelo Shell)

apps/dash/                   (type:app, scope:dash)   # host fino: app.routes → @neuroassist/dash
libs/dash/
├── access/          (type:domain)       # roleGuard (esconde Onboarding p/ cuidadora)
├── data-access/     (type:data-access)  # DashApiService + DashHomeFacade + NotificationsFacade (signals)
├── feature-home/    (type:feature)      # StatusCard + quick-link (Tela 01)
├── feature-entrada/ (type:feature)      # brand entry / CTA (Tela 00)
├── feature-notifications/ (type:feature)# notification center (bell/inbox)
├── integration/     (type:domain)       # DashShellIntegrationService (registro no Shell)
├── navigation/      (type:domain)       # breadcrumbs/menu state
├── ui/ (opcional)   (type:ui)           # StatusCard, DeviceStatusBadge (visual), NotificationListItem
└── styles/ (opcional) (type:ui)         # utilitários Tailwind + tokens (sem SCSS de tokens)

apps/dash-e2e/                           # Playwright
```

> **Shell composition note.** The **shell chrome** (sidebar/topbar layout + the global
> outlet host) is rendered by the app-level `neuroassist-ai-web` (`scope:web`), which is
> allowed to import every module. DASH **owns the design and contracts** of that chrome
> and provides `DASH_MODULE_DESCRIPTOR`; the actual `@nx/enforce-module-boundaries`-safe
> wiring of `activeEvent` (EVNT) and the device badge (EDGE) into the topbar/Home happens
> at `scope:web`, never inside `scope:dash` (ADR-005 / nx-boundaries.md).

## 5. Cross-cutting concerns

- **Auth / entitlement** — role read from `@neuroassist/shared/auth-session`; navigation
  gated via `@neuroassist/shared/access-decision` (Onboarding admin-only). No RBAC logic
  invented in DASH.
- **Global outlets** — DASH hosts EVNT's alarm/toast through the shared
  `ShellOutletRegistry` (`libs/shared/ui-components`); DASH declares the outlet region,
  EVNT registers the components (INT-FE-EVNT-001).
- **Observability** — panel-open / status-view traces via `@neuroassist/shared` observability.
- **Design tokens** — `@neuroassist/shared/ui-tokens`; the calm palette (green/sober) is
  the default; `--color-alarm` is never used by DASH (reserved to EVNT).
- **i18n** — human-facing strings pt-BR; keys via shared i18n; no technical codes surfaced.
