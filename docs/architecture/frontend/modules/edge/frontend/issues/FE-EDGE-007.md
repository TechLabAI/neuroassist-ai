## [FE-EDGE-007] DeviceFacade — signals + poll timer + deriveState() (server-relative age)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`DeviceFacade` in `libs/edge/data-access` exposes `device`, `derivedState`, `connectivity`, `detail`, and
`lastSyncRelative` as Angular `Signal`s derived via `computed()`. The poll timer (`timer(pollIntervalMs)` →
`switchMap` liveness) handles ETag/304, pauses on Page Visibility hidden, and sets `_pollOk=false` on failure
so `derivedState` degrades to `unknown` — never optimistically `online`.

**Context / Problem statement:**
- `derivedState` is the honesty invariant: computed from server-relative age (`serverTime − ultimaSincronizacao`)
  from the same poll response — never from `Date.now()` — to defeat client clock skew (ADR-005, doc 06 R5).
- `unknown ≠ online` is a hard invariant: on poll failure `_pollOk=false` → `unknown`, not `online` (SPEC-03 §2).
- ETag/304: a 304 does NOT update `_device` but DOES tick `_clock` and sets `_pollOk=true` so the age keeps
  aging honestly toward `stale/offline` (SPEC-04 §3, api-contract §4.4).
- Bloqueia: FE-EDGE-004, FE-EDGE-014, FE-EDGE-015, INT-FE-EDGE-001

**Approach (execution path):**
1. Create `libs/edge/data-access/src/lib/device.facade.ts` as `@Injectable({ providedIn: 'root' })`.
2. Declare private signals: `_device = signal<DispositivoEdgeView | null>(null)`, `_clock = signal<number>(0)`,
   `_pollOk = signal<boolean>(true)`.
3. Implement `deriveState()` pure function (server-relative age logic, SPEC-03 §2):
   - Not-paired: `pairingState ∈ {unpaired, retired}` or `status='n/a'`
   - Offline (never reported): `paired` + `ultimaSincronizacao = null`
   - Unknown: `_pollOk = false`
   - Online/stale/offline: thresholds from `MODULE_FEATURE_FLAGS`
4. `computed()` signals: `derivedState`, `connectivity` (→ `DeviceConnectivityVM`), `detail` (→ `DeviceDetailVM`),
   `lastSyncRelative` (relative time string).
5. Implement `startPolling()`: `timer(0, pollIntervalMs).pipe(switchMap(...pollPatientLiveness...))` — update
   `_device`, `_clock`, `_pollOk`. On 304: tick `_clock` + set `_pollOk=true`, leave `_device` unchanged.
   On error: set `_pollOk=false`.
6. Wire Page Visibility: pause (unsubscribe) on `visibilitychange:hidden`; resume on `visible`.
7. `stopPolling()`: complete the poll subscription.
8. `unpair(reason)`: call `EdgeHttpService.unpair()`, on success call `load()` to refresh (not optimistic).
9. `retire()`: call `EdgeHttpService.retire()`, on success refresh.

**Non-goals:**
- Does not own the pairing flow (PairingFacade, FE-EDGE-008).
- Does not compute breadcrumbs (libs/edge/navigation, FE-EDGE-016).

**Layer(s):**
frontend / libs/edge/data-access

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-04-data-access.md` (§3, §4)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-03-view-models.md` (§2, §5)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`
- Signals-first: no `BehaviorSubject` / store (doc 04 §6)
- RxJS only for the poll timer pipeline (`timer` → `switchMap` → `takeUntilDestroyed`)

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/data-access/src/lib/device.facade.ts` (DeviceFacade class)
- 🔲 `libs/edge/data-access/src/lib/derive-state.ts` (pure deriveState() function — testable in isolation)
- 🔲 `libs/edge/data-access/src/index.ts` (re-export DeviceFacade)

**Tests**
- 🔲 `libs/edge/data-access/src/lib/derive-state.spec.ts`:
  - `pairingState='unpaired'` → `not-paired`
  - `ultimaSincronizacao=null` + `paired` → `offline`
  - `_pollOk=false` → `unknown` (never `online`)
  - `age < stalenessThresholdMs` → `online`
  - `stalenessThresholdMs ≤ age < offlineWindowMs` → `stale`
  - `age ≥ offlineWindowMs` → `offline`
- 🔲 `libs/edge/data-access/src/lib/device.facade.spec.ts`:
  - 304 response: `_device` unchanged, `_pollOk=true`, `_clock` ticked
  - poll failure: `derivedState = 'unknown'`
  - `stopPolling()` after `startPolling()`: timer subscription completes
  - `unpair()`: calls `EdgeHttpService.unpair()`, then `getDevice()` (not optimistic state flip)
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
- `deriveState` unit: `unknown` invariant passes (poll failure never → `online`)
- `deriveState` unit: 5-state transitions all covered
- `unpair` test: `getDevice` called after success (server refresh, not optimistic)

**Dependencies:**
- FE-EDGE-005 (DTOs: DispositivoEdgeView, LivenessView, DerivedState, DeviceConnectivityVM, DeviceDetailVM)
- FE-EDGE-006 (EdgeHttpService)

**Data impact:**
- Nenhum (read-through cache from poll; no client-side persistence)

**API / Contract impact:**
- Reads from `GET /api/v1/edge/patients/{pacienteId}/liveness` (poll), `GET /devices/{id}`,
  `DELETE .../pairings/current`, `POST .../:retire`

**Testing / Evidence required:**
- `derive-state.spec.ts` all 6 cases passing
- Poll failure test: `_pollOk=false` → `derivedState='unknown'`
- ETag/304 test: `_device` unchanged on 304

**Risks / Notes:**
- `_clock` signal tick on 304: the stalled-poll-loop detection uses `_clock` to detect when no fresh
  `serverTime` has arrived for > offlineWindowMs. This must be distinguished from `_device` freshness —
  implement carefully to avoid conflating "poll succeeded (304)" with "device is online".
- Page Visibility API availability: add a guard for server-side rendering / test environments where
  `document.visibilityState` may not be present.
