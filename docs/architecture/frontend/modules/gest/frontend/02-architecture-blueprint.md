# 02 — Architecture Blueprint — GEST (Frontend)

## 1. System boundary

GEST-FE is a **client feature** of two things:

1. the **local Edge Agent** (same-device, source of real recognition signals for
   the live test), reached via a localhost bridge (ADR-005); and
2. the **backend** (via `@neuroassist/shared/access-http-base` → API Gateway) for
   any command-sync / persistence side-effects, always tenant-scoped by the
   `X-Empresa-Id` interceptor.

The browser holds **no detection logic**. It captures the webcam locally
(`getUserMedia`) for the operator's visual confirmation, and it *displays* the
recognition verdict produced by the edge engine.

## 2. External actors and systems

| Actor / system | Role | Channel |
|---|---|---|
| Caregiver / conductor (authenticated) | Runs the live test | Browser UI |
| Browser MediaDevices | Local webcam preview | `navigator.mediaDevices.getUserMedia` |
| Local Edge Agent (GEST runtime) | Produces recognition signals | localhost WS/SSE (ADR-005), mock fallback |
| API Gateway (GWY) | Command sync / persist | REST via `access-http-base` |
| EDGE module | Device pairing + online status | shared session/state (composed at page) |

## 3. End-to-end user flows

### Golden path — live-test a command (inside calibração)

```
[Command editor open]
   caregiver fills rótulo / gesto / sensibilidade
        |
        v
   presses "Testar agora"  --------------------> requestCamera()
        |                                          |
   state: testing                                  getUserMedia({video:true})
   <video> preview shows the person                |
        |                                          v
        |                             GEST facade subscribes to edge stream
        |                             (localhost bridge; command context sent)
        v                                          |
   Márcio performs the gesture                     |
        |                                          v
        |                             edge agent emits { recognized, primitivo, ... }
        v                                          |
   state: recognized  <----------------------------+
   "Gesto reconhecido com sucesso!"  (camera stopped)
```

Alternative / edge cases:

```
camera permission denied / no device   -> state: camera-error
   "Não foi possível acessar a webcam. Verifique a permissão…"  [Tentar de novo]

edge agent unreachable / offline device -> degraded mode (ADR-005 fallback)
   preview works, verdict = simulated/manual; banner: "Dispositivo offline —
   teste sem reconhecimento real"

composite gesture (2 primitives)        -> per-step feedback
   "Faça o primeiro gesto…" -> "agora o segundo…" -> recognized|not-recognized

timeout (no recognition within window)  -> state: not-recognized
   "Não reconhecido — repita o gesto ou aumente a sensibilidade"
```

## 4. Internal boundaries (module slicing per module-pattern.md)

GEST follows the canonical NeuroAssist module slicing. The entry is a **library**
(ADR-006), not an app folder.

```
libs/gest/shell-entry/        (type:app, scope:gest)   # @neuroassist/gest — lazy entry
└── src/lib/
    ├── gest.module.ts                 # the module's only NgModule
    ├── gest.contract.ts               # MODULE_KEY + metadata + permissions + flags
    ├── gest.bootstrap.ts              # init/destroy (edge bridge lifecycle)
    ├── gest.routes.ts                 # GEST_ROUTES[]
    ├── gest.permission-map.ts
    ├── gest.feature-flags.ts          # e.g. edgeLiveBridge on/off
    ├── gest.nav.config.ts
    └── gest.descriptor.ts             # GEST_MODULE_DESCRIPTOR (consumed by Shell)

apps/gest/                    (type:app, scope:gest)   # thin host: loads @neuroassist/gest
apps/gest-e2e/                (type:e2e)               # Playwright specs

libs/gest/
├── data-access/  (type:data-access) # EdgeDetectionClient + GestureTestFacade (signals)
├── feature-live-test/ (type:feature) # GestureLiveTestComponent (webcam preview + verdict)
├── access/       (type:domain)       # gest-specific guard (needs paired online device)
├── integration/  (type:domain)       # GestShellIntegrationService
├── navigation/   (type:domain)       # breadcrumb/menu state
├── ui/ (opcional, type:ui)           # local presentational bits (StateBadge, PreviewFrame)
└── styles/ (opcional)                # Tailwind utilities + tokens (no SCSS tokens)
```

**Composition rule (critical).** `scope:pac` (calibração) and `scope:acs`/onboarding
**must not** import `scope:gest` directly — that is a hard nx-boundaries violation.
The `GestureLiveTestComponent` is composed into the calibração / onboarding
experiences at the **`scope:web` shell-page level** (the only scope allowed to
depend on every module) or surfaced through a shared slot. This is why the
prototype's single `calibracao` screen decomposes, in production, into a PAC
command-editor page that hosts the GEST live-test feature via page-level
composition. See ADR-006 and doc 04 §5.

## 5. Cross-cutting concerns

- **Auth / entitlement** — route guarded by shared `AccessDecisionService`; a
  gest-local guard additionally requires a paired, online edge device.
- **Tenancy** — every backend call carries `X-Empresa-Id` via the shared
  `access-http-base` interceptor (schema-per-empresa).
- **Observability** — test attempts, outcomes, and camera/edge errors emitted via
  the shared observability lib (no video, no PII).
- **Design tokens** — colors/typography from `@neuroassist/shared/ui-tokens`
  (`--color-alarm`, `brand`, `success`, `danger`); no ad-hoc colors.
- **i18n** — UI copy pt-BR (human-facing); code/identifiers en-US.
- **Privacy** — camera active only during an explicit test; stream stopped on
  stop / navigate / destroy; no frame persisted or uploaded.
