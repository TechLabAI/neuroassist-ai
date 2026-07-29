## [FE-ACS-015] CaregiverRowComponent + badge components (CoverageBadge, RoleBadge, StatusBadge) in acs/ui

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`CaregiverRowComponent` and three badge sub-components live in `libs/acs/ui` (the shared ui lib within ACS).
`CaregiverRowComponent` renders a single roster row with action buttons (edit, deactivate, manageLinks);
`deactivate` is disabled when `row.isLastCovering === true`. All three badge components (CoverageBadge,
RoleBadge, StatusBadge) are purely visual: icon + label, never color-only.

**Context / Problem statement:**
- SPEC-02 §1 and §5 define `libs/acs/ui` components: RoleBadge, StatusBadge, CoverageBadge, CaregiverRow.
- `CoverageBadgeComponent` must use icon + label, not just color (accessibility — ADR-005).
- `CaregiverRowComponent.deactivate` output is conditionally disabled when `row.isLastCovering` (ADR-005 guard).
- These components live in `acs/ui` (not in feature libs) because they may be reused across roster and linkage.
- Bloqueia: FE-ACS-013 (roster renders CaregiverRowComponent)

**Approach (execution path):**
1. Create `libs/acs/ui/src/lib/`:
   - `role-badge/role-badge.component.ts` — `@Input() papel: 'cuidadora' | 'coordenador_clinica'`
   - `status-badge/status-badge.component.ts` — `@Input() status: 'active' | 'inactive'`
   - `coverage-badge/coverage-badge.component.ts` — `@Input() coverage: 'coberto' | 'sem-cobertura'` (icon + label)
   - `caregiver-row/caregiver-row.component.ts` — `@Input() row: CuidadorRowVM`; 3 outputs + deactivate disabled
2. Each badge: standalone, OnPush, pure presentational.
3. `CaregiverRowComponent`: disable deactivate button when `row.isLastCovering === true`; show blocking reason.
4. Unit tests: badges render correct label for each value; deactivate disabled on isLastCovering.

**Non-goals:**
- No facade access anywhere in `acs/ui`.
- No routing in these components.

**Layer(s):**
frontend / libs/acs/ui

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1 leaf nodes, §5)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§1 CuidadorRowVM)

**Architecture alignment:**
- Standalone, OnPush; @Input/@Output only; icon + label for accessibility
- Tags Nx: `scope:acs`, `type:ui`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/ui/`
- 🔲 `src/lib/role-badge/role-badge.component.ts` + `.html`
- 🔲 `src/lib/status-badge/status-badge.component.ts` + `.html`
- 🔲 `src/lib/coverage-badge/coverage-badge.component.ts` + `.html` (icon + label, not color-only)
- 🔲 `src/lib/caregiver-row/caregiver-row.component.ts` + `.html` (edit/deactivate/manageLinks; deactivate disabled on isLastCovering)
- 🔲 `src/index.ts` — re-exports all 4 components

**Tests**
- 🔲 `*.component.spec.ts` — badge labels; deactivate button disabled state
- 🔲 `nx test acs-ui` PASS
- 🔲 `nx lint acs-ui` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-ui` PASS
- `npx nx lint acs-ui` PASS
- `CoverageBadge(coverage='sem-cobertura')` → ícone + label "Sem cobertura" (não só cor)
- `CaregiverRow(row.isLastCovering=true)` → botão desativar desabilitado + reason surfaced
- Badges passam por inspeção de acessibilidade (texto visível, não só cor)

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- FE-ACS-010 (CuidadorRowVM type, via data-access)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshots de cada badge (active/inactive, coberto/sem-cobertura, cuidadora/coordenador)
- Teste de disabled state no deactivate button

**Risks / Notes:**
- Os ícones devem vir de `@neuroassist/shared/ui-tokens` (icon system). Verificar se `group`, `check`, `warning`
  estão disponíveis no token set antes de implementar.
