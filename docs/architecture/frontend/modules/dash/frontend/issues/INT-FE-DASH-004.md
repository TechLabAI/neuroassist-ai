## [INT-FE-DASH-004] Write-through calibração → PAC (PUT /panel/patients/.../calibration)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
O DASH FE faz proxy de escrita de calibração para PAC via `DashApiService.saveCalibration()`, sem armazenar o valor localmente. Em caso de falha, propaga o erro do upstream (toast com mensagem relayed ou field error 422).

**Context / Problem statement:**
- PAC é o dono da calibração; DASH apenas roteia a escrita via BFF (`dash-service`).
- DASH não armazena calibração localmente — resultado do write-through é o autoritativo (ADR-006).
- Fonte: SPEC-04 §2; SPEC-07 §2 (`#upstream-rejected`, `#validation`).

**Approach (execution path):**
1. `DashApiService.saveCalibration(patientId, commandId, req: CalibrationRequest)` já definido em FE-DASH-005.
2. Criar (ou completar) o componente/form de calibração que usa este método — provavelmente em `feature-home` ou `feature-notifications`.
3. Em sucesso: fechar form/modal; nenhum valor armazenado localmente.
4. Em `#upstream-rejected`: mostrar toast com a mensagem relayed do upstream.
5. Em `#validation` (422): mostrar inline field error; sem re-submit automático.
6. Sem auto-retry.

**Non-goals:**
- Não armazenar calibração no frontend.
- Não modificar PAC service.

**Layer(s):**
frontend / libs/dash/feature-home ou feature-notifications (type:feature)

**Bounded context:**
dash ↔ pac (write-through via dash-service BFF)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§2 Calibration row)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2)

**Architecture alignment:**
- Write-through pattern; sem estado local
- Tag Nx: `scope:dash`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/dash/feature-home (ou feature-notifications)**
- 🔲 Componente de calibração (form + submit) — criar ou completar
- 🔲 Tratamento de erro: toast para #upstream-rejected, inline para #validation

**Tests**
- 🔲 `npx nx test dash-feature-home` PASS (saveCalibration chamado; erro 422 → field error; upstream-rejected → toast)
- 🔲 `npx nx lint dash-feature-home` PASS

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:dash
priority:P4

**Acceptance criteria:**
- `npx nx test dash-feature-home` PASS
- `npx nx lint dash-feature-home` PASS
- `npx nx build dash-feature-home` PASS
- Sucesso: nenhum valor de calibração em localStorage ou state local
- `#upstream-rejected` → toast com mensagem relayed
- `#validation` → inline field error; sem re-submit
- e2e coberto por FE-DASH-027

**Dependencies:**
- FE-DASH-005 (DashApiService.saveCalibration)

**Data impact:**
- Nenhum (DASH não persiste calibração)

**API / Contract impact:**
- `PUT /panel/patients/{patientId}/commands/{commandId}/calibration`

**Testing / Evidence required:**
- Test com DashApiService mockado (sucesso + 422 + 500)
- e2e: FE-DASH-027

**Risks / Notes:**
- Verificar onde o form de calibração mora na UX (identificar a tela no protótipo BA antes de criar o componente).
