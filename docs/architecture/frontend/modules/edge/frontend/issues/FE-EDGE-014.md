## [FE-EDGE-014] DeviceDetailPageComponent — smart container (polling lifecycle, unpair/retire dispatch, confirm dialogs)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`DeviceDetailPageComponent` in `libs/edge/feature-device-detail` is the smart container for the device
management surface. It reads `DeviceFacade` signals, handles `@Input() deviceId?: string`, manages
startPolling/stopPolling lifecycle, and dispatches unpair/retire after user confirmation dialogs.

**Context / Problem statement:**
- When `deviceId` is absent, the page calls `DeviceFacade.resolveForActivePatient()` (SPEC-01 §4).
- The poll must start on `ngOnInit` and stop on `ngOnDestroy` — no leaked timer (doc 05 §3).
- `unpair` and `retire` are irreversible conductor actions — a confirm dialog must gate them before dispatch
  (product requirement; FE-EDGE-013 only emits the intent).
- After `unpair(reason)` / `retire()` success, the page does NOT optimistically flip state — it waits for
  the server refresh (via `DeviceFacade.getDevice()`, SPEC-04 §3).
- Bloqueia: FE-EDGE-027 (Playwright e2e device-repair-unpair)

**Approach (execution path):**
1. Create `libs/edge/feature-device-detail/src/lib/device-detail-page/device-detail-page.component.ts`
   as `standalone: true`, `OnPush`.
2. Inject `DeviceFacade`.
3. `@Input() deviceId?: string` — if provided, call `DeviceFacade.load(deviceId)` on init; else
   `DeviceFacade.resolveForActivePatient()`.
4. `ngOnInit`: call start method, then `DeviceFacade.startPolling()`.
5. `ngOnDestroy`: `DeviceFacade.stopPolling()`.
6. Template binds facade signals to child component inputs:
   - `[derivedState]="device.derivedState()"`, `[lastSyncRelative]="device.lastSyncRelative()"`,
   - `[detail]="device.detail()"`, `[canManage]="device.detail().canManage"`.
7. `(rePair)` output from actions → navigate to `/edge/pairing` (router).
8. `(unpair)` output → open confirm dialog; on confirm: `DeviceFacade.unpair(reason)`.
9. `(retire)` output → open confirm dialog; on confirm: `DeviceFacade.retire()`.
10. Loading / empty states: render skeleton while `device.detail() === null`; render "not-paired" empty
    state when `derivedState = 'not-paired'` (FE-EDGE-018).

**Non-goals:**
- Does not implement `DeviceFacade` logic (FE-EDGE-007).
- Does not implement the pairing flow (DevicePairingPageComponent, FE-EDGE-011).

**Layer(s):**
frontend / libs/edge/feature-device-detail

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2 DeviceDetailPageComponent)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-04-data-access.md` (§3 DeviceFacade)

**Architecture alignment:**
- `standalone: true`, `OnPush`, signals read via `DeviceFacade`
- Tag Nx: `scope:edge`, `type:feature`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-device-detail/src/lib/device-detail-page/device-detail-page.component.ts`
- 🔲 `libs/edge/feature-device-detail/src/lib/device-detail-page/device-detail-page.component.html`
- 🔲 `libs/edge/feature-device-detail/src/index.ts` (re-export DeviceDetailPageComponent)

**Tests**
- 🔲 Unit: `deviceId=null` → `resolveForActivePatient()` called on init
- 🔲 Unit: `deviceId='abc'` → `load('abc')` called on init
- 🔲 Unit: `stopPolling()` called on `ngOnDestroy`
- 🔲 Unit: `(unpair)` output → confirm dialog opened; on cancel: `unpair()` NOT called; on confirm: `unpair(reason)` called
- 🔲 Unit: `(retire)` output → confirm dialog; on confirm: `retire()` called
- 🔲 `nx test edge-feature-device-detail` PASS
- 🔲 `nx lint edge-feature-device-detail` PASS

**Milestone:**
Wave P3 / Feature Libs — Device Detail

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P3

**Acceptance criteria:**
- `npx nx test edge-feature-device-detail` PASS
- `npx nx lint edge-feature-device-detail` PASS
- `stopPolling()` spy called on destroy (no leaked timer)
- Confirm dialog gates unpair/retire: cancel does not dispatch; confirm does

**Dependencies:**
- FE-EDGE-007 (DeviceFacade)
- FE-EDGE-012 (ConnectivityStatusCardComponent)
- FE-EDGE-013 (DeviceMetaListComponent, DeviceActionsComponent)
- FE-EDGE-018 (loading/empty states)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Indirectly via DeviceFacade: `GET .../device`, `GET .../liveness`, `DELETE .../pairings/current`, `POST .../:retire`

**Testing / Evidence required:**
- Timer leak: `stopPolling()` spy called on destroy
- Confirm gate: two separate unit tests (cancel path + confirm path) for unpair

**Risks / Notes:**
- Confirm dialog library: use `@neuroassist/shared/ui-feedback` dialog or Angular CDK dialog — confirm
  which is available before implementation. Stub it in tests.
