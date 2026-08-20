## [INT-FE-HIST-006] Integração: access-http-base headers + correlationId em toda superfície de erro

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Interceptor `@neuroassist/shared/access-http-base` registrado para o módulo HIST; todos os calls HTTP carregam `Authorization`, `X-Empresa-Id`, `X-Correlation-Id`; `correlationId` do response echoed em todos os `AppError`.

**Context / Problem statement:**
- SPEC-04 §2: `access-http-base` attaches Authorization, X-Empresa-Id, X-Correlation-Id.
- FE-GAP-12 (Specs-Validate): `correlationId` deve aparecer em toda superfície de erro.
- X-Empresa-Id: JWT `tenant` claim é autoritativo server-side; FE nunca seta de user input.
- X-Correlation-Id: gerado no GWY se ausente; ecoado na response `correlationId` (api-contract §1.1).

**Approach (execution path):**
1. Verificar que `access-http-base` interceptor está no `provideHttpClient(withInterceptors([...]))` do bootstrap do módulo HIST.
2. Confirmar que todos os requests HTTP do módulo HIST passam pelo interceptor (unit test de integração).
3. `mapHistError` (FE-HIST-017) extrai `correlationId` do body de problem+json E do header `X-Correlation-Id` (fallback).
4. `InlineErrorComponent` exibe `correlationId` como texto de suporte ("ID de suporte: {correlationId}").
5. E2e: verificar presença do header X-Empresa-Id via `page.on('request', ...)`.

**Non-goals:**
- Não alterar o interceptor shared.

**Layer(s):**
frontend / integration + data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2)
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§5, §6)

**Implementation checklist (by file):**
- 🔲 Verificar provider de `access-http-base` em `hist.bootstrap.ts`
- 🔲 Unit test: mock request verifica presença de X-Empresa-Id e Authorization
- 🔲 `mapHistError` extrai `correlationId` do problem+json
- 🔲 `InlineErrorComponent` exibe `correlationId`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx test hist-feature-timeline` PASS

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- X-Empresa-Id e Authorization presentes em todos os requests HTTP (unit test).
- `correlationId` presente em todo `AppError` (unit test do mapper, FE-HIST-017).
- InlineErrorComponent exibe o `correlationId` (screenshot e2e).

**Dependencies:**
- FE-HIST-001, FE-HIST-005, FE-HIST-017
