# Plan FE-GEST — Frontend Execution Plan — GEST Module
# NeuroAssist AI

> **Module:** GEST — Detecção de Gesto (Edge)
> **Status:** BACKLOG READY — awaiting scaffold (M5-fe)
> **Issues:** 26 functional + 7 integration = 33 total
> **Estimated total:** ~14 developer-days
> **Constraint:** GEST is an embedded feature (showInNav: false); no standalone product route

---

## 1. Project summary

GEST is the gesture-detection integration layer for NeuroAssist AI's frontend. It exposes a single user-facing surface — `GestureLiveTestComponent` — that is composed into PAC (command calibration) and onboarding pages at the `scope:web` level. GEST itself has no top-level navigation entry and no standalone UI module: the caregiver accesses it only through host pages.

The thin host app (`apps/gest`) exists exclusively for development and end-to-end testing. In production, the only composition points are:
- `pac-comando-editor-page` (PAC module — command calibration)
- `onboarding-gesto-step` (onboarding module — initial gesture setup)

The primary architectural complexity lies in:
1. **Edge bridge isolation:** live recognition stream runs over localhost WS/SSE to a local edge agent; the FE wraps it as `Observable<EdgeRecognitionSignal>` via `EdgeDetectionClient`.
2. **Honest-degraded invariant (ADR-005):** when the device is offline or the bridge is unreachable, the surface degrades gracefully but honestly — never showing a fake "recognized" verdict.
3. **Nx-boundary composition:** `scope:pac` and `scope:acs` must never import `scope:gest` directly; `GestShellIntegrationService` is the only sanctioned resolution path.
4. **Bridge transport auth (OPEN):** the localhost channel auth (mTLS vs OAuth2) is a joint decision with the EDGE team, tracked in GEST-INT-004 (FE) and INT-GEST-007 (BE).

---

## 2. Wave map (P1–P9)

### Wave 1 — Foundation (P1) · ~2–3 days · FIRST
**Definition of Done:** `nx build gest-shell-entry` PASS; shell can register GEST; `project-context.json` registry complete.

| Issue | Task | Day |
|---|---|---|
| GEST-INT-007 | Fix project-context.json registry (frontendLib, hasUi, isInfrastructure) | D1 |
| GEST-FE-001 | gest.contract.ts (MODULE_KEY, menu, permissions, flags) | D1 |
| GEST-FE-003 | GestModule barrel + 7 lib project.json files | D1 |
| GEST-FE-002 | GEST_ROUTES + apps/gest thin host | D2 |
| GEST-FE-004 | GEST_MODULE_DESCRIPTOR + gest.descriptor.ts | D2 |
| GEST-FE-005 | gest.bootstrap.ts (init/destroy) | D2 |
| GEST-FE-006 | tool-descriptors.ts fallback at scope:web | D3 |

### Wave 2 — Data Access (P2) · ~2 days · Unblocked after W1
**Definition of Done:** `EdgeDetectionClient` + `GestureTestFacade` + REST services unit-tested; signal → VM mapping complete.

| Issue | Task | Day |
|---|---|---|
| GEST-INT-003 | EdgeRecognitionSignal + EligibilityResult in shared/contracts | D4 |
| GEST-FE-007 | EdgeDetectionClient (openStream/mockStream/close + version negotiation) | D4 |
| GEST-FE-008 | GestureTestFacade (signals, state machine, 7 signal→VM mappings) | D5 |
| GEST-FE-009 | GestEligibilityService + DetectionContractService | D5 |
| GEST-FE-010 | ProblemDetail → AppError pt-BR copy table | D5 |

### Wave 3 — Feature Libs (P3) · ~3 days · Unblocked after W2
**Definition of Done:** All 5 presentational components + smart container; `nx build gest-feature-live-test` PASS; screenshots of all 5 states captured.

| Issue | Task | Day |
|---|---|---|
| GEST-FE-012 | PreviewFrameComponent (webcam video wrapper) | D6 |
| GEST-FE-013 | TestStateBadgeComponent (5-state badge) | D6 |
| GEST-FE-014 | CompositeStepIndicatorComponent (two-step progress) | D7 |
| GEST-FE-015 | DegradedBannerComponent (4 reasons, always shown) | D7 |
| GEST-FE-016 | LiveTestControlsComponent (RBAC hide, 3 controls) | D7 |
| GEST-FE-011 | GestureLiveTestComponent (smart container — wires all above) | D8 |

### Wave 4 — Hub Integrations (P4) · ~2 days · Parallel with W3
**Definition of Done:** Shell integration service working; nx-boundary rules enforced; composition slots wired in PAC/onboarding.

| Issue | Task | Day |
|---|---|---|
| GEST-INT-002 | ESLint nx-boundary rules (scope:gest import restrictions) | D6 |
| GEST-INT-001 | GestShellIntegrationService (registerWithShell + resolveLiveTestSlot) | D6 |
| GEST-INT-004 | bridgeToken provider (JWT-device provisional; document OPEN item) | D7 |
| GEST-INT-005 | PAC host page composition slot | D8 |
| GEST-INT-006 | Onboarding host page composition slot | D8 |

### Wave 5 — Access Guards (P5) · ~0.5 days · Unblocked after W2
| Issue | Task | Day |
|---|---|---|
| GEST-FE-017 | gestDeviceOnlineGuard (non-blocking, degraded mode on offline) | D9 |

### Wave 6 — Navigation (P6) · ~0.5 days · Unblocked after W1
| Issue | Task | Day |
|---|---|---|
| GEST-FE-018 | gest/navigation breadcrumb (thin host only) | D9 |

### Wave 7 — UX Polish (P7) · ~1 day · Unblocked after W3
**Definition of Done:** All states visually distinct; screenshots captured; camera-error panel a11y verified.

| Issue | Task | Day |
|---|---|---|
| GEST-FE-019 | Loading/empty states (spinner, idle placeholder, camera-error panel) | D10 |
| GEST-FE-020 | Error UI routing (toast/banner/modal per SPEC-07 §4) | D10 |

### Wave 8 — Observability (P8) · ~0.5 days · Unblocked after W2
| Issue | Task | Day |
|---|---|---|
| GEST-FE-021 | Telemetry events (start/stop/verdict/degraded; no confidence in payload) | D11 |

### Wave 9 — E2E + Hardening (P9) · ~2.5 days · Unblocked after W3+W4
**Definition of Done:** All 5 Playwright spec files PASS; Gate 2 coverage 33/33 (100%); MediaStream leak test PASS.

| Issue | Task | Day |
|---|---|---|
| GEST-FE-022 | Playwright — simple-recognized + composite | D12 |
| GEST-FE-023 | Playwright — degraded-offline + timeout-not-recognized | D12 |
| GEST-FE-024 | Playwright — camera-permission-error + camera-lifecycle-teardown | D13 |
| GEST-FE-025 | Playwright — composition-boundary + backend-error-handling | D13 |
| GEST-FE-026 | **[MANDATORY GAP]** stream-no-cache-between-runs (SPEC-04 §5; Gate 2 closure) | D14 |

---

## 3. Critical path

```
GEST-INT-007  →  GEST-FE-001  →  GEST-FE-003
                      ↓               ↓
               GEST-FE-004    GEST-INT-003
                      ↓               ↓
               GEST-FE-005    GEST-FE-007 (EdgeDetectionClient)
                      ↓               ↓
               GEST-FE-006    GEST-FE-008 (Facade + state machine)
                                       ↓
                     GEST-FE-011 (GestureLiveTestComponent)
                        ↙         ↘
              GEST-INT-001      GEST-FE-017 (guard)
                   ↓
           GEST-INT-005 + INT-006 (composition slots)
                   ↓
           GEST-FE-022..026 (e2e — all must pass)
                   ↑
           GEST-FE-026 (stream-no-cache — MANDATORY gap closure)
```

Total critical path: 14 developer-days (sequential; parallelization possible at W3/W4).

---

## 4. MVP milestone gates

| Gate | Issues done | Deliverable |
|---|---|---|
| **W1 Done** | FE-001..006, INT-007 | Shell can discover and register GEST; thin host builds |
| **W2 Done** | + FE-007..010, INT-003 | Facade and bridge client unit-tested; stream → VM mapping verified |
| **W3 Done** | + FE-011..016 | All components rendered; screenshots captured; dev mode shows live test |
| **W4 Done** | + INT-001..006 | PAC/onboarding composition slots wired; nx-boundary enforced |
| **MVP (W7 Done)** | + FE-017..020 | Feature-complete: all states, all error surfaces, guards active |
| **Ship-ready (W9 Done)** | + FE-021..026 | All e2e PASS; Gate 2 coverage 33/33; MediaStream leak test PASS; telemetry active |

---

## 5. Top-3 risks

1. **Bridge transport auth (GEST-INT-004 / BE INT-GEST-007 — OPEN)**
   Mitigation: provisional JWT-device token isolated behind `InjectionToken`; swap is a 1-file change when decision is made. Escalate to EDGE team in W1.

2. **PAC/onboarding host pages not scaffolded when GEST composition slots are ready (W4)**
   Mitigation: GEST-INT-005/006 can be stubbed with a placeholder page in `scope:web` for development; real PAC/onboarding integration follows PAC backlog timeline.

3. **MediaStream track leakage (GEST-FE-024)**
   Mitigation: `camera-lifecycle-teardown.feature` Playwright test is the final gate; `ngOnDestroy → facade.stopTest()` is mandatory in GEST-FE-011.

---

## 6. Approval criteria per milestone

| Milestone | Criteria |
|---|---|
| W1 (Foundation) | `nx build gest-shell-entry` PASS; `MODULE_MENU_METADATA.showInNav === false`; project-context.json GEST registry complete |
| W3 (Feature-complete UI) | Screenshots of 5 states (idle, testing, recognized, not-recognized, camera-error) + degraded banner captured |
| W4 (Integration) | `nx lint` PASS on all libs (no scope:pac → scope:gest violation); composition slot resolves `GestureLiveTestComponent` at runtime |
| W9 (Ship-ready) | `nx e2e gest-e2e` PASS (all 5 spec files); GEST-FE-026 Gate 2 coverage = 33/33; no MediaStream leaks in teardown test |

---

## 7. Deferred items

| Item | Tracking |
|---|---|
| bddGate (BE) | Run after BE scaffold via `python scripts/be-gates-replay.py --run --update --service gest-service --gate bddGate` (BE GEST-023,024) |
| Bridge transport auth final decision | GEST-INT-004 (FE) / BE INT-GEST-007 — joint with EDGE team |
| GEST registry `frontendLib`/`hasUi` | GEST-INT-007 — fix in W1 (first task) |
