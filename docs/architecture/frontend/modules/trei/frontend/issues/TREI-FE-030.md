## [TREI-FE-030] e2e Playwright: erros-e-degradacao.feature (G4 patient-not-found + G5 correlationId not-rendered)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/trei-e2e/src/erros-e-degradacao.spec.ts` cobre os cenários de erro da feature `erros-e-degradacao.feature`, incluindo: `patient-not-found 404` ao iniciar sessão (G4) — banner visível, sessão não criada; e validação de que o `correlationId` nunca aparece como texto bruto na UI (G5).

**Context / Problem statement:**
- **G4 (gap):** `POST /sessoes` responde `404 patient-not-found` → banner "Paciente não encontrado nesta empresa." na superfície de início, `status` permanece `'idle'`, nenhuma sessão criada.
- **G5 (gap):** todos os erros mapeados incluem `correlationId` em `AppError.metadata` para logging, mas **nenhuma** mensagem exibida ao utilizador contém o UUID/correlationId como texto — o teste verifica que o DOM não contém o UUID do `correlationId`.
- Também cobre: `llm-unavailable 503` → banner retriable, `forbidden-scope 403` → toast sem retry.

**Approach (execution path):**
1. Criar `apps/trei-e2e/src/erros-e-degradacao.spec.ts`.
2. G4: `page.route('/api/v1/trei/sessoes', route => route.fulfill({ status: 404, body: JSON.stringify({ type: 'https://neuroassist.ai/problems/patient-not-found', correlationId: 'corr-123' }) }))` → verificar banner; verificar `page.url()` não muda para `/treinamento/sessao`.
3. G5: após qualquer erro, `expect(page.locator('body')).not.toContainText('corr-123')` — o `correlationId` não aparece no DOM.
4. `llm-unavailable`: mock 503 → banner retriable com botão "Tentar novamente" visível.

**Non-goals:**
- Não testar erros de plataforma (503 de infra genérica) — apenas os erros mapeados do `trei-service`.

**Layer(s):**
frontend / apps/trei-e2e

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/e2e/erros-e-degradacao.feature`
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§2)

**Architecture alignment:**
- Playwright + page.route()
- Tag Nx: `scope:trei`, `type:e2e`

**Implementation checklist (by file)**

**apps/trei-e2e**
- [ ] `apps/trei-e2e/src/erros-e-degradacao.spec.ts`
- [ ] G4: `patient-not-found` → banner + status idle
- [ ] G5: `correlationId` ausente do DOM após qualquer erro
- [ ] `llm-unavailable`: banner retriable

**Tests**
- [ ] `nx e2e trei-e2e --grep "erros-e-degradacao"` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:trei
priority:P9

**Acceptance criteria:**
- `npx nx e2e trei-e2e` PASS
- G4: banner "Paciente não encontrado" visível; URL permanece `/treinamento/sessao`
- G5: `page.locator('body').not.toContainText(correlationIdValue)` PASS para todos os erros testados
- `llm-unavailable`: botão "Tentar novamente" presente no banner

**Dependencies:**
- TREI-FE-025 (patient-not-found banner — G4)
- TREI-FE-012 (TreiErrorMapper — G5)
- TREI-FE-016 (ConversationErrorPanelComponent — llm-unavailable)

**Data impact:**
- Nenhum (mocks)

**API / Contract impact:**
- Mocks de erros RFC 7807: `patient-not-found`, `llm-unavailable`, `forbidden-scope`

**Testing / Evidence required:**
- Screenshot: banner "Paciente não encontrado" no estado idle (G4)
- Screenshot: nenhum UUID visível na UI após erro (G5)

**Risks / Notes:**
- G5: usar um `correlationId` fixo no mock (ex. `'corr-abc-123'`) para que o `not.toContainText('corr-abc-123')` seja determinístico no teste.
