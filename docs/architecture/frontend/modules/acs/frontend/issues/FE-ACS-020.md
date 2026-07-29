## [FE-ACS-020] TenantSetupComponent (smart + TenantFormComponent + TenantSaveStatusComponent)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`TenantSetupComponent` (standalone, OnPush) — the ACS onboarding wizard step 1 ("Empresa"). Composed inside
the `scope:web` shell page, not routed. Reads `tenant` and `saveState` signals from `AcsTenantFacade`,
dispatches `createTenant(nome, tipo)`. On `201 active` writes empresa-context; on `202` shows provisioning
state. Two presentational components: `TenantFormComponent` and `TenantSaveStatusComponent`.

**Context / Problem statement:**
- SPEC-02 §4 defines the component tree for the tenant setup step.
- `TenantSetupComponent` MUST NOT contribute a breadcrumb (onboarding shell owns the wizard trail — SPEC-06 §2).
- `TenantSetupComponent` exposes via `@Output() stepComplete: EventEmitter` so the wizard shell can advance.
- `TenantFormComponent`: `@Input() value: TenantVM|null`; `@Output() change`; fields: nome + tipo selector.
- `TenantSaveStatusComponent`: `@Input() saveState: TenantSaveState`; `@Output() retry`.
- Bloqueia: INT-FE-ACS-004 (wizard composition exposes this component)

**Approach (execution path):**
1. Create `libs/acs/feature-tenant-setup/src/lib/tenant-setup.component.ts` (standalone, OnPush).
2. Inject `AcsTenantFacade`.
3. Bind `tenant()` + `saveState()` signals from facade.
4. `(change)` from `TenantFormComponent` → patch local draft state.
5. `(submit)` → `facade.createTenant(nome, tipo)`.
6. On `saveState() === 'active'` → emit `stepComplete`.
7. `TenantFormComponent`: `@Input() value`; `@Output() change`; two fields (nome, tipo selector).
8. `TenantSaveStatusComponent`: shows state label (idle/saving/provisioning/active/error); retry on error.
9. No breadcrumb contribution (call `acsNavService.clearBreadcrumb()` in init).
10. Unit tests: createTenant called; provisioning state shown; empresa-context written on active; no breadcrumb.

**Non-goals:**
- No routing (composed step, not routed).
- No auth logic.
- No PAC/EDGE/GEST imports.

**Layer(s):**
frontend / libs/acs/feature-tenant-setup

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§4)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§4 TenantVM, TenantSaveState)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3 no breadcrumb)

**Architecture alignment:**
- Standalone, OnPush, Signals; composed (not routed); no breadcrumb contribution
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-tenant-setup/`
- 🔲 `src/lib/tenant-setup.component.ts` — smart, no breadcrumb, stepComplete output
- 🔲 `src/lib/tenant-setup.component.html`
- 🔲 `src/lib/components/tenant-form.component.ts` + `.html` (nome + tipo)
- 🔲 `src/lib/components/tenant-save-status.component.ts` + `.html` (state label + retry)
- 🔲 `src/index.ts` — re-exports TenantSetupComponent (for wizard composition)

**Tests**
- 🔲 `src/lib/tenant-setup.component.spec.ts` — createTenant; 202 provisioning state; active → stepComplete; no breadcrumb
- 🔲 `nx test acs-feature-tenant-setup` PASS
- 🔲 `nx lint acs-feature-tenant-setup` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-tenant-setup` PASS
- `npx nx lint acs-feature-tenant-setup` PASS
- `saveState = 'provisioning'` → provisioning state shown; empresa-context NOT escrito
- `saveState = 'active'` → `stepComplete` emitido; empresa-context escrito
- Nenhum ACS breadcrumb contribuído durante o step
- E2E tenant-onboarding.feature scenarios 1-5 suportados

**Dependencies:**
- FE-ACS-007 (AcsTenantFacade)
- FE-ACS-006 (clearBreadcrumb para wizard context)

**Data impact:**
- Escreve empresa-context apenas no estado active (via AcsTenantFacade)

**API / Contract impact:**
- Via AcsTenantFacade: `POST /api/v1/acs/tenants`

**Testing / Evidence required:**
- Screenshot: step 1 do wizard (form de empresa)
- Screenshot: estado provisioning (spinner/banner)
- Screenshot: estado active (avança para próximo step)

**Risks / Notes:**
- `TenantSetupComponent` é o único componente ACS que opera ANTES de um tenant ativo existir —
  portanto não deve enviar `X-Empresa-Id`. Verificar que o interceptor respeita requests de tenant provisioning.
