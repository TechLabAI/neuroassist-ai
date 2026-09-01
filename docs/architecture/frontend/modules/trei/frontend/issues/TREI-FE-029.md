## [TREI-FE-029] e2e Playwright: apenas-lado-cuidadora.feature (INV-1) + guard G1 + deep-link G2

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/trei-e2e/src/apenas-lado-cuidadora.spec.ts` cobre: ausência de qualquer controlo de aprovação do paciente na UI (INV-1), comportamento do `treiProporGuard` sem scope `propor` (G1 — review surface acessível), e deep-link para `/treinamento/sessao/propostas` sem turno activo (G2).

**Context / Problem statement:**
- **INV-1:** nenhum botão/toggle no TREI FE escreve `confirmadoPaciente` — o e2e verifica que não existe tal elemento no DOM.
- **G1 (gap):** cuidadora sem scope `treinamento.no.propor` → acede a `/treinamento/sessao` (review surface visível), mas é redirecionada de `/treinamento/sessao/propostas` para `/treinamento/sessao` ao tentar propor/editar.
- **G2 (gap):** deep-link directo a `/treinamento/sessao/propostas` sem turno activo → proposals pré-existentes renderizadas.
- Estes três cenários estão documentados em `apenas-lado-cuidadora.feature`.

**Approach (execution path):**
1. Criar `apps/trei-e2e/src/apenas-lado-cuidadora.spec.ts`.
2. INV-1: navegar para a review surface → `expect(page.locator('[data-testid="patient-approve-btn"]')).not.toBeVisible()`.
3. G1: mock JWT sem scope `propor`, navegar para `/treinamento/sessao/propostas` → redirect para `/treinamento/sessao`. Verificar que os proposals existentes são visíveis na sessão.
4. G2: mock de proposals pré-existentes, navegar directamente para `/treinamento/sessao/propostas` → lista de proposals renderizada sem `POST /sessoes` em voo.

**Non-goals:**
- Não testar autenticação completa (mock de JWT).

**Layer(s):**
frontend / apps/trei-e2e

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/e2e/apenas-lado-cuidadora.feature`
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§3 — guards G1/G2)

**Architecture alignment:**
- Playwright + page.route() + mock JWT
- Tag Nx: `scope:trei`, `type:e2e`

**Implementation checklist (by file)**

**apps/trei-e2e**
- [ ] `apps/trei-e2e/src/apenas-lado-cuidadora.spec.ts`
- [ ] INV-1: nenhum botão de aprovação do paciente no DOM
- [ ] G1: redirect para `/treinamento/sessao` sem scope `propor`
- [ ] G2: deep-link `/treinamento/sessao/propostas` sem sessão activa → proposals visíveis

**Tests**
- [ ] `nx e2e trei-e2e --grep "apenas-lado-cuidadora"` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:trei
priority:P9

**Acceptance criteria:**
- `npx nx e2e trei-e2e` PASS
- INV-1: `[data-testid="patient-approve-btn"]` not visible/not exist (G5)
- G1: URL após navegação sem scope `propor` = `/treinamento/sessao`
- G2: proposals visíveis após deep-link sem turno activo

**Dependencies:**
- TREI-FE-023 (treiProporGuard — G1)
- TREI-FE-018 (ProposalReviewPageComponent — G2)
- TREI-FE-019 (ProposedNodeCardComponent — INV-1)

**Data impact:**
- Nenhum (mocks)

**API / Contract impact:**
- Mock JWT sem scope `propor` (G1)

**Testing / Evidence required:**
- Screenshot: review surface sem botão de aprovação do paciente (INV-1)
- Screenshot: URL = `/treinamento/sessao` após redirect do guard (G1)
- Screenshot: proposals listadas em deep-link (G2)

**Risks / Notes:**
- G1: o mock de JWT precisa de simular a verificação de scope no guard — pode ser feito sobrepondo o `CaregiverSessionService` no TestBed ou via intercept de route de autenticação no Playwright.
