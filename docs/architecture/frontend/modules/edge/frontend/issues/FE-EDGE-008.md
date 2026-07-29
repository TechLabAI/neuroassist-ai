## [FE-EDGE-008] PairingFacade — register→claim→first-heartbeat pairing flow (signals, no-premature-paired invariant)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`PairingFacade` in `libs/edge/data-access` manages the full pairing flow as a `signal<PairingVM>`. It exposes
`state`, `phase`, `countdown` signals and the dispatch methods `register`, `pairToPatient`, `retry`, `reset`,
`stopPolling`. The `paired` phase is only reached after a real first heartbeat (SPEC-03 §7 no-premature-paired
invariant). Enrollment token TTL expiry forces `phase→expired` before `pairToPatient` can be called.

**Context / Problem statement:**
- The pairing flow has 4 steps: register (mint token) → awaiting-claim → pairToPatient → awaiting-first-heartbeat → paired.
- `paired` must NOT be advanced on the enrollment response alone — only after `ultimaSincronizacao ≠ null`
  AND server-relative age < threshold (ADR-005 no-premature-paired, SPEC-03 §7).
- Countdown is a `computed()` from `enrollmentExpiresAtIso`; at zero, `phase→expired` and polling stops.
- On registration failure, `phase` is restored and the error surfaced (SPEC-04 §2 no optimism).
- Bloqueia: FE-EDGE-011, FE-EDGE-012, FE-EDGE-013, FE-EDGE-014

**Approach (execution path):**
1. Create `libs/edge/data-access/src/lib/pairing.facade.ts` as `@Injectable({ providedIn: 'root' })`.
2. Private `_vm = signal<PairingVM>(idleVm())` — `idleVm()` returns phase:`idle` state.
3. `countdown = computed(() => secondsUntil(this._vm().enrollmentExpiresAtIso))` — drives the UI TTL display.
4. `register(label, pacienteHint?)`:
   - Set phase `registering`, call `EdgeHttpService.register()`.
   - On success: set `deviceId`, `enrollmentToken`, `enrollmentExpiresAtIso`, phase `awaiting-claim`;
     start the claim-poll (poll `getDevice` until `ultimaSincronizacao` is fresh or TTL expires).
   - On failure: restore to `idle`, set `errorMessage`.
5. Claim-poll watches the countdown — at 0 transitions `phase→expired`, stops polling.
6. `pairToPatient(pacienteId)`:
   - Set phase `awaiting-first-heartbeat`, call `EdgeHttpService.pairToPatient()`.
   - Start heartbeat-poll (poll liveness until first fresh `ultimaSincronizacao`).
   - Only then: phase `paired`, emit `PairedDeviceOutcome` (with `derivedState='online'`).
7. `retry()`: re-invoke the last failed step.
8. `reset()`: `_vm.set(idleVm())`, stop all polls.
9. `stopPolling()`: teardown (called from `ngOnDestroy` of `DevicePairingPageComponent`).
10. Export from `libs/edge/data-access/src/index.ts`.

**Non-goals:**
- Does not manage connectivity polling (DeviceFacade, FE-EDGE-007).
- Does not produce the `DeviceConnectivityVM` for the topbar chip.

**Layer(s):**
frontend / libs/edge/data-access

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-04-data-access.md` (§2)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-03-view-models.md` (§4, §6, §7)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`
- Signals-first; RxJS only for the poll loops
- `takeUntilDestroyed` for lifecycle safety

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/data-access/src/lib/pairing.facade.ts`
- 🔲 `libs/edge/data-access/src/lib/pairing-idle-vm.ts` (idleVm() factory — testable in isolation)
- 🔲 `libs/edge/data-access/src/index.ts` (re-export PairingFacade)

**Tests**
- 🔲 `libs/edge/data-access/src/lib/pairing.facade.spec.ts`:
  - `register()` success → phase=`awaiting-claim`; enrollmentToken surfaced
  - `register()` failure → phase=`idle`; errorMessage set; no token stored
  - Claim-poll: `ultimaSincronizacao` fresh → phase NOT yet `paired` (must wait for pairToPatient call)
  - TTL countdown → 0 → phase=`expired`; polling stopped
  - `pairToPatient()` → `awaiting-first-heartbeat`
  - First fresh heartbeat → phase=`paired` (no premature advancement on enroll alone)
  - `reset()` → phase=`idle`; token cleared
- 🔲 `nx test edge-data-access` PASS
- 🔲 `nx lint edge-data-access` PASS

**Milestone:**
Wave P2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P2

**Acceptance criteria:**
- `npx nx test edge-data-access` PASS
- `npx nx lint edge-data-access` PASS
- No-premature-paired invariant test: phase stays `awaiting-first-heartbeat` until a real fresh heartbeat
- TTL expiry test: at countdown=0 phase=`expired` and poll stopped
- `reset()` test: `_vm.set(idleVm())` restores idle state and clears token

**Dependencies:**
- FE-EDGE-005 (DTOs: PairingVM, PairingPhase, PairedDeviceOutcome, RegisterDeviceResult)
- FE-EDGE-006 (EdgeHttpService: register, pairToPatient)
- FE-EDGE-007 (deriveState — used in heartbeat poll to evaluate freshness)

**Data impact:**
- Nenhum (no client-side persistence; enrollment token held in memory only, never localStorage)

**API / Contract impact:**
- Calls `POST /api/v1/edge/devices`, `POST .../pairings`, polls `GET .../liveness` + `GET .../devices/{id}`

**Testing / Evidence required:**
- No-premature-paired: test advances to `paired` only after fresh `ultimaSincronizacao` (not on `register` response)
- Token cleared on `reset()` confirmed in unit test

**Risks / Notes:**
- Enrollment "enrolled" state ambiguity (contracts/backend-api-cross-link.md §5 open item):
  the FE infers "agent claimed" from the first fresh heartbeat, not a distinct wire state. Confirm with BE
  before claim-poll implementation — if BE adds a `claimed` pairingState, the poll shortcut changes.
- ACS ↔ EDGE dynamic client registration (M2 warning, BE INT-002): onboarding UX copy ("run the agent,
  enter this code") depends on final enrollment ergonomics. Freeze copy before this issue ships.
