# GEST — PoC-to-Real Architecture Status & Plan (for Architecture Team review)

> **Module:** GEST — Detecção de Gesto (Edge) · **Service:** `gest-service` · **Companion FE lib:** `@neuroassist/gest`
> **Status:** DRAFT — awaiting architecture team review
> **Author context:** Generated from an implementation session that (1) merged an incoming PoC-mode commit into in-progress real P1–P3 backend work, (2) fixed 8 real backend gate failures with a JDK available for the first time, (3) built the missing GEST frontend port/adapter/component layer, and (4) wired it into PAC's calibração screen. This document exists because that session made several architecture-relevant decisions ad hoc, under time pressure, without first discovering the pre-existing `docs/architecture/edge-agent/python-edge-agent/` design set — several of those decisions need explicit ratification or correction.
> **Related living docs (not superseded by this one):** `plan-GEST.md` (backend priority tracker), `docs/architecture/frontend/modules/gest/frontend/plans/plan-FE-GEST.md` (frontend wave/backlog tracker). This document is a point-in-time status + decision-request; those remain the ongoing source of truth for backlog sequencing.

---

## 1. Executive summary

- **Backend `gest-service`** now has a real (non-PoC) hexagonal implementation for P1 (scaffold/API), P2 (mock-first domain logic), and P3 (real Postgres persistence, multi-tenant schema routing). 141/143 backend tests pass; the 2 remaining failures are a pre-existing BDD gap unrelated to this work (§5).
- **Frontend `@neuroassist/gest` lib** now has the port/adapter/component layer (`EdgeDetectionPort`, `EligibilityPort`, `GestureLiveTestComponent`, `GestShellIntegrationService`) that the pre-existing `gest.facade.ts` state machine was designed against but never had implementations for. It's wired into PAC's `calibração` screen via a new scope:web host page.
- **A new script, `backend/edge-agent-ws.py`**, implements the localhost WebSocket bridge that `ADR-005-live-test-via-edge-agent-stream.md` calls for — but it reuses the PoC's EAR/BRI heuristic detector, not the formally-specified ONNX classifier from `docs/architecture/edge-agent/python-edge-agent/`. **This is the single most important open item in this document** — see §4.
- **Nothing is runnable end-to-end yet.** No Nx build target exists for the app or the `gest` lib; no auth is wired on the frontend's calls to the (auth-enforced) real backend; nobody has started `gest-service` as a live server this cycle. See §7 for the full blocker list.
- Nothing described here has been pushed or opened as a PR. Two commits exist locally on branch `gest/p3-real-implementation-and-fe-wiring`.

---

## 2. Current state — backend (`gest-service`)

### 2.1 What's real now

| Priority | Scope | Status |
|---|---|---|
| P1 | Scaffold, controllers, DTOs, exception handling | DONE |
| P2 | Mock-first domain logic (`EligibilityService`, `DetectionProfileService`, `DetectionContractService`) + ~44 unit tests | DONE |
| P3 | Real JPA persistence: `TipoGesto`, `TipoGestoTransition`, `DetectionProfile`, `DetectionProfilePrimitive` repository adapters; `V0400__gest_initial_schema.sql` contributed to `neuroassist-ai-migrations`; multi-tenant schema routing (`TenantIdentifierResolver` prefixing `t_`, `TenantContextFilter` 503 guard) | DONE |

### 2.2 The merge this was built on top of

Local work (P1–P3, real implementation) diverged from `origin/main`, which had gained a PoC-mode commit (`c922cd8`) adding `application-poc.yml` profiles, `NoOp`/hardcoded stub adapters, and a simplified local migration path for a live demo. Pulling caused a 32-file merge conflict; every conflict was resolved in favor of the real implementation (the PoC side was consistently the shallower one — e.g. `String id` vs `UUID id`, a fixed `"poc"` tenant vs real JWT tenant extraction). **The PoC profile itself (`application-poc.yml`, stub adapters) was left intact and unmodified** — it still exists as a separate, valid Spring profile for demos; only the *default* (real) profile's code path was the one in conflict and resolved toward realism.

### 2.3 Bugs found and fixed getting the gates green

With a JDK available on the dev machine for the first time this session, the real `architectureCheck`/`test`/`build` gates ran for the first time against this code. They surfaced 8 genuine, previously-latent bugs:

| # | Bug | Root cause | Fix |
|---|---|---|---|
| 1 | Compile error: `EdgePairingPortStub` (added by the PoC commit) referenced `application.port.out.EdgePairingPort`, which never existed | The PoC commit added a stub adapter assuming a port interface that was never written | Created `EdgePairingPort.java` (`getTenantForDevice(deviceId)`) |
| 2 | All Cucumber/BDD tests failed at Spring context load (`Connection to localhost:5432 refused`) | `CucumberSpringContext` had `@SpringBootTest` + `@ActiveProfiles("test")` but no Testcontainers wiring, unlike `BaseIntegrationTest` | Made it `extends BaseIntegrationTest` to inherit the singleton Testcontainers Postgres + `@DynamicPropertySource` |
| 3 | 3 unit tests: `Mockito.verify(eventPublisher).publishEvent(any())` reported "wanted but not invoked" despite the mock clearly being called | `ApplicationEventPublisher.publishEvent` is overloaded (`ApplicationEvent` vs `Object`); domain events are plain records, so production code resolves to the `Object` overload, but bare `any()` binds to the more-specific `ApplicationEvent` overload at compile time | `any(Object.class)` |
| 4 | Cucumber scenario outline: step `"the edge posts a report with primitive confidence 0.0"` reported undefined | Step definition used `{string}` (requires quotes in Gherkin); the Examples table provides bare numbers | Changed to `{double}` |
| 5 | `StaleObjectStateException` / `InvalidDataAccessApiUsageException: detached entity passed to persist` across all 4 P3 JPA entities | Domain layer assigns UUIDs client-side (`TipoGesto.register()` etc.) before the adapter maps to a JPA entity, but entities were also annotated `@GeneratedValue(strategy = GenerationType.UUID)`. Spring Data's `isNew()` heuristic saw a non-null id and called `merge()`; separately, Hibernate's own UUID generator rejected a pre-assigned id as "detached" | Implemented `org.springframework.data.domain.Persistable<UUID>` (transient `isNew` flag, `@PostLoad`/`@PostPersist` clears it) **and** removed `@GeneratedValue` entirely — the id is always assigned, never generated, by design |
| 6 | `column "classification_params" is of type jsonb but expression is of type character varying` | `@Column(columnDefinition = "jsonb")` only affects DDL generation, not JDBC parameter binding for a plain `String` field | Added `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6) |
| 7 | `duplicate key value violates unique constraint "ux_dp_single_active"` across `DetectionProfileRepositoryAdapterIT` | No DB cleanup between test methods in a class whose table has a partial-unique "only one active row" constraint; earlier tests' rows collided with later tests' inserts | Added `@AfterEach` that deletes `detection_profile`/`detection_profile_primitive` rows |
| 8 | `InvalidDataAccessApiUsageException: Executing an update/delete query` on `deactivateCurrent()` | The repository's `@Modifying` query deliberately has no `@Transactional` (RULE-03: transactions only in `application.service`) — correct for production, but the IT test calls it directly with no service layer and thus no transaction context | Added `@Transactional` to the one test method that needs it |

None of these are cosmetic — each was a genuine correctness or tooling defect. #5 and #6 in particular would have caused silent data-layer failures in any real deployment, not just tests.

### 2.4 What's still red

`architectureCheck` has a hard Gradle dependency on `test` succeeding, so **it has not actually executed yet** — it's gated behind:

- 2 remaining `CucumberIT` failures: `Scenario Outline: Tenant isolation — primitives are not visible across tenants`, both examples, `UndefinedStepException` against a step definition (`TipoGestoLifecycleSteps.java:281`) that reads correctly on inspection. Confirmed via `git log` to be **unmodified since the initial repo scaffold commit** — pre-existing, not introduced by this work. Investigated at some length (Cucumber JSON report, bytecode timestamps, duplicate-definition search) without finding the cause; recommend the architecture/QA team take a fresh look, since a second pair of eyes may spot what a single debugging pass missed.
- This scenario sits inside `P8` (BDD), which `plan-GEST.md`'s own validator history already classifies as deferred until P1–P6 mature — so it was pre-existing scope, not a regression, but it's now the literal blocker for a clean `architectureCheck` signal.

---

## 3. Current state — frontend (`@neuroassist/gest` + PAC composition)

### 3.1 What existed already vs. what was built

`gest.facade.ts` and `gest.providers.ts` were **already fully implemented** before this session — a signal-based state machine (`GestureTestFacade`) handling camera lifecycle, composite two-step gesture progression, and the honest-degraded invariant, already coded against an `EDGE_DETECTION_PORT`/`ELIGIBILITY_PORT` injection-token pattern. What was missing, and built this session:

- `EligibilityPort` + mock adapter + real adapter (`POST /gest-service/eligibility`, mapping the backend's `{eligible, missingStates}` to the FE contract's `{eligible, reason, unsupportedPrimitiveIds}`)
- `EdgeDetectionPort` + mock adapter (**never** emits a fake `recognized` — only `preview_frame_ack`/`degraded`, per ADR-005's honest-degraded requirement) + real WS-based adapter (`EdgeDetectionHttpAdapter`, despite the name — it's a WebSocket client, not HTTP)
- `GestShellIntegrationService`, `gestDeviceOnlineGuard`, `GestureLiveTestComponent` (smart container consolidating the Wave-3 presentational pieces `plan-FE-GEST.md` originally speced as 5 separate components — done as one file for now, splittable later without an API change), `GestDevHostComponent`
- One small additive change to `gest.facade.ts`: exposed a `previewStream` readonly signal so the live-test component can bind a cosmetic `<video>` preview (the facade already acquired the `MediaStream` internally; it just wasn't exposed)

### 3.2 PAC composition (GEST-INT-005)

`CommandEditorComponent` (PAC) has an `<ng-content select="[slot=live-test]">` outlet with an explicit comment: *"scope:web compõe aqui — PAC nunca importa scope:gest."* Nothing was actually projecting into that slot. Built:

- `frontend/apps/neuroassist-ai-web/src/app/pages/pac-comando-editor-page.component.ts` — a new scope:web host page wrapping `<pac-command-editor>`, projecting `<gest-gesture-live-test slot="live-test">` into it. Builds the `ComandoDraft` GEST needs from `CommandEditorComponent`'s already-public `editor` signal via a `viewChild` + `computed()` — no changes needed to PAC's internal form state.
- `app.routes.ts`: the `/pac/comandos/novo` and `/pac/comandos/:comandoId/editar` routes now resolve to this wrapper instead of `PAC_ROUTES`'s own (unwrapped) editor routes. **Route ordering matters here** — the override routes must be listed before the catalog's `loadChildren: PAC_ROUTES` entry in the same `children` array, since Angular tries routes in order and `PAC_ROUTES`'s own editor paths would otherwise match first.
- `libs/pac/src/index.ts`: exported `pacAuthGuard`, `pacTenantPatientGuard`, `pacCommandWriteGuard` from the barrel so the app-level route override could reuse PAC's own access-control guards verbatim instead of reimplementing them.
- Onboarding's equivalent composition slot (`GEST-INT-006`) is **not done** — only the PAC calibração slot was wired this session.

### 3.3 Verification performed (and its limits)

Verified via the real Angular compiler (`node_modules/.bin/ngc`, `strictTemplates: true`, not just `tsc`) against the app + `gest`/`pac`/`shared/contracts` libs — **zero errors** in any new or modified file. Also fixed a pre-existing, unrelated `isolatedModules` bug in `shared/contracts/src/index.ts` (`export` → `export type` for interface-only re-exports) that was blocking this check for anyone downstream of that barrel.

This confirms **type and template correctness**, not runtime correctness. Nothing here has been run in a browser — see §7.1.

---

## 4. Architecture reconciliation: three edge-agent artifacts (ACTION NEEDED)

This is the item most in need of architecture team judgment. There are now **three** distinct pieces of code/documentation under the "edge agent" umbrella that were never reconciled against each other:

| Artifact | Purpose | Detection algorithm | Auth | Status |
|---|---|---|---|---|
| `docs/architecture/edge-agent/python-edge-agent/` (full ADR/spec set: ADR-001..005, SPEC-03..08) | **Formal, approved production architecture.** Continuous detection → `GestoDetectado` event → REST POST to EVNT, via an outbox pattern with SQLite persistence | MediaPipe FaceMesh landmarks → **ONNX classifier** (`gesture_v1.onnx`) | OAuth2 `client_credentials` against ACS, JWT RS256, Bearer, in-memory | Documented, not yet implemented as running code (no `.py` matching this design was found) |
| `backend/poc-edge-agent-webcam.py` | Ad-hoc demo script predating/parallel to the formal PEA design; opens its own OpenCV window, posts detected gestures straight to `evnt-service` | Hand-coded **EAR (Eye Aspect Ratio) + BRI (Brow Raise Index)** threshold/hysteresis state machine | None (direct POST, PoC-mode relaxed security) | Working demo script, never formally referenced by the PEA docs |
| `backend/edge-agent-ws.py` (**new this session**) | Implements the **live-test/calibration bridge** specified narrowly by FE `ADR-005-live-test-via-edge-agent-stream.md` + BE `event-contracts.md §3.2` — a separate, additional surface from the main detection→alarm pipeline, used only when a caregiver is testing a `Comando` in PAC's calibração screen | **Reused the PoC's EAR/BRI detector** (no ONNX model exists to reuse instead) | Provisional / unenforced, explicitly labeled as such in-code (`GEST-INT-004`/`INT-GEST-007` track the real decision) | Smoke-tested (WS handshake, `start_session`/`stop_session`, honest-degraded fallback) with no real camera; never tested against real recognized-gesture output |

**The gap:** ADR-005's entire decision rationale for bridging to a real edge agent instead of running ML in the browser is *"what you test is what detects — no drift... a model in the browser would decide differently from the production edge classifier, so a command could pass calibration and still miss in production — unacceptable for a safety channel."* That rationale assumes the live-test bridge and the production detector are **the same engine**. Right now they would not be: `edge-agent-ws.py` (calibration) uses EAR/BRI; the documented production PEA design uses ONNX. If both existed and ran simultaneously, a gesture could calibrate successfully via one algorithm's thresholds and fail in production under the other's — precisely the failure mode ADR-005 was written to prevent.

**This is not a bug in `edge-agent-ws.py`** — it correctly implements the frozen wire contract, and no ONNX model exists yet to do otherwise. It's a **sequencing gap**: the calibration bridge was built before the production detector it's supposed to mirror. Options for the architecture team to weigh:

1. **Accept as a known, time-boxed interim gap.** Ship EAR/BRI-backed calibration now (useful for E2E testing and demos), explicitly flag it as provisional, and replace the detector inside `edge-agent-ws.py` (or better, retire it and have the real PEA process expose the live-test WS endpoint itself) once the ONNX classifier and PEA are actually implemented.
2. **Block the live-test bridge until PEA/ONNX exists**, keeping GEST's calibration screen in permanent degraded/mock mode until then — safer per ADR-005's own logic, slower to demo.
3. **Formally amend ADR-005** if the architecture team decides a *documented, understood* algorithm mismatch during a bounded interim period is an acceptable risk (e.g., because EAR/BRI and ONNX are both trained/tuned against the same primitive vocabulary and the risk is judged low) — but this should be a conscious decision recorded in the ADR, not silent drift.

Recommend the architecture team pick one of these and record it as either an ADR-005 amendment or a new backlog item before this bridge sees a real caregiver.

---

## 5. Explicitly deferred / not this team's call

- **`INT-GEST-007` (BE) / `GEST-INT-004` (FE)** — the mTLS-vs-OAuth2 `client_credentials` decision for device/bridge auth. Both `edge-agent-ws.py` and `EdgeDetectionHttpAdapter` use a provisional, unenforced channel pending this. Requires a joint session with the EDGE team per the existing issue.
- **P4/P5** (EdgePairing auth wiring, OAuth2 resource server config + RULE-07 multi-tenant enforcement audit) — not started.
- **P8 BDD** — 52 of 54 Cucumber scenarios remain `PendingException` stubs; the 2 that are genuinely broken are described in §2.4.

---

## 6. Concrete blockers to end-to-end verification

None of these require architecture-level decisions — they're implementation/setup gaps, listed here so the team has the full picture of "why haven't you just run it and shown us":

1. **No Nx build target for the app or `gest` lib.** `frontend/apps/neuroassist-ai-web` has no `project.json` at all; `libs/gest/project.json` has `"targets": {}`. `nx serve`/`nx build` cannot run yet.
2. **No auth wiring on the frontend's GEST calls.** The real (non-PoC) `gest-service` controllers are `@PreAuthorize`-gated; `EligibilityHttpAdapter`/`EdgeDetectionHttpAdapter` (my WS client) call out with no token. Confirmed by inspection, not yet hit in practice since #1 blocks running the app at all.
3. **Nobody has started `gest-service` as a live server this cycle** — all verification was via Testcontainers (ephemeral) or unit tests. Per project convention, `neuroassist-ai-migrations` must run first, then real Postgres via `docker-compose`.
4. **`edge-agent-ws.py`'s Python deps** (`opencv-python`, `mediapipe`, `numpy`, `websockets`) were installed via `pip install --user` on one dev machine this session — not captured in any `requirements.txt` or setup doc.
5. **`evnt-service` likely has the same latent bugs** `gest-service` just had fixed (§2.3 #5/#6 especially — its JPA entities looked structurally identical on inspection) but went through the identical PoC merge without the same gate-fixing pass.

---

## 7. Recommended next steps, in order

1. **Architecture team: resolve §4** (the EAR/BRI-vs-ONNX drift question) — this is the one decision that affects whether the calibration bridge as built is safe to demo to anyone who might mistake it for production-representative.
2. Scaffold Nx build targets for the app and `gest` lib (unblocks everything else).
3. Wire frontend auth (auth-session token attachment) into `EligibilityHttpAdapter`/`EdgeDetectionHttpAdapter`.
4. Run `neuroassist-ai-migrations` + `docker-compose` + `gest-service` for a real end-to-end smoke test (webcam → `edge-agent-ws.py` → browser → PAC calibração screen), even with the EAR/BRI interim detector.
5. Apply the same gate-fixing pass (§2.3 pattern) to `evnt-service`.
6. Revisit the 2 pre-existing BDD failures (§2.4) with fresh eyes once higher-priority items are clear.
7. Push `gest/p3-real-implementation-and-fe-wiring` and open a PR once the team is comfortable with §4's resolution — per repo convention, no direct push to `main`.
