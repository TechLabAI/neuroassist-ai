## [FE-EDGE-018] Loading skeleton + empty states (resolving device, not-paired, device-not-found)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Device detail page has three intermediate/empty visual states beyond the happy path:
1. **Resolving skeleton** — while `DeviceFacade.detail() === null` (first load), a skeleton placeholder
   prevents layout shift.
2. **Not-paired empty state** — when `derivedState = 'not-paired'`: friendly message + CTA to start pairing
   (conductor only; read-only caregivers see an informational message).
3. **Device-not-found** — when a `404 /errors/device-not-found` response is returned on an explicit
   deviceId load: an error banner with a "voltar" link.

**Context / Problem statement:**
- Without a skeleton, the detail page shows a flash of empty content on first load (poor UX + a11y issue).
- The `not-paired` state is the normal "new patient" state — it must be welcoming, not an error (SPEC-07 §2).
- A caregiver (without manage permission) in `not-paired` state sees an informational message ("Aguardando
  o dispositivo ser configurado pelo responsável técnico"), NOT a pairing CTA.
- Coverage gap G3 (from FE Specs-Validate M4): the `not-paired` informational path for caregivers was not
  fully specified — this issue closes it.

**Approach (execution path):**
1. In `DeviceDetailPageComponent` template (FE-EDGE-014):
   - Add `@if (device.detail() === null && !device.derivedState())` → render skeleton.
   - Add `@if (device.derivedState() === 'not-paired')` → render not-paired state.
   - On `404 device-not-found` error: set a local `errorBanner` signal; render banner + back link.
2. Create `libs/edge/feature-device-detail/src/lib/device-not-paired/device-not-paired.component.ts`:
   - `@Input() canManage: boolean`
   - Render conductor CTA: "Configurar dispositivo" → navigate to `/edge/pairing`.
   - Render caregiver message: "Aguardando o dispositivo ser configurado pelo responsável técnico."
3. Skeleton: reuse `@neuroassist/shared/ui-feedback` skeleton directives (card + list skeletons).

**Non-goals:**
- Does not cover connectivity `unknown`/`offline` degradation (those are chip states, not empty states).
- Does not implement the pairing flow itself.

**Layer(s):**
frontend / libs/edge/feature-device-detail

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-07-error-handling.md` (§2 not-paired, §1 device-not-found)

**Architecture alignment:**
- `standalone: true`, `OnPush`
- Tag Nx: `scope:edge`, `type:feature`
- Closes coverage gap G3 (not-paired caregiver informational path)

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-device-detail/src/lib/device-not-paired/device-not-paired.component.ts`
- 🔲 `libs/edge/feature-device-detail/src/lib/device-not-paired/device-not-paired.component.html`
- 🔲 Update `device-detail-page.component.html` (skeleton + not-paired + error-banner branches)

**Tests**
- 🔲 Unit: `detail() === null` → skeleton present in DOM; no real data rendered
- 🔲 Unit: `derivedState='not-paired' + canManage=true` → "Configurar dispositivo" CTA present
- 🔲 Unit: `derivedState='not-paired' + canManage=false` → informational text, NO pairing CTA
- 🔲 Unit: `errorBanner.set('device-not-found')` → error banner + "voltar" link visible
- 🔲 `nx test edge-feature-device-detail` PASS
- 🔲 `nx lint edge-feature-device-detail` PASS

**Milestone:**
Wave P7 / UX Polish

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P7

**Acceptance criteria:**
- `npx nx test edge-feature-device-detail` PASS
- `npx nx lint edge-feature-device-detail` PASS
- Not-paired caregiver: no pairing CTA visible (DOM assertion)
- Skeleton: card + list placeholders visible while `detail() === null`
- Coverage gap G3 closed: caregiver sees informational message, not a broken CTA

**Dependencies:**
- FE-EDGE-014 (DeviceDetailPageComponent — skeleton + empty state added to its template)
- FE-EDGE-015 (edgeConductorGuard — `canManage` flag flows from facade)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `404 /errors/device-not-found` handled at this layer (banner, no re-throw)

**Testing / Evidence required:**
- G3 closure: caregiver not-paired test with `canManage=false` confirms no pairing CTA
- 404 error banner: mock 404 response → banner with back link visible

**Risks / Notes:**
- The "voltar" link target on `device-not-found` must be confirmed with UX (back to previous route vs.
  dashboard). Use `Location.back()` as a safe default.
