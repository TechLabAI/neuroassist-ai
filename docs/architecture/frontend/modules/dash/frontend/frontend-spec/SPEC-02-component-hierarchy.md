# SPEC-02 — Component Hierarchy — DASH (Frontend)

> Sources: FE 02 §4, FE 04 §3/§6, FE 05 §1. Standalone components, OnPush everywhere, Signals-first.
> Smart components consume facades (signals); presentational components receive `input()` and emit `output()`.
> This SPEC is prose + trees — **no EARS section** (per command: component trees stay prose).

## Conventions

- **Smart** (page/container) components inject facades and read signals; they never take `@Input` domain data.
- **Presentational** (`libs/dash/ui`) components are pure: `input()` in, `output()` out, no HTTP, no facade.
- Each smart component below declares the **signals it reads** explicitly.

## feature-entrada (`libs/dash/feature-entrada`, `type:feature`)

```text
EntradaPageComponent (smart, chromeless)
  reads signals: authSession.isAuthenticated()
├── BrandHeroComponent            (ui) — logo + "Um gesto que você não vai perder."
└── EnterPanelCtaComponent        (ui)
      output: enter → EntradaPageComponent routes to '/' (session already valid) or triggers login
```

## feature-home (`libs/dash/feature-home`, `type:feature`)

```text
HomeStatusPageComponent (smart)
  injects: DashHomeFacade
  reads signals: home.summary(), home.state(), home.calm()   // calm is derived (computed)
├── StatusCardComponent           (ui)
│     input: vm: HomeStatusCardVm   (calm | loading | error)
│     output: retry → DashHomeFacade.reloadSummary()
│     output: viewHistory → shell router → HIST (cross-module link)
├── PatientHeaderComponent        (ui)   // active patient name + device badge slot
│     input: patient: PatientHeaderVm
│     input: device: DeviceBadgeVm      // injected at shell from EDGE (ADR-005), passed down
└── QuickLinkListComponent        (ui)
      input: links: QuickLinkVm[]       // "Ver histórico de hoje" → HIST
      output: navigate → shell router
```

> `StatusCardComponent` renders the calm ✓/green by default; it **never** renders alarm styling. The
> emergency full-screen alarm is EVNT's `AlarmOverlayComponent` mounted in the global shell outlet — outside
> this tree (FE 02 §5, ADR-005). Home shows "está bem" **only while** `home.calm()` is true.

## feature-notifications (`libs/dash/feature-notifications`, `type:feature`)

```text
NotificationCenterPageComponent (smart)
  injects: NotificationsFacade
  reads signals: notifications.items(), notifications.state(), notifications.unreadCount()
├── NotificationFilterBarComponent (ui)
│     input: filter: NotificationFilterVm
│     output: filterChange → NotificationsFacade.setFilter()
├── NotificationListComponent      (ui)
│     input: items: NotificationItemVm[]
│     output: markRead → NotificationsFacade.markRead(dispatchId)
│     output: open → NotificationsFacade / shell router (patient context)
│     └── NotificationListItemComponent (ui)   // repeated; tier badge (discreet), read state, timestamp
└── EmptyOrErrorStateComponent     (ui)         // inline empty/error (never blocks Home)
```

## Shared / shell-hosted (owned elsewhere, composed at `scope:web`)

```text
AppShellComponent (neuroassist-ai-web, scope:web)  — NOT a DASH lib
├── SidebarComponent            // Status(root) / Histórico / Calibração / [Onboarding gated]
├── TopbarComponent
│     └── DeviceStatusBadge      // fed by EDGE facade at shell → passed into DASH PatientHeader
├── <router-outlet>             // DASH routes render here
└── ShellOutletRegion (global outlet, z:interrupt)
      └── EVNT AlarmOverlayComponent / toast  // registered by EVNT via ShellOutletRegistry (INT-FE-EVNT-001)
```

> `scope:dash` imports only `scope:dash` + `scope:shared`. It **never** imports `scope:evnt`/`scope:edge`/
> `scope:hist`. `activeEvent` (EVNT) and the device badge (EDGE) are injected into DASH at `scope:web` via
> tokens/inputs (ADR-005, nx-boundaries.md).
