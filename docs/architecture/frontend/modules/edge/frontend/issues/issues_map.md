# EDGE Frontend — Issues Map

> Module: **EDGE — Integração de Dispositivo Edge** · Lib: `@neuroassist/edge`
> Generated: 2026-07-29 · Pipeline phase: fe-backlog (M5)

## Summary

| Category | Count |
|---|---|
| Functional issues (FE-EDGE) | 27 |
| Integration issues (INT-FE-EDGE) | 6 |
| **Total** | **33** |

---

## By Priority Wave

### P1 — Foundation
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-001 | edge.contract.ts — MODULE_KEY, menu, permission map, feature flags | `libs/edge/shell-entry` | S |
| FE-EDGE-002 | edge.routes.ts + edge.module.ts — EDGE_ROUTES, EdgeModule, thin host | `libs/edge/shell-entry` + `apps/edge` | M |
| FE-EDGE-003 | EDGE_MODULE_DESCRIPTOR — composition slots (connectivityReadModel + pairingComponent) | `libs/edge/shell-entry` | M |
| FE-EDGE-004 | edge.bootstrap.ts + apps/edge AppComponent | `libs/edge/shell-entry` + `apps/edge` | S |

### P2 — Data Access
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-005 | Shared contracts DTOs (DispositivoEdgeView, RegisterDeviceResult, LivenessView, VMs) | `libs/shared/contracts` | M |
| FE-EDGE-006 | EdgeHttpService — all 8 HTTP methods + ETag/304 | `libs/edge/data-access` | M |
| FE-EDGE-007 | DeviceFacade — signals + poll timer + deriveState() server-relative age | `libs/edge/data-access` | L |
| FE-EDGE-008 | PairingFacade — register→claim→first-heartbeat flow (no-premature-paired) | `libs/edge/data-access` | L |

### P3 — Feature Libs (Pairing)
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-009 | PairingCodeCardComponent — token display, TTL countdown, copy | `libs/edge/feature-pairing` | S |
| FE-EDGE-010 | PairingProgressComponent + PairingResultComponent — step indicator + outcome | `libs/edge/feature-pairing` | S |
| FE-EDGE-011 | DevicePairingPageComponent — smart container (register/pair/reset/retry) | `libs/edge/feature-pairing` | M |

### P3 — Feature Libs (Device Detail)
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-012 | ConnectivityStatusCardComponent — derived state, accessible (color+text+icon) | `libs/edge/feature-device-detail` | S |
| FE-EDGE-013 | DeviceMetaListComponent + DeviceActionsComponent — metadata + conductor-gated actions | `libs/edge/feature-device-detail` | M |
| FE-EDGE-014 | DeviceDetailPageComponent — smart container (polling lifecycle, confirm dialogs) | `libs/edge/feature-device-detail` | M |

### P4 — Hub Integrations
| Issue | Title | Lib | Size |
|---|---|---|---|
| INT-FE-EDGE-001 | EdgeShellIntegrationService — registerWithShell, readModel, pairingSlot | `libs/edge/integration` | M |
| INT-FE-EDGE-002 | Shell topbar chip wiring (scope:web) — bind read-model + nx-boundary | `apps/web` | S |
| INT-FE-EDGE-003 | Onboarding step-3 pairing slot (scope:web) — NgComponentOutlet + [pacienteHint] | `apps/web` | M |
| INT-FE-EDGE-004 | tool-descriptors.ts static fallback — EDGE entry, showInNav:false | `apps/web` | S |
| INT-FE-EDGE-005 | Shared contracts barrel verification — no circular dep | `libs/shared/contracts` | S |
| INT-FE-EDGE-006 | Shared error-model EDGE pt-BR copy (12 error types) | `libs/edge/data-access` | M |

### P5 — Access Guards
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-015 | edgeConductorGuard + authGuard /edge route wiring | `libs/edge/access` | S |

### P6 — Navigation
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-016 | libs/edge/navigation — breadcrumb state (3 paths; silent in composed context) | `libs/edge/navigation` | S |

### P7 — UX Polish
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-017 | ConnectivityChipComponent + RelativeTimeComponent (libs/edge/ui) | `libs/edge/ui` | M |
| FE-EDGE-018 | Loading skeleton + empty states (not-paired caregiver path, device-not-found) | `libs/edge/feature-device-detail` | M |
| FE-EDGE-019 | G6 retry-backoff tests — 2-attempt + toast for register/pair/unpair/retire | `libs/edge/data-access` | M |

### P8 — Observability
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-020 | Telemetry events — pairing completed, offline, poll failure, retire | `libs/edge/data-access` | S |

### P9 — E2E + Hardening
| Issue | Title | Lib | Size |
|---|---|---|---|
| FE-EDGE-021 | Playwright — pairing-happy-path.feature (SPEC-08 #1) | `apps/edge-e2e` | M |
| FE-EDGE-022 | Playwright — pairing-code-expired + pairing-claim-no-heartbeat (#2, #3) | `apps/edge-e2e` | M |
| FE-EDGE-023 | Playwright — connectivity-derived-freshness + poll-failure-unknown (#4, #5) | `apps/edge-e2e` | M |
| FE-EDGE-024 | Playwright — device-repair-unpair (#6): 409 refresh, caregiver RBAC | `apps/edge-e2e` | M |
| FE-EDGE-025 | Playwright — shell-chip-composition (#7): topbar, bootstrap init/destroy | `apps/edge-e2e` | M |
| FE-EDGE-026 | Playwright — backend-error-handling + a11y + perf budget (#8) | `apps/edge-e2e` | L |
| FE-EDGE-027 | EARS gap closure — G1-G5 + SPEC-07 device-retired clause | `libs/edge/data-access` | M |

---

## By SPEC Source

| SPEC | Issues |
|---|---|
| SPEC-01 (Route Map) | FE-EDGE-002, FE-EDGE-015, FE-EDGE-016, FE-EDGE-025 |
| SPEC-02 (Component Hierarchy) | FE-EDGE-009, FE-EDGE-010, FE-EDGE-011, FE-EDGE-012, FE-EDGE-013, FE-EDGE-014, FE-EDGE-017 |
| SPEC-03 (View Models) | FE-EDGE-005, FE-EDGE-007, FE-EDGE-008, FE-EDGE-010, FE-EDGE-011 |
| SPEC-04 (Data Access) | FE-EDGE-006, FE-EDGE-007, FE-EDGE-008, FE-EDGE-020, FE-EDGE-023 |
| SPEC-05 (Module Contract) | FE-EDGE-001, FE-EDGE-013, FE-EDGE-015 |
| SPEC-06 (Shell Integration) | FE-EDGE-003, FE-EDGE-004, FE-EDGE-025, INT-FE-EDGE-001, INT-FE-EDGE-002, INT-FE-EDGE-003, INT-FE-EDGE-004 |
| SPEC-07 (Error Handling) | FE-EDGE-018, FE-EDGE-019, FE-EDGE-026, FE-EDGE-027, INT-FE-EDGE-006 |
| SPEC-08 (E2E Scenarios) | FE-EDGE-021, FE-EDGE-022, FE-EDGE-023, FE-EDGE-024, FE-EDGE-025, FE-EDGE-026 |
| Contracts (DTOs + cross-link) | FE-EDGE-005, FE-EDGE-006, INT-FE-EDGE-005, INT-FE-EDGE-006 |

---

## By Layer (Nx Lib)

| Lib Path | Type | Issues |
|---|---|---|
| `libs/edge/shell-entry` | type:app (entry) | FE-EDGE-001, FE-EDGE-002, FE-EDGE-003, FE-EDGE-004 |
| `libs/edge/data-access` | type:data-access | FE-EDGE-006, FE-EDGE-007, FE-EDGE-008, FE-EDGE-019, FE-EDGE-020, FE-EDGE-027, INT-FE-EDGE-006 |
| `libs/edge/feature-pairing` | type:feature | FE-EDGE-009, FE-EDGE-010, FE-EDGE-011 |
| `libs/edge/feature-device-detail` | type:feature | FE-EDGE-012, FE-EDGE-013, FE-EDGE-014, FE-EDGE-018 |
| `libs/edge/access` | type:domain | FE-EDGE-015 |
| `libs/edge/integration` | type:domain | INT-FE-EDGE-001 |
| `libs/edge/navigation` | type:domain | FE-EDGE-016 |
| `libs/edge/ui` | type:ui | FE-EDGE-017 |
| `libs/shared/contracts` | type:domain | FE-EDGE-005, INT-FE-EDGE-005 |
| `apps/edge` | thin host | FE-EDGE-002, FE-EDGE-004 |
| `apps/edge-e2e` | type:e2e | FE-EDGE-021..027 |
| `apps/web` (scope:web) | type:app | INT-FE-EDGE-002, INT-FE-EDGE-003, INT-FE-EDGE-004 |

---

## Critical Path (blocking chain — longest dependency sequence)

```
FE-EDGE-001 (contract)
  └─► FE-EDGE-002 (routes + module)
        └─► FE-EDGE-003 (descriptor + slots)
              └─► INT-FE-EDGE-001 (EdgeShellIntegrationService)
                    ├─► INT-FE-EDGE-002 (topbar wiring)
                    └─► INT-FE-EDGE-003 (onboarding slot)

FE-EDGE-005 (DTOs)
  └─► FE-EDGE-006 (EdgeHttpService)
        ├─► FE-EDGE-007 (DeviceFacade — deriveState + poll)
        │     ├─► FE-EDGE-004 (bootstrap — needs startPolling)
        │     └─► FE-EDGE-014 (DeviceDetailPageComponent)
        │           └─► FE-EDGE-024 (e2e device-repair-unpair)
        └─► FE-EDGE-008 (PairingFacade)
              └─► FE-EDGE-011 (DevicePairingPageComponent)
                    └─► FE-EDGE-021 (e2e pairing-happy-path)
                          └─► FE-EDGE-022 / FE-EDGE-023 / FE-EDGE-025 (e2e others)

FE-EDGE-017 (ConnectivityChipComponent)
  └─► INT-FE-EDGE-002 (topbar chip wiring)
        └─► FE-EDGE-025 (e2e shell-chip-composition)
```

**Longest chain:** FE-EDGE-001 → 002 → 003 → INT-001 → INT-002 → FE-025 = **6 levels**

---

## Estimated Size by Wave

| Wave | Issues | Size | Days (est.) |
|---|---|---|---|
| P1 Foundation | 4 | 4×S/M | 2 d |
| P2 Data Access | 4 | 2×M + 2×L | 3 d |
| P3 Feature Libs | 6 | 2×S + 3×M | 3 d |
| P4 Hub Integrations | 6 (INT) | 2×S + 4×M | 2.5 d |
| P5 Guards | 1 | 1×S | 0.5 d |
| P6 Navigation | 1 | 1×S | 0.5 d |
| P7 UX Polish | 3 | 3×M | 1.5 d |
| P8 Observability | 1 | 1×S | 0.5 d |
| P9 E2E + Hardening | 7 | 1×L + 5×M + 1×M | 3 d |
| **Total** | **33** | | **~16.5 d** |

---

## Coverage Gaps Closed (from M4 Specs-Validate)

| Gap | Closed by |
|---|---|
| G1: connectivity() computed chain | FE-EDGE-027 |
| G2: countdown → expired transition | FE-EDGE-027 |
| G3: not-paired caregiver informational path | FE-EDGE-018 |
| G4: edgeBootstrap.destroy() lifecycle integration | FE-EDGE-027 |
| G5: serverTime absent fallback | FE-EDGE-027 |
| G6: retry-backoff 2-attempt tests (HIGH) | FE-EDGE-019 |
| Missing EARS clause SPEC-07 /errors/device-retired | FE-EDGE-027 |
