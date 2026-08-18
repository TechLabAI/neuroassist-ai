## [FE-DASH-027] e2e Playwright: calibration write-through (SPEC-04 §2)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/calibration-writethrough.spec.ts` cobre os cenários do feature file existente: salvar calibração → PUT proxy para PAC; falha → mensagem de erro relayed (toast); sem retry automático.

**Context / Problem statement:**
- Feature file existente: `docs/architecture/frontend/modules/dash/frontend/e2e/calibration-writethrough.feature`.
- Write-through: DASH faz proxy da calibração para PAC (dono do dado); não armazena nada localmente.
- Fonte: SPEC-04 §2; SPEC-07 §2 (`#upstream-rejected`).

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/calibration-writethrough.spec.ts`.
2. Cenários do feature file original.
3. Cenário sucesso: preencher form de calibração → verificar `PUT /panel/patients/.../calibration` disparado; form fecha sem erro.
4. Cenário falha: simular resposta `#upstream-rejected` → verificar toast com mensagem relayed; sem armazenamento local.
5. Cenário 422: simular `#validation` → verificar inline field error; sem re-submit automático.

**Non-goals:**
- Não testar o backend PAC.

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/e2e/calibration-writethrough.feature`
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/calibration-writethrough.spec.ts`

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec calibration-writethrough` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec calibration-writethrough` PASS
- Sucesso → `PUT` disparado; nenhum dado armazenado localmente
- `#upstream-rejected` → toast com mensagem relayed
- `#validation` 422 → inline field error; sem re-submit

**Dependencies:**
- FE-DASH-005 (DashApiService — saveCalibration)
- INT-FE-DASH-004 (write-through PAC)

**Data impact:**
- Nenhum (DASH não persiste calibração)

**API / Contract impact:**
- Mocks de `PUT /panel/patients/{patientId}/commands/{commandId}/calibration`

**Testing / Evidence required:**
- Screenshot: form de calibração com erro inline (422)
- Screenshot: toast após upstream-rejected

**Risks / Notes:**
- Verificar se o form de calibração está na feature-home ou feature-notifications; se não implementado ainda, esta issue pode ser bloqueada até o componente existir.
