# 02 — Architecture Blueprint — EVNT (Frontend)

## 1. System boundary

EVNT-FE is a **client feature** of the backend `evnt-service` (via
`@neuroassist/shared/access-http-base` → API Gateway), always tenant-scoped by
the `X-Empresa-Id` interceptor (schema-per-empresa). It holds **no urgency logic
and no persistence**: it subscribes to the live event stream, renders the active
event by its (already-resolved) urgency, and posts the caregiver's confirmation.

The browser is a **delivery and confirmation surface**. It does not talk to the
edge agent directly (that is GEST/EDGE); it consumes the business `Evento` that
`evnt-service` produced from the edge's recognized-command signal.

## 2. External actors and systems

| Actor / system | Role | Channel |
|---|---|---|
| Caregiver / coordinator (authenticated) | Receives alarm, confirms attendance | Browser UI |
| API Gateway (GWY) → `evnt-service` | Delivers active/recent events, accepts confirmation | REST + live channel via `access-http-base` |
| ACS module | Caregiver ↔ patient vínculo (routing target) | shared session/state (composed at page) |
| EDGE module | Device online/offline state (gates delivery) | shared state (composed at shell page) |
| Shell (`neuroassist-ai-web`) | Hosts the global alarm outlet | `scope:web` composition |

## 3. End-to-end user flows

### Golden path — emergency alarm + confirmation (wireframe §4.2)

```
[caregiver anywhere on the panel]
        |
        v
   evnt-service delivers Evento (urgencia = emergencia)  --live channel-->
        |
        v
   EventDeliveryFacade.activeEvent := Evento
        |
   state: alarm-active
        v
   Shell AlarmOutlet renders full-bleed AlarmOverlay (--color-alarm)
   "Márcio precisa de ajuda" · "Gesto detectado às 14h32"
   [ Confirmar que atendi Márcio ]   (only action available)
        |
   caregiver presses confirm
        v
   POST /events/{id}/confirm  -> writes confirmadoEm + confirmadoPor
        |
   state: confirmed -> idle   (overlay dismissed)
```

### Routine notice (wireframe §4.6)

```
   evnt-service delivers Evento (urgencia = rotina)
        |
        v
   routine queue signal += Evento
   discreet RoutineNoticeComponent (banner/toast), non-blocking, dismissible
```

Alternative / edge cases:

```
live channel drops / unreachable   -> state: delivery-degraded
   fall back to polling GET /events/active; banner "Modo degradado —
   verificando a cada Xs"; no active alarm is lost

confirm request fails (network)     -> alarm stays active; inline retry
   "Não foi possível registrar. Tentar de novo." (never silently cleared)

concurrent confirm (clinic, 2 caregivers) -> first confirm wins;
   409/late confirm -> "Já confirmado por <nome> às <hora>"; overlay clears

device offline (EDGE)               -> no new events expected; Home shows the
   offline state (composed), EVNT idle
```

## 4. Internal boundaries (module slicing per module-pattern.md)

EVNT follows the canonical NeuroAssist module slicing. The entry is a
**library** (ADR-006), not an app folder. The emergency overlay is exposed as a
**shell-level global outlet**, because it must pre-empt any active route (ADR-005).

```
libs/evnt/shell-entry/        (type:app, scope:evnt)   # @neuroassist/evnt — lazy entry
└── src/lib/
    ├── evnt.module.ts                 # the module's only NgModule
    ├── evnt.contract.ts               # MODULE_KEY + metadata + permissions + flags
    ├── evnt.bootstrap.ts              # init/destroy (live channel subscribe/teardown)
    ├── evnt.routes.ts                 # EVNT_ROUTES[] (routine feed / event detail)
    ├── evnt.permission-map.ts
    ├── evnt.feature-flags.ts          # e.g. liveChannel (SSE) on/off, pollIntervalMs
    ├── evnt.nav.config.ts
    └── evnt.descriptor.ts             # EVNT_MODULE_DESCRIPTOR (consumed by Shell)

apps/evnt/                    (type:app, scope:evnt)   # thin host: loads @neuroassist/evnt
apps/evnt-e2e/                (type:e2e)               # Playwright specs

libs/evnt/
├── data-access/  (type:data-access) # EvntClient (REST + live channel) + EventDeliveryFacade (signals)
├── feature-alarm/ (type:feature)    # AlarmOverlayComponent (full-bleed emergency) + confirm
├── feature-routine/ (type:feature)  # RoutineNoticeComponent (discreet notification)
├── access/       (type:domain)      # evnt-specific guard (caregiver linked to patient)
├── integration/  (type:domain)      # EvntShellIntegrationService (registers AlarmOutlet)
├── navigation/   (type:domain)      # breadcrumb/menu state
├── ui/ (opcional, type:ui)          # local presentational bits (UrgencyBadge, EventTimestamp)
└── styles/ (opcional)               # Tailwind utilities + tokens (no SCSS tokens)
```

**Composition rule (critical).** The emergency alarm cannot live inside a single
route, or a caregiver on `/calibracao` would never see it. The
`EvntShellIntegrationService` registers a global **AlarmOutlet** rendered by the
Shell (`scope:web`) above the router outlet; it reads the `activeEvent` signal
from the EVNT facade (exported by `@neuroassist/evnt`). `scope:web` is the only
scope allowed to depend on every module (nx-boundaries.md). Home/DASH consume the
same `activeEvent` signal at page-level composition to answer "Márcio está bem?".

## 5. Cross-cutting concerns

- **Auth / entitlement** — routine routes guarded by shared `AccessDecisionService`;
  an evnt-local guard additionally requires the caregiver to be linked to the
  patient's tenant (ACS vínculo).
- **Tenancy** — every backend call carries `X-Empresa-Id` via the shared
  `access-http-base` interceptor (schema-per-empresa); routing never crosses tenants.
- **Observability** — event delivered, alarm shown, confirmation posted, and
  degraded-mode transitions emitted via the shared observability lib (no PII beyond
  ids; no clinical content).
- **Design tokens** — `--color-alarm`, `success`, `danger`, `brand` from
  `@neuroassist/shared/ui-tokens`; the full-bleed alarm treatment is token-driven,
  never ad-hoc colors.
- **i18n** — UI copy pt-BR (human-facing, e.g. "Márcio precisa de ajuda");
  code/identifiers en-US.
- **Accessibility / safety** — the alarm uses `role="alertdialog"`, moves focus to
  the confirm action, and is dismissible only by confirmation; high-contrast,
  large target for the single button.
