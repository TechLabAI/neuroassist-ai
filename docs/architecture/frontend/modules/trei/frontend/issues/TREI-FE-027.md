## [TREI-FE-027] e2e Playwright: ciclo-de-sessao.feature + geracao-server-mediated.feature

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/trei-e2e/src/ciclo-de-sessao.spec.ts` e `apps/trei-e2e/src/geracao-server-mediated.spec.ts` implementam os cenários Playwright para o ciclo completo de sessão (início → conversação → encerramento) e para a autoria server-mediated (proposta LLM → materialização → estado dos nós).

**Context / Problem statement:**
- `ciclo-de-sessao.feature`: cobre o fluxo de início de sessão com paciente, submissão de turnos, indicador de "thinking", e encerramento com rascunhos preservados (INV-4).
- `geracao-server-mediated.feature`: cobre que as propostas LLM chegam do servidor (nunca fabricadas pelo FE — INV-2), a materialização de uma proposta em nó rascunho, e a exibição do `aiDisclosed` badge.
- As specs Gherkin estão em `docs/architecture/frontend/modules/trei/frontend/e2e/`.
- Os testes usam mocks de API (MSW ou Playwright route intercept) para simular o `trei-service`.

**Approach (execution path):**
1. Criar `apps/trei-e2e/src/ciclo-de-sessao.spec.ts`:
   - Setup: mock `POST /api/v1/trei/sessoes` → `{ sessaoId: 'sess-1', llmModelId: 'claude-3', aiDisclosed: true }`.
   - Cenários: iniciar sessão, verificar empty state → active, submit turn, encerrar sessão.
2. Criar `apps/trei-e2e/src/geracao-server-mediated.spec.ts`:
   - Mock: `POST /api/v1/trei/sessoes/{id}/propostas` → `{ propostas: [{propostaId:'p1', texto:'Beber água'}], aiDisclosed: true }`.
   - Cenários: turno submetido → proposals aparecem como nós inerts, materialização via `POST /nos`.
3. Usar `page.route()` do Playwright para interceptar as chamadas HTTP.

**Non-goals:**
- Não testar o backend real — mocks de API apenas.
- Não cobrir erros aqui (TREI-FE-030).

**Layer(s):**
frontend / apps/trei-e2e

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/e2e/ciclo-de-sessao.feature`
- `docs/architecture/frontend/modules/trei/frontend/e2e/geracao-server-mediated.feature`
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-08-e2e-scenarios.md`

**Architecture alignment:**
- Playwright com `page.route()` para API mocking
- Tag Nx: `scope:trei`, `type:e2e`

**Implementation checklist (by file)**

**apps/trei-e2e**
- [ ] `apps/trei-e2e/src/ciclo-de-sessao.spec.ts`
- [ ] `apps/trei-e2e/src/geracao-server-mediated.spec.ts`
- [ ] Helpers de mock de API em `apps/trei-e2e/src/helpers/api-mocks.ts`

**Tests**
- [ ] `nx e2e trei-e2e --grep "ciclo-de-sessao"` PASS
- [ ] `nx e2e trei-e2e --grep "geracao-server-mediated"` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:trei
priority:P9

**Acceptance criteria:**
- `npx nx e2e trei-e2e` PASS
- Cenário: após `startSession`, empty state desaparece e painel de conversa aparece
- Cenário: após submit de turno, proposals renderizadas como cards inerts (sem `nodeId`)
- INV-2: nenhuma bolha de assistente antes da resposta da API

**Dependencies:**
- TREI-FE-013 (TrainingSessionPageComponent)
- TREI-FE-014 (ChatConversationPanelComponent)

**Data impact:**
- Nenhum (testes com mocks)

**API / Contract impact:**
- Mocks de `POST /sessoes` e `POST /sessoes/{id}/propostas`

**Testing / Evidence required:**
- Screenshots dos estados de sessão (idle → active → ended)
- Relatório Playwright HTML

**Risks / Notes:**
- Confirmar que o app e2e TREI já está registado no `workspace.json` / `nx.json` (TREI-FE-004 cria as rotas; a configuração Nx do e2e pode precisar de setup adicional).
