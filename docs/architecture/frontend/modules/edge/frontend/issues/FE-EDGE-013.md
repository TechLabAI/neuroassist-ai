## [FE-EDGE-013] DeviceMetaListComponent + DeviceActionsComponent — metadata list and conductor-gated actions

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Two presentational components in `libs/edge/feature-device-detail`:
- `DeviceMetaListComponent` — renders device metadata (pairingState, ultimaSincronizacao, versao, profileDrift, stalenessThreshold).
- `DeviceActionsComponent` — renders re-pair / unpair / retire buttons; **hidden entirely** (not disabled) when `canManage=false`; `rePairEnabled` flag gates re-pair/unpair actions.

**Context / Problem statement:**
- A caregiver must never see an affordance she cannot use (SPEC-05 §5). Hiding (not disabling) prevents
  confusion and the false impression of a broken button.
- `DeviceMetaListComponent` surfaces `profileDrift` (running ≠ current profile version) as a warning badge —
  a critical maintenance signal for the conductor.
- `DeviceActionsComponent` emits three outputs: `rePair`, `unpair`, `retire`. The page component confirms
  destructive actions before dispatching (unpair/retire require confirmation — not part of this component).
- `rePairEnabled` feature flag controls visibility of re-pair AND unpair (both involve device replacement);
  `retire` is always gated only on `canManage`.
- Bloqueia: FE-EDGE-014 (DeviceDetailPageComponent)

**Approach (execution path):**
**DeviceMetaListComponent:**
1. Create component; `@Input() detail: DeviceDetailVM`.
2. Render: pairingState badge, ultimaSincronizacao (ISO → pt-BR date string), versao, profileDrift warning,
   stalenessThreshold in seconds.
3. `profileDrift=true` → "Perfil desatualizado" badge (warn tone, not just color).

**DeviceActionsComponent:**
1. Create component; `@Input() canManage: boolean`; `@Input() rePairEnabled: boolean`.
2. `@Output() rePair = new EventEmitter<void>()`; `@Output() unpair = new EventEmitter<void>()`; `@Output() retire = new EventEmitter<void>()`.
3. When `!canManage`: entire actions section hidden (`*ngIf` / `@if`), not disabled.
4. When `canManage && !rePairEnabled`: re-pair and unpair buttons hidden; retire remains.
5. When `canManage && rePairEnabled`: all three buttons visible.

**Non-goals:**
- Does not own confirm dialogs (DeviceDetailPageComponent responsibility).
- Does not dispatch to the facade (presentational only).

**Layer(s):**
frontend / libs/edge/feature-device-detail

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2 DeviceMetaListComponent, DeviceActionsComponent)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-05-tool-contract.md` (§5 RBAC of UI)

**Architecture alignment:**
- `standalone: true`, `OnPush`, purely presentational
- Tag Nx: `scope:edge`, `type:feature`
- Hidden (not disabled) for !canManage — WCAG guidance + UX clarity

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-device-detail/src/lib/device-meta-list/device-meta-list.component.ts`
- 🔲 `libs/edge/feature-device-detail/src/lib/device-meta-list/device-meta-list.component.html`
- 🔲 `libs/edge/feature-device-detail/src/lib/device-actions/device-actions.component.ts`
- 🔲 `libs/edge/feature-device-detail/src/lib/device-actions/device-actions.component.html`
- 🔲 `libs/edge/feature-device-detail/src/index.ts` (re-export both)

**Tests**
- 🔲 DeviceMetaListComponent: `profileDrift=true` → warn badge present; `profileDrift=false` → badge absent
- 🔲 DeviceActionsComponent: `canManage=false` → zero action buttons in DOM
- 🔲 DeviceActionsComponent: `canManage=true, rePairEnabled=false` → retire button present; re-pair absent
- 🔲 DeviceActionsComponent: `canManage=true, rePairEnabled=true` → all 3 buttons present
- 🔲 DeviceActionsComponent: `(rePair)` emitted on click; `(unpair)` emitted; `(retire)` emitted
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
- `canManage=false` → no buttons in DOM (hidden, not just `disabled` attribute)
- `profileDrift=true` → warn badge visible AND has accessible text (not color-only)

**Dependencies:**
- FE-EDGE-005 (DeviceDetailVM, DerivedState types)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (presentational)

**Testing / Evidence required:**
- DOM query: `canManage=false` → `querySelectorAll('button').length === 0` in actions section
- `profileDrift` badge has text content (not only CSS icon)

**Risks / Notes:**
- Confirm confirm-dialog UX (are unpair/retire destructive enough to require a modal?) with product before
  FE-EDGE-014 implementation. `DeviceActionsComponent` only emits the intent — the page component decides.
