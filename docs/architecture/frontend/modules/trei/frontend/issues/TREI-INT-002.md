## [TREI-INT-002] Integration com GWY — X-Correlation-Id header echoing + correlationId no AppError (G5)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
Todos os erros mapeados do `trei-service` incluem o `correlationId` echado pelo GWY (via `X-Correlation-Id` response header ou body `ProblemDetail.correlationId`) em `AppError.metadata.correlationId` — sem que o valor seja renderizado como texto bruto na UI (G5).

**Context / Problem statement:**
- O GWY (Spring Cloud Gateway) injecta e ecoa o `X-Correlation-Id` em cada request/response (api-contract §1.1, cross-link §3).
- O `ProblemDetail` do `trei-service` inclui `correlationId` no body do erro (api-contract §3).
- O `TreiErrorMapper` deve extrair `correlationId` do body e armazená-lo em `AppError.metadata.correlationId` para observabilidade/suporte — nunca em `AppError.message` (G5, SPEC-07 §4).
- Esta issue documenta e valida a integração — a implementação principal está em TREI-FE-012 (TreiErrorMapper).

**Approach (execution path):**
1. Confirmar que o `ProblemDetail` body do `trei-service` contém `correlationId` (string UUID) — verbatim do api-contract §3.
2. Em `TreiErrorMapper.map()` (TREI-FE-012): `appError.metadata = { correlationId: problemDetail.correlationId }`.
3. Confirmar que nenhum componente/template renderiza `appError.metadata.correlationId` como texto.
4. Documentar em `backend-api-cross-link.md §3` a política de correlação FE (já presente — verificar).

**Non-goals:**
- Não implementar injecção do `X-Correlation-Id` request header (responsabilidade do shared HTTP interceptor).
- Não exibir o `correlationId` na UI sob nenhuma circunstância.

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei → GWY

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/contracts/backend-api-cross-link.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§4)

**Architecture alignment:**
- `correlationId` em `AppError.metadata` (observabilidade), nunca em `message`
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `trei-error-mapper.ts` — `metadata.correlationId` extraído do body de erro
- [ ] Nenhum componente template renderiza `metadata.correlationId`

**Tests**
- [ ] `TreiErrorMapper`: mapeamento de `ProblemDetail` com `correlationId: 'corr-xyz'` → `AppError.metadata.correlationId === 'corr-xyz'`
- [ ] `AppError.message` não contém `'corr-xyz'`
- [ ] e2e G5: `page.locator('body').not.toContainText('corr-xyz')` (TREI-FE-030)

**Milestone:**
Wave 2 / Data Access (integração)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P2

**Acceptance criteria:**
- Mapper testa PASS com `correlationId` em `metadata`
- Nenhum template TREI contém `error.metadata.correlationId` num binding de texto
- e2e G5 PASS (TREI-FE-030)

**Dependencies:**
- TREI-FE-012 (TreiErrorMapper — implementação principal)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `ProblemDetail.correlationId` (api-contract §3)

**Testing / Evidence required:**
- Teste: `map(error).metadata.correlationId !== undefined`

**Risks / Notes:**
- Se o `ProblemDetail` do `trei-service` não incluir `correlationId` em todos os tipos de erro, o mapper deve tolerar `undefined` graciosamente.
