## [PAC-INT-003] @neuroassist/shared/error-model — ProblemDetail → AppError mapper para PAC

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/error-surface/pac-error-codes.ts` define os 12 códigos de erro PAC como constantes e registra o mapper PAC-específico no `@neuroassist/shared/error-model`, permitindo que o `HttpInterceptor` global converta `ProblemDetail` em `AppError` com `kind` e `surface` corretos antes que o facade receba o erro.

**Context / Problem statement:**
- `@neuroassist/shared/error-model` fornece o tipo `AppError` e um mecanismo de registro de mappers por módulo.
- PAC tem 12 tipos de `ProblemDetail` (SPEC-07 §1); cada um precisa de um `AppError.kind` que o `pac-error.handler.ts` (PAC-FE-021) usa para routing de superfície.
- O mapper deve ser registrado no `pac.bootstrap.ts` (PAC-FE-005) para que o interceptor global já transforme os erros antes que cheguem ao facade.
- Sem esse registro, o facade receberia `ProblemDetail` bruto em vez de `AppError`, quebrando o error handler.

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/error-surface/pac-error-codes.ts` com 12 constantes de string: `PAC_ERROR_EMERGENCY_NOT_DETECTABLE = '/errors/emergency-not-detectable'` etc.
2. Criar `libs/pac/data-access/src/lib/error-surface/pac-problem-detail.mapper.ts`: função `mapPacProblemDetail(pd: ProblemDetail): AppError` que implementa a tabela de SPEC-07 §1.
3. Registrar via `ErrorModelRegistry.registerMapper('pac', mapPacProblemDetail)` no `pac.bootstrap.ts` init.
4. Unit test: chamar o mapper com cada um dos 12 tipos → assert `AppError.code` e `AppError.kind` corretos.

**Non-goals:**
- Sem implementação do `ErrorModelRegistry` (pertence a `@neuroassist/shared/error-model`).
- Sem tratamento de erros de outras camadas (GEST, ACS) — apenas erros PAC.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 tabela)

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-error-codes.ts`
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-problem-detail.mapper.ts`
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-problem-detail.mapper.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS (12 error types cobertos)
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: mapper(`{ type: '/errors/emergency-not-detectable' }`) → `AppError.kind === 'blocking-modal'`.
- `npx nx test pac-data-access` PASS: mapper(`{ type: '/errors/missing-tenant' }`) → `AppError.kind === 'banner'`.
- `npx nx test pac-data-access` PASS: mapper(`{ type: '/errors/conflict-precondition' }`) → `AppError.retryable === true`.
- Todos os 12 tipos retornam `AppError.kind` correto (nenhum `undefined`).

**Dependencies:**
- `@neuroassist/shared/error-model` (tipos AppError, ProblemDetail, ErrorModelRegistry)
- PAC-FE-013 (error-mapping.ts — pode reutilizar o mapper daqui)
- PAC-FE-005 (pac.bootstrap.ts — ponto de registro)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Consiste em conversão de tipos — sem chamadas de API.

**Testing / Evidence required:**
- Unit: tabela de 12 mapeamentos assertada.

**Risks / Notes:**
- Se `ErrorModelRegistry` não existir ainda em `@neuroassist/shared/error-model`, criar um provider token para registro manual e integrar depois quando o serviço centralizado existir.
