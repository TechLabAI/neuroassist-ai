## [INT-FE-DASH-006] HIST cross-module navigation — quick-link de paciente (scope:web)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`QuickLinkListComponent` (ou `DashHomeFacade.viewHistory`) navega para a rota de HIST correspondente via shell router, sem que `scope:dash` importe `scope:hist`. A navegação preserva o `patientId` como parâmetro de rota.

**Context / Problem statement:**
- "Ver histórico de hoje" é o quick-link primário do Home (SPEC-02 feature-home tree).
- `scope:dash` não importa `scope:hist` — usa apenas o shell router com a rota canônica de HIST.
- Fonte: SPEC-02 (feature-home → QuickLinkListComponent); SPEC-04 §2 (History row).

**Approach (execution path):**
1. `DashHomeFacade.viewHistory(patientId: string)`: usa `Router.navigate(['/historico', patientId])` (ou a rota canônica definida por HIST).
2. `QuickLinkListComponent` emite `navigate` output com o patientId → `HomeStatusPageComponent` chama `facade.viewHistory(patientId)`.
3. Em `apps/neuroassist-ai-web`, verificar que a rota de HIST está registrada (ex: `/historico` via `HIST_MODULE_DESCRIPTOR`).
4. Não importar nenhuma API ou componente de scope:hist no DASH.

**Non-goals:**
- Não implementar a tela de histórico (HIST).
- Não fazer HTTP para buscar histórico no DASH.

**Layer(s):**
frontend / libs/dash/data-access (type:data-access) + apps/neuroassist-ai-web (scope:web)

**Bounded context:**
dash → hist (navegação via shell router)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-home — QuickLinkListComponent)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§2 History row)

**Architecture alignment:**
- Shell router navigation; Nx boundary preservada
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-home.facade.ts` (implementar viewHistory via Router)

**App: apps/neuroassist-ai-web**
- 🔲 Verificar que rota de HIST está registrada no app-routing (não criar — apenas confirmar pré-requisito)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (viewHistory chama Router.navigate com rota correta; sem import de scope:hist)
- 🔲 `npx nx lint dash-data-access` PASS (boundary: dash não importa hist)

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:dash
priority:P4

**Acceptance criteria:**
- `npx nx test dash-data-access` PASS
- `npx nx lint dash-data-access` PASS
- Clicar em "Ver histórico de hoje" → router navega para rota de HIST com patientId
- `scope:dash` não importa `scope:hist`

**Dependencies:**
- FE-DASH-012 (QuickLinkListComponent — emite navigate output)
- FE-DASH-010 (HomeStatusPageComponent — chama viewHistory)
- HIST FE scaffold (rota de HIST deve existir no app-routing)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (navegação de rota)

**Testing / Evidence required:**
- Test com Router mockado: viewHistory('p1') → navigate(['/historico', 'p1'])
- Lint boundary test

**Risks / Notes:**
- A rota canônica de HIST (ex: `/historico/:patientId`) precisa ser confirmada quando HIST FE for scaffoldado; atualizar a URL canônica aqui se necessário.
