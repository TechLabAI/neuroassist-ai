# plan-FE-EDGE — EDGE Frontend Execution Plan

> Module: **EDGE — Integração de Dispositivo Edge**
> Lib: `@neuroassist/edge` · Service: `edge-service`
> Generated: 2026-07-29 · Phase: fe-backlog (M5) · Pipeline: architecture-fullstack

---

## 1. Project Summary

EDGE-FE is the NeuroAssist AI **connectivity conscience**: it pairs the local edge agent to a patient
and thereafter provides an always-visible, freshness-derived truth about whether the device is
actually watching. Its two jobs — onboarding pairing and honest connectivity monitoring — are
inseparable safety concerns in a healthcare assistive platform. A falsely-green connectivity chip
is a safety defect (doc 01 §2), not a UX annoyance.

The implementation follows the ADR-005 freshness-derived state pattern: `online | stale | offline |
unknown` are computed from `serverTime − ultimaSincronizacao` against runtime-tunable thresholds
(`stalenessThresholdMs`, `offlineWindowMs`) — never from a persisted `status` flag. `unknown ≠ online`
is an invariant enforced at every layer. The module is composed into the Shell (topbar chip + onboarding
step 3) rather than occupying a top-level nav slot (`showInNav:false`); it is reachable from the chip
link at `/edge/device`.

Architecture: Nx monorepo, Angular standalone components, Signals-first (`computed()`/`signal()`, no
NgRx), lazy-loaded lib (`@neuroassist/edge`), OnPush everywhere. Nx boundary enforced: `scope:edge`
imports only `scope:edge` or `scope:shared`; the Shell composes at `scope:web` via read-model port.

Total backlog: **27 functional issues + 6 integration issues = 33 issues** across 9 waves (~16.5 dev-days).

---

## 2. Wave Map (P1–P9)

### P1 — Foundation (2 dev-days)

**Goal:** Establish module contracts, routes, descriptor, and bootstrap. The shell must be able to
register EDGE without importing any internals.

| Issue | Title | Lib | Size | Blocker for |
|---|---|---|---|---|
| FE-EDGE-001 | `edge.contract.ts` — MODULE_KEY, menu, permission map, feature flags | `libs/edge/shell-entry` | S | 002, 003, 015, INT-004 |
| FE-EDGE-002 | `edge.routes.ts` + `edge.module.ts` — EDGE_ROUTES, EdgeModule, thin host | `libs/edge/shell-entry` + `apps/edge` | M | 003, 004 |
| FE-EDGE-003 | `EDGE_MODULE_DESCRIPTOR` — composition slots (`connectivityReadModel` + `pairingComponent`) | `libs/edge/shell-entry` | M | INT-001 |
| FE-EDGE-004 | `edge.bootstrap.ts` + `apps/edge` AppComponent | `libs/edge/shell-entry` + `apps/edge` | S | e2e-025 |

**DoD P1:**
- `nx lint edge-shell-entry` and `nx build edge-shell-entry` pass
- `MODULE_KEY = 'edge'`, `showInNav = false`, threshold invariant (`stalenessThresholdMs < offlineWindowMs`) all asserted in unit tests
- `EDGE_MODULE_DESCRIPTOR` exposes `connectivityReadModel` slot (typed interface, not `any`)
- Apps/edge thin host loads `@neuroassist/edge` and renders with no console errors

---

### P2 — Data Access (3 dev-days)

**Goal:** HTTP service + two facades cover the full read/write surface against `edge-service`. The
`deriveState()` pure function captures the 5-state invariant in isolation.

| Issue | Title | Lib | Size | Blocker for |
|---|---|---|---|---|
| FE-EDGE-005 | Shared contracts DTOs (`DispositivoEdgeView`, `RegisterDeviceResult`, `LivenessView`, VMs) | `libs/shared/contracts` | M | 006, 007, 008 |
| FE-EDGE-006 | `EdgeHttpService` — all 8 HTTP methods + ETag/304 | `libs/edge/data-access` | M | 007, 008, 019 |
| FE-EDGE-007 | `DeviceFacade` — signals + poll timer + `deriveState()` server-relative age | `libs/edge/data-access` | L | 004, 012, 014, 015, INT-001 |
| FE-EDGE-008 | `PairingFacade` — register→claim→first-heartbeat flow (no-premature-paired) | `libs/edge/data-access` | L | 011 |

**DoD P2:**
- `derive-state.spec.ts`: all 6 state-transition cases pass (`not-paired`, `offline-never-reported`, `unknown`, `online`, `stale`, `offline`)
- `_pollOk=false` never produces `online` (honesty invariant asserted)
- ETag/304: `_device` unchanged, `_clock` ticked, `_pollOk=true`
- `PairingFacade.register()` does not advance to `paired` until first heartbeat confirmed
- `nx test edge-data-access` and `nx lint edge-data-access` pass

---

### P3 — Feature Libs (3 dev-days)

**Goal:** All page and card components. Pairing flow and device-detail view are the two product
surfaces. Smart containers own state; presentational components are pure.

**Pairing sub-wave:**

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-009 | `PairingCodeCardComponent` — token display, TTL countdown, copy | `libs/edge/feature-pairing` | S |
| FE-EDGE-010 | `PairingProgressComponent` + `PairingResultComponent` — step indicator + outcome | `libs/edge/feature-pairing` | S |
| FE-EDGE-011 | `DevicePairingPageComponent` — smart container (register/pair/reset/retry) | `libs/edge/feature-pairing` | M |

**Device detail sub-wave:**

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-012 | `ConnectivityStatusCardComponent` — derived state, accessible (color+text+icon) | `libs/edge/feature-device-detail` | S |
| FE-EDGE-013 | `DeviceMetaListComponent` + `DeviceActionsComponent` — metadata + conductor-gated actions | `libs/edge/feature-device-detail` | M |
| FE-EDGE-014 | `DeviceDetailPageComponent` — smart container (polling lifecycle, confirm dialogs) | `libs/edge/feature-device-detail` | M |

**DoD P3:**
- Pairing wizard: `PairingCodeCardComponent` countdown reaches 0 and emits `expired` event; `PairingResultComponent` shows success/failure state
- `DevicePairingPageComponent` calls `PairingFacade.register()` then polls with `PairingFacade.waitForFirstHeartbeat()` — never transitions to "paired" on claim alone
- `ConnectivityStatusCardComponent`: all 5 derived states render distinct color+text+icon (verified via unit test)
- `DeviceActionsComponent`: hidden (not disabled) when user lacks `edge.device.manage`
- `nx test edge-feature-pairing edge-feature-device-detail` pass

---

### P4 — Hub Integrations (2.5 dev-days)

**Goal:** Wire EDGE into the Shell's topbar chip, onboarding step 3, and the tool-descriptors registry.

| Issue | Title | Lib | Size |
|---|---|---|---|
| INT-FE-EDGE-001 | `EdgeShellIntegrationService` — `registerWithShell`, read-model, pairingSlot | `libs/edge/integration` | M |
| INT-FE-EDGE-002 | Shell topbar chip wiring (`scope:web`) — bind read-model + nx-boundary | `apps/web` | S |
| INT-FE-EDGE-003 | Onboarding step-3 pairing slot (`scope:web`) — `NgComponentOutlet` + `[pacienteHint]` | `apps/web` | M |
| INT-FE-EDGE-004 | `tool-descriptors.ts` static fallback — EDGE entry, `showInNav:false` | `apps/web` | S |
| INT-FE-EDGE-005 | Shared contracts barrel verification — no circular dep | `libs/shared/contracts` | S |
| INT-FE-EDGE-006 | Shared error-model EDGE pt-BR copy (12 error types) | `libs/edge/data-access` | M |

**DoD P4:**
- Shell topbar chip renders connectivity state from `EDGE_MODULE_DESCRIPTOR.connectivityReadModel` (no `scope:edge` import in `scope:web`)
- Onboarding step 3 mounts `DevicePairingPageComponent` via `NgComponentOutlet` — no direct component import in `apps/web`
- `nx lint web` clean (no nx-boundary violations from `apps/web → scope:edge`)
- All 12 pt-BR error messages present and matched to SPEC-07 error-kind table

---

### P5 — Access Guards (0.5 dev-day)

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-015 | `edgeConductorGuard` + `authGuard` `/edge` route wiring | `libs/edge/access` | S |

**DoD P5:**
- `edgeConductorGuard` redirects to `/edge/device` (read-only detail) when session lacks `edge.device.manage`
- `authGuard` blocks unauthenticated access to all `/edge/*` routes
- `nx test edge-access` pass

---

### P6 — Navigation (0.5 dev-day)

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-016 | `libs/edge/navigation` — breadcrumb state (3 paths; silent in composed context) | `libs/edge/navigation` | S |

**DoD P6:**
- Breadcrumbs produce 3 paths: `/edge/device` (detail), `/edge/pairing` (pair/manage), `/edge/device/history` (reserved)
- Composed chip context: breadcrumb state is silent (no breadcrumb emitted when navigated from topbar chip)
- `nx test edge-navigation` pass

---

### P7 — UX Polish (1.5 dev-days)

**Goal:** Loading states, empty states, and G6 retry-backoff closure (HIGH PRIORITY).

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-017 | `ConnectivityChipComponent` + `RelativeTimeComponent` (`libs/edge/ui`) | `libs/edge/ui` | M |
| FE-EDGE-018 | Loading skeleton + empty states (not-paired caregiver path, device-not-found) | `libs/edge/feature-device-detail` | M |
| FE-EDGE-019 | G6 retry-backoff tests — 2-attempt + toast for register/pair/unpair/retire | `libs/edge/data-access` | M |

**DoD P7:**
- `ConnectivityChipComponent`: all 5 states render distinct chip variants; `RelativeTimeComponent` formats time relative strings in pt-BR
- Loading skeleton: renders during initial `DeviceFacade.load()` (no flash of empty)
- Not-paired caregiver path: informational state renders (no management affordance)
- G6 CLOSED: all 4 action operations (`register`, `pairToPatient`, `unpair`, `retire`) have 2-attempt retry coverage; liveness poll confirmed to NOT toast on failure
- `nx test edge-data-access edge-ui edge-feature-device-detail` pass

---

### P8 — Observability (0.5 dev-day)

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-020 | Telemetry events — pairing completed, offline, poll failure, retire | `libs/edge/data-access` | S |

**DoD P8:**
- 4 telemetry events emitted via `@neuroassist/shared/observability` (or hub equivalent):
  - `edge.device.paired` (pairing completed)
  - `edge.device.offline` (derived state → offline)
  - `edge.device.poll_failure` (poll error)
  - `edge.device.retired` (retire action completed)
- Unit: spy on observability service; verify event shape (no PII in payload)
- `nx test edge-data-access` pass

---

### P9 — E2E + Hardening (3 dev-days)

**Goal:** Playwright specs for all 8 `.feature` files. A11y + perf budget checked in FE-EDGE-026.
EARS gap closure (G1-G5 + SPEC-07 device-retired clause) via FE-EDGE-027.

| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-021 | Playwright — `pairing-happy-path.feature` (SPEC-08 #1) | `apps/edge-e2e` | M |
| FE-EDGE-022 | Playwright — `pairing-code-expired` + `pairing-claim-no-heartbeat` (#2, #3) | `apps/edge-e2e` | M |
| FE-EDGE-023 | Playwright — `connectivity-derived-freshness` + `poll-failure-unknown` (#4, #5) | `apps/edge-e2e` | M |
| FE-EDGE-024 | Playwright — `device-repair-unpair` (#6): 409 refresh, caregiver RBAC | `apps/edge-e2e` | M |
| FE-EDGE-025 | Playwright — `shell-chip-composition` (#7): topbar, bootstrap init/destroy | `apps/edge-e2e` | M |
| FE-EDGE-026 | Playwright — `backend-error-handling` + a11y + perf budget (#8) | `apps/edge-e2e` | L |
| FE-EDGE-027 | EARS gap closure — G1-G5 + SPEC-07 device-retired clause | `libs/edge/data-access` | M |

**DoD P9:**
- All 8 `.feature` files have a corresponding Playwright spec that passes
- 26 e2e scenarios all green in `nx e2e edge-e2e`
- `axe-core` scan: 0 violations on `DeviceDetailPage` + `DevicePairingPage`
- Perf budget: Lighthouse FCP ≤ 1.5 s on cold load of `/edge/device`
- EARS gaps G1-G5 closed (explicit unit assertions added to existing specs)
- SPEC-07 `/errors/device-retired` clause: EARS assertion added and unit-tested

---

## 3. Critical Path

```
FE-EDGE-001 (contract)
  └─► FE-EDGE-002 (routes + module)
        └─► FE-EDGE-003 (descriptor + slots)
              └─► INT-FE-EDGE-001 (EdgeShellIntegrationService)
                    ├─► INT-FE-EDGE-002 (topbar chip wiring)        ──► FE-EDGE-025 (e2e chip)
                    └─► INT-FE-EDGE-003 (onboarding slot)

FE-EDGE-005 (DTOs)
  └─► FE-EDGE-006 (EdgeHttpService)
        ├─► FE-EDGE-007 (DeviceFacade — deriveState + poll)
        │     ├─► FE-EDGE-004 (bootstrap — startPolling wired)
        │     └─► FE-EDGE-014 (DeviceDetailPage)
        │           └─► FE-EDGE-024 (e2e device-repair-unpair)
        └─► FE-EDGE-008 (PairingFacade)
              └─► FE-EDGE-011 (DevicePairingPage)
                    └─► FE-EDGE-021 (e2e pairing-happy-path)
                          └─► FE-EDGE-022 / FE-EDGE-023 / FE-EDGE-025 (e2e variants)

FE-EDGE-017 (ConnectivityChipComponent)
  └─► INT-FE-EDGE-002 (topbar chip wiring)
        └─► FE-EDGE-025 (e2e shell-chip-composition)
```

**Longest chain (6 levels):**
`FE-EDGE-001 → 002 → 003 → INT-FE-EDGE-001 → INT-FE-EDGE-002 → FE-EDGE-025`

**Implication:** P1 issues must be unblocked on Day 1. Any slip in FE-EDGE-001 or FE-EDGE-005
propagates through the entire e2e suite.

---

## 4. Top-3 Risks

### R1 — Safety: false-green connectivity (doc 06 §1, HIGH)

The worst outcome is a device shown `online` while the agent has silently stopped. Mitigated by the
freshness-derived `deriveState()` pattern (ADR-005) and the threshold invariant (`stale < offline`),
but the **threshold values are live safety parameters** that must be validated with the pilot family.
`stalenessThresholdMs=45000` and `offlineWindowMs=90000` are conservative defaults; if the edge agent's
real heartbeat cadence differs from the assumed 30s, both values must be retuned before production.

**Mitigation in backlog:** FE-EDGE-001 exposes flags as `const` objects (runtime-overridable);
FE-EDGE-007 asserts the `unknown ≠ online` invariant in 6 unit cases; FE-EDGE-019 adds retry-backoff
tests so network noise never silently corrupts derived state.

### R2 — Blocking: `edge-service` API contract stability (doc 06 §2, HIGH)

EDGE-FE consumes `POST /devices/pairing-codes`, `POST /devices/pairing-codes/{code}/claim`,
`GET /patients/{id}/liveness`, `DELETE .../pairings/current`, and `POST .../:retire`. These endpoints
were specified in the BE api-contract (M2) but `edge-service` is not yet generated. Any endpoint
shape change (path, envelope, ETag semantics, TTL values) requires a corresponding update in
`EdgeHttpService` (FE-EDGE-006) and the DTOs (FE-EDGE-005).

**Mitigation:** FE-EDGE-006 uses typed DTOs and a centralized `ApiPath` constant file so endpoint
changes are a single-file update. Mock adapters in tests use the `DispositivoEdgeView` / `LivenessView`
shapes from `libs/shared/contracts`.

### R3 — Nx boundary discipline: Shell ↔ EDGE composition (doc 06 §4, MEDIUM)

The topbar chip and onboarding slot are in `apps/web` (`scope:web`); `DevicePairingPageComponent`
and `DeviceFacade` are in `scope:edge`. If any `scope:web` file imports `scope:edge` internals
directly, nx-boundary enforcement will fail and the composition pattern collapses.

**Mitigation:** `EDGE_MODULE_DESCRIPTOR.pairingComponent` carries the component reference as an
opaque token (`Type<unknown>`); `apps/web` mounts it via `NgComponentOutlet`. INT-FE-EDGE-001
through INT-FE-EDGE-004 are all `scope:web` issues and have explicit `nx lint web` gates in their
acceptance criteria.

---

## 5. Definition of Done by Wave

| Wave | Gates |
|---|---|
| P1 Foundation | `nx lint edge-shell-entry` PASS · `MODULE_KEY='edge'` unit · threshold invariant unit |
| P2 Data Access | All 6 `derive-state` cases unit · `unknown ≠ online` invariant · ETag/304 test · `nx test edge-data-access` PASS |
| P3 Feature Libs | 5-state chip render unit · PairingPage no-premature-paired · `nx test edge-feature-pairing edge-feature-device-detail` PASS |
| P4 Hub Integrations | `nx lint web` PASS (0 boundary violations) · 12 pt-BR error copies present · chip renders from read-model port |
| P5 Guards | `edgeConductorGuard` redirect test · `nx test edge-access` PASS |
| P6 Navigation | 3 breadcrumb paths + silent-in-chip test · `nx test edge-navigation` PASS |
| P7 UX Polish | G6 closed (6 retry specs) · loading skeleton renders · not-paired caregiver state renders |
| P8 Observability | 4 telemetry events emitted with correct shape · no PII in payload |
| P9 E2E + Hardening | All 26 e2e scenarios PASS · axe 0 violations · Lighthouse FCP ≤ 1.5s · G1-G5 + SPEC-07 gap closed |

---

## 6. Approval Criteria by Milestone

### Milestone A: P1 + P2 done → Data layer complete

- `edge.contract.ts` and `EdgeHttpService` committed and reviewed
- `derive-state.spec.ts` all 6 cases green
- `nx build @neuroassist/edge` with no errors
- Architecture doc `SPEC-04` cross-link verified (endpoint paths match BE api-contract §4)

### Milestone B: P3 done → MVP shippable in dev

- All 6 page/card components renderable in isolation via Storybook or test harness
- Pairing flow end-to-end manually verified in dev environment against mock `edge-service`
- `DeviceDetailPage` renders all 5 derived states without console errors
- Code review gate: `showInNav:false`, `OnPush`, standalone, Signals-only (no `BehaviorSubject`)

### Milestone C: P4 done → Shell integration complete

- Shell topbar chip visible in `apps/web` with live connectivity state
- Onboarding step 3 mounts pairing flow with correct `[pacienteHint]` binding
- `nx lint web` clean
- RBAC: caregiver cannot reach `/edge/pairing`; management actions hidden (not disabled)

### Milestone D: P5–P8 done → Feature-complete

- G6 retry-backoff verified (6 test cases + `ToastService` assertion)
- 4 telemetry events verified (no PII)
- All guards, breadcrumbs, loading skeletons in place
- UX walkthrough with pilot persona (Jamilly) completed

### Milestone E: P9 done → Shippable

- All 26 e2e scenarios PASS in CI (`nx e2e edge-e2e`)
- `axe-core` 0 violations
- Lighthouse FCP ≤ 1.5 s
- EARS G1-G5 + SPEC-07 device-retired clause unit-tested and closed
- Sign-off from tech conductor (Danilo) on pairing flow + connectivity chip UX

---

## 7. Issue Reference

Full issue index: `docs/architecture/frontend/modules/edge/frontend/issues/issues_map.md`

Individual issues:
- Functional: `FE-EDGE-001.md` … `FE-EDGE-027.md`
- Integration: `INT-FE-EDGE-001.md` … `INT-FE-EDGE-006.md`

Next phase: `/fe-architect-scaffold neuroassist-ai EDGE`
