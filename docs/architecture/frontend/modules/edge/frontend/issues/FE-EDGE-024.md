## [FE-EDGE-024] Playwright e2e — device-repair-unpair.feature (SPEC-08 #6): conductor actions, 409 refresh, caregiver RBAC

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/device-repair-unpair.spec.ts` asserts: (a) a conductor session sees re-pair/unpair/retire
buttons; (b) a caregiver session sees NO management affordance; (c) a `409 pairing-conflict` on unpair triggers
a device read-model refresh rather than a stale write; (d) `409 optimistic-lock` refreshes without a retry.

**Context / Problem statement:**
- SPEC-08 #6: `409` responses for conductor actions (pairing-conflict, optimistic-lock) must refresh the
  read-model and re-present server state — never silently fail or send a stale write (SPEC-07 §1).
- Caregiver RBAC: `DeviceActionsComponent` hides (not disables) management affordances when `canManage=false`
  (SPEC-05 §5). This must be asserted at the DOM level, not just unit-level.
- This scenario is the integration proof that `DeviceFacade.unpair()` and `retire()` dispatch correctly
  and handle 409 by calling `getDevice()` refresh.

**Approach (execution path):**
1. Conductor session flow:
   - Stub `GET /devices/{id}` → paired device.
   - Navigate to `/edge/device/{id}`; assert re-pair/unpair/retire buttons visible.
   - Stub `DELETE .../pairings/current` → `409 pairing-conflict`.
   - Click unpair; confirm dialog; assert `getDevice()` re-fetched; no stale write.
2. Caregiver session flow:
   - Authenticate as caregiver (no `edge.device.manage`).
   - Navigate to `/edge/device/{id}`; assert zero action buttons in DOM.
3. Optimistic-lock:
   - Stub `DELETE .../pairings/current` → `409 optimistic-lock`.
   - Assert device read-model refreshed (stub `/devices/{id}` called again).

**Non-goals:**
- Does not test the full re-pairing flow (FE-EDGE-021 happy path covers that).
- Does not test the pairing route guard redirect (unit test in FE-EDGE-015).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§6)
- `docs/architecture/frontend/modules/edge/frontend/e2e/device-repair-unpair.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/device-repair-unpair.spec.ts`

**Tests**
- 🔲 `nx e2e edge-e2e --spec=device-repair-unpair` PASS (Playwright)

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- Spec PASS
- Caregiver: zero action buttons in DOM (asserted via `querySelectorAll` count = 0)
- `409 pairing-conflict`: GET /devices/{id} called again after conflict (stub call count asserted)
- No stale write: DELETE not re-attempted automatically after 409

**Dependencies:**
- FE-EDGE-013 (DeviceActionsComponent — actions hidden for caregiver)
- FE-EDGE-014 (DeviceDetailPageComponent — confirm dialog + refresh dispatch)
- FE-EDGE-021 (shared stubs infrastructure)

**Data impact:**
- Nenhum (stubbed)

**API / Contract impact:**
- Stubs: GET .../devices/{id}, DELETE .../pairings/current, POST .../:retire

**Testing / Evidence required:**
- Playwright report: spec green
- Caregiver action count: 0 buttons confirmed by DOM query
- 409 refresh: stub `/devices/{id}` call count increased after conflict

**Risks / Notes:**
- Confirm dialog behavior in Playwright: may need `page.on('dialog', dialog => dialog.accept())` if
  using `window.confirm`, or a custom dialog component spy.
