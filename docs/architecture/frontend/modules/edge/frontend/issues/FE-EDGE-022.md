## [FE-EDGE-022] Playwright e2e — pairing-code-expired.feature + pairing-claim-no-heartbeat.feature (SPEC-08 #2, #3)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/pairing-code-expired.spec.ts` and `pairing-claim-no-heartbeat.spec.ts` implement
the two negative pairing scenarios: (2) enrollment token TTL elapses before claim → phase=`expired` →
re-register CTA; (3) agent claims but never heartbeats → phase stays `awaiting-first-heartbeat` (never
advances to `paired`).

**Context / Problem statement:**
- Scenario 2 validates the TTL/countdown path — safety-critical because a stale token must never silently
  hang or auto-advance (SPEC-03 §7 code-TTL invariant).
- Scenario 3 validates the no-premature-paired invariant: the phase must NOT advance past
  `awaiting-first-heartbeat` without a real fresh heartbeat (ADR-005).
- Both scenarios use the same stub infrastructure as FE-EDGE-021.

**Approach (execution path):**
**Scenario 2 (expired):**
1. Stub `POST /devices` → `enrollmentExpiresAt: <now + 1s>`.
2. Override `pollIntervalMs` = 200ms; wait 2s.
3. Assert: `PairingCodeCardComponent` shows "Código expirado"; copy button gone.
4. Assert: `phase = 'expired'`; re-register CTA present.
5. Click re-register CTA → `register()` called again (new request to stub).

**Scenario 3 (claim no heartbeat):**
1. Stub `POST /devices` → normal token; `POST /devices/{id}/pairings` → `pairingState: 'registered'`.
2. Stub liveness: always returns `ultimaSincronizacao: null` (never a fresh heartbeat).
3. Navigate through pairing steps; click "Vincular ao paciente".
4. Assert: `phase` stays `awaiting-first-heartbeat`; "pareado" step NOT marked done; chip NOT green.

**Non-goals:**
- Does not test happy path (FE-EDGE-021).
- Does not test network error retry (FE-EDGE-024).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§2, §3)
- `docs/architecture/frontend/modules/edge/frontend/e2e/pairing-code-expired.feature`
- `docs/architecture/frontend/modules/edge/frontend/e2e/pairing-claim-no-heartbeat.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/pairing-code-expired.spec.ts`
- 🔲 `apps/edge-e2e/src/pairing-claim-no-heartbeat.spec.ts`

**Tests**
- 🔲 `nx e2e edge-e2e --spec=pairing-code-expired` PASS
- 🔲 `nx e2e edge-e2e --spec=pairing-claim-no-heartbeat` PASS

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- Both specs PASS in `nx e2e edge-e2e`
- Expired: "Código expirado" visible; copy button absent; re-register CTA present
- No-heartbeat: phase indicator stays at step 3 (`awaiting-first-heartbeat`); chip NOT green

**Dependencies:**
- FE-EDGE-021 (shared e2e fixture/stubs infrastructure)
- FE-EDGE-009, FE-EDGE-010 (PairingCodeCard + Progress + Result components)

**Data impact:**
- Nenhum (stubbed)

**API / Contract impact:**
- Stub shapes from DTOs (FE-EDGE-005)

**Testing / Evidence required:**
- Playwright report: 2 specs green
- Screenshot: expired state (code expired card) + no-heartbeat state (awaiting step)

**Risks / Notes:**
- Scenario 2 relies on a very short TTL in the stub (`1s`). Use `fakeTimers` / `playwright.clock.install()`
  if available, to fast-forward time instead of adding real `waitForTimeout(2000)` (flaky).
