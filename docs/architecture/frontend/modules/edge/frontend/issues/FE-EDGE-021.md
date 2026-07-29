## [FE-EDGE-021] Playwright e2e — pairing-happy-path.feature (SPEC-08 #1)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/pairing-happy-path.spec.ts` implements the Gherkin scenarios from
`docs/architecture/frontend/modules/edge/frontend/e2e/pairing-happy-path.feature`. Edge-service is stubbed
with deterministic register/liveness responses (MSW or Playwright route intercept). The full flow
"register → show one-time code → agent claims → first heartbeat → pareado·online·versão X" is asserted.

**Context / Problem statement:**
- The pairing happy path is the anchor scenario for the EDGE module (ADR-005 success signal, doc 01 §7).
- Agent claim + first heartbeat is simulated by flipping the stubbed liveness `ultimaSincronizacao` to a
  fresh server-relative value (SPEC-08 §Notes #1).
- The test must assert that `derivedState='online'` is NOT shown before the first heartbeat mock fires.
- Bloqueia: FE-EDGE-027 (Playwright device-repair-unpair uses same stub patterns)

**Approach (execution path):**
1. Set up Playwright fixture with `apps/edge-e2e/src/fixtures/edge-stubs.ts`:
   - `POST /api/v1/edge/devices` → returns `RegisterDeviceResult` with fresh `enrollmentToken`.
   - `GET .../liveness` → first N calls return `pairingState: 'registered'`, `ultimaSincronizacao: null`.
   - After a route intercept flag flip: return `pairingState: 'paired'`, `ultimaSincronizacao: <recent>`,
     `serverTime: <same>`.
2. Navigate to `/edge/pairing` (authenticated conductor session).
3. Assert: enrollment token displayed on screen; "Aguardando código" step active.
4. Flip the stub: liveness now returns fresh heartbeat.
5. Assert: `PairingProgressComponent` advances to "Pareado"; "pareado · online · versão X" visible.
6. Assert: `derivedState='online'` chip appears.

**Non-goals:**
- Does not test expired token (FE-EDGE-022).
- Does not test the onboarding host page composition (INT-FE-EDGE-003).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§1)
- `docs/architecture/frontend/modules/edge/frontend/e2e/pairing-happy-path.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/fixtures/edge-stubs.ts` (shared Playwright stub setup)
- 🔲 `apps/edge-e2e/src/pairing-happy-path.spec.ts` (Playwright spec matching .feature)

**Tests**
- 🔲 `nx e2e edge-e2e --spec=pairing-happy-path` PASS (Playwright)

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- `npx nx e2e edge-e2e` PASS for this spec
- Enrollment token visible in DOM before first heartbeat
- `derivedState='online'` chip ONLY visible after heartbeat stub flip (not prematurely)
- "versão X" label visible in result component

**Dependencies:**
- FE-EDGE-011 (DevicePairingPageComponent — the surface under test)
- FE-EDGE-008 (PairingFacade — pairing flow logic)
- FE-EDGE-007 (DeviceFacade — derivedState computation)

**Data impact:**
- Nenhum (all data stubbed)

**API / Contract impact:**
- Edge-service stubbed; real contract shapes from DTOs (FE-EDGE-005)

**Testing / Evidence required:**
- Playwright report: scenario "Pairing completes on first heartbeat" green
- Screenshot: "pareado · online" state captured

**Risks / Notes:**
- Playwright route intercept timing: the stub flip (liveness → fresh heartbeat) must align with the poll
  timer cadence. Use `pollIntervalMs` override via feature flag to 500ms in e2e to speed up the test.
