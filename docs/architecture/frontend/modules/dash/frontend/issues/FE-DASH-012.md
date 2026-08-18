## [FE-DASH-012] PatientHeaderComponent + QuickLinkListComponent — ui apresentacionais (feature-home)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/ui` contém `PatientHeaderComponent` (nome do paciente + slot do badge de dispositivo) e `QuickLinkListComponent` (links rápidos para HIST), ambos presentacionais puros.

**Context / Problem statement:**
- `PatientHeaderComponent` recebe `DeviceBadgeVm` injetado do shell (EDGE signal via scope:web — INT-FE-DASH-002), não importa scope:edge diretamente.
- `QuickLinkListComponent` emite `navigate` output; o container (HomeStatusPageComponent) faz a navegação para HIST via shell router.
- Fonte: SPEC-02 (feature-home).

**Approach (execution path):**
1. `PatientHeaderComponent` (standalone, OnPush):
   - `input()`: `patient: PatientHeaderVm`, `device: DeviceBadgeVm`.
   - Renderiza nome do paciente + status do dispositivo (online/offline/unknown + `degraded` chip).
2. `QuickLinkListComponent` (standalone, OnPush):
   - `input()`: `links: QuickLinkVm[]`.
   - `output()`: `navigate: OutputEmitterRef<string>` (patientId ou rota alvo).
   - Renderiza lista de links (ex: "Ver histórico de hoje").
3. Ambos em `libs/dash/ui` (type:ui, scope:dash).
4. Adicionar ao barrel `index.ts` de `dash/ui`.

**Non-goals:**
- Não importar scope:edge nem scope:hist.
- Não fazer HTTP.

**Layer(s):**
frontend / libs/dash/ui (type:ui)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-home)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1 DeviceBadgeVm, PatientSummaryVm)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/patient-header/patient-header.component.ts`
- 🔲 `libs/dash/ui/src/lib/patient-header/patient-header.component.html`
- 🔲 `libs/dash/ui/src/lib/quick-link-list/quick-link-list.component.ts`
- 🔲 `libs/dash/ui/src/lib/quick-link-list/quick-link-list.component.html`
- 🔲 `libs/dash/ui/src/index.ts` (barrel — adicionar exports)

**Tests**
- 🔲 `npx nx test dash-ui` PASS (testar: DeviceBadgeVm degraded renderiza chip; navigate output emitido; device=unknown renderiza "desconhecido")
- 🔲 `npx nx lint dash-ui` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:ui
layer:frontend
scope:dash
priority:P3

**Acceptance criteria:**
- `npx nx test dash-ui` PASS
- `npx nx lint dash-ui` PASS
- `npx nx build dash-ui` PASS
- `device.status='unknown'` renderiza "desconhecido" (degraded chip)
- `device.degraded=true` adiciona chip de degradação
- `navigate` output emite ao clicar em link

**Dependencies:**
- FE-DASH-010 (HomeStatusPageComponent usa estes componentes)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshots: dispositivo online, offline, unknown/degraded

**Risks / Notes:**
- `DeviceBadgeVm` vem do EDGE signal injetado no scope:web (INT-FE-DASH-002); em testes unitários de DASH, mockar o input diretamente.
