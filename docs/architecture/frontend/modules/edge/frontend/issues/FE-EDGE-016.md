## [FE-EDGE-016] libs/edge/navigation — breadcrumb state (thin host only; no contribution when composed)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/edge/navigation` contributes breadcrumb entries only for EDGE's own thin-host routes. When EDGE
surfaces are composed (topbar chip, onboarding step 3), no breadcrumb is contributed — the host page owns
the trail (SPEC-01 §2, SPEC-06 §3).

**Context / Problem statement:**
- Three breadcrumb paths exist on EDGE's own routes (SPEC-01 table):
  - `/edge/device` → `Dispositivo`
  - `/edge/device/:deviceId` → `Dispositivo › Detalhes`
  - `/edge/pairing` → `Dispositivo › Parear`
- When `DevicePairingPageComponent` is composed into onboarding step 3 (`scope:web`), the component emits
  NO breadcrumb — the host owns the trail. `EdgeShellIntegrationService` must suppress the breadcrumb
  contribution in the composed context.
- This library is `type:domain` (per generator-config.yml); it depends only on shared breadcrumb service
  and the route data.

**Approach (execution path):**
1. Create `libs/edge/navigation/src/lib/edge-breadcrumb.service.ts`.
2. On route change within `/edge/*`, read `ActivatedRouteSnapshot.data.breadcrumb` (set in EDGE_ROUTES).
3. Push `{ label: 'Dispositivo', url: '/edge/device' }` etc. to the shared `BreadcrumbService`.
4. When in composed context (no EDGE route active): do not push any breadcrumb (service is idle).
5. Export from `libs/edge/navigation/src/index.ts`.

**Non-goals:**
- Does not implement the shared `BreadcrumbService` (from `@neuroassist/shared/ui-layout`).
- Does not render breadcrumbs in the template (host shell renders them).

**Layer(s):**
frontend / libs/edge/navigation

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-01-route-map.md` (§2 Breadcrumb column)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:domain`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/navigation/src/lib/edge-breadcrumb.service.ts`
- 🔲 `libs/edge/navigation/src/index.ts` (re-export)
- 🔲 Update `EDGE_ROUTES` route data: `data: { breadcrumb: 'Dispositivo' }` for each own route (FE-EDGE-002)

**Tests**
- 🔲 Unit: navigate to `/edge/device` → BreadcrumbService.push called with `[{label:'Dispositivo'}]`
- 🔲 Unit: navigate to `/edge/pairing` → BreadcrumbService.push called with `[{label:'Dispositivo'}, {label:'Parear'}]`
- 🔲 Unit: no EDGE route active → BreadcrumbService.push NOT called
- 🔲 `nx test edge-navigation` PASS
- 🔲 `nx lint edge-navigation` PASS

**Milestone:**
Wave P6 / Navigation

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P6

**Acceptance criteria:**
- `npx nx test edge-navigation` PASS
- `npx nx lint edge-navigation` PASS
- Composed context: BreadcrumbService.push not called when no `/edge/*` route is active
- `/edge/pairing` breadcrumb: 2-segment trail asserted in unit test

**Dependencies:**
- FE-EDGE-002 (EDGE_ROUTES — route data property for breadcrumb text)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None

**Testing / Evidence required:**
- BreadcrumbService spy confirming correct trail for each of the 3 EDGE routes

**Risks / Notes:**
- Confirm `BreadcrumbService` interface with `@neuroassist/shared/ui-layout` before implementing.
  If not yet available, stub with a push-only interface.
