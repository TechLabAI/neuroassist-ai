# INT-FE-HIST-001 -- Integracao FE-BE: ativar hist-service HTTP para timeline de auditoria

**Status:** BLOQUEADO -- hist-service nao deployado; Gateway route /hist/** nao configurada
**Tipo:** INTEGRATION
**Maturidade:** 0 -- Stubs implementados; integracao real nao iniciada
**Done/Total:** 0/5 itens

## Contexto

O modulo frontend HIST possui todos os adapters de infra preparados: `HistClientHttpAdapter` (stub) e `HistFeedbackHttpAdapter` (stub). O `provideHist()` opera em modo `adapter: 'mock'` ate que `hist-service` esteja disponivel no ambiente. Esta issue rastreia o desbloqueio e ativacao da integracao HTTP real entre o FE HIST e o backend `hist-service`. O padrao de multi-empresa (`X-Empresa-Id` via `@neuroassist/shared/access-http-base`) ja esta documentado e sera implementado na ativacao.

## Spec de referencia

- `docs/architecture/frontend/plans/plan-FE-HIST.md` — P5
- `frontend/libs/hist/src/lib/api/hist-client-http.adapter.ts` — stub a completar
- `frontend/libs/hist/src/lib/hist.types.ts` — `AuditRecordResponse`, `HistoryQueryParams` (contratos de wire DTO)
- `docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` — `http_backend: hist-service`
- Commit 3e4328d — `acsHttpInterceptorFn` (padrao de referencia para interceptor multi-empresa)

## Escopo

- [ ] Confirmar que `hist-service` esta deployado com Eureka registration e Gateway route `/hist/**` configurada
- [ ] Confirmar que contrato de `AuditRecordResponse` (api-contract.md do hist-service) e compativel com `hist.types.ts`
- [ ] Implementar `HistClientHttpAdapter` completo (list, get, verify) via `HttpClient`
- [ ] Criar `histHttpInterceptorFn` que injeta `X-Empresa-Id` via `@neuroassist/shared/access-http-base`
- [ ] Substituir `provideHist({ adapter: 'mock' })` por `provideHist({ adapter: 'http' })` em producao

## Resultado esperado

Timeline de auditoria exibe dados reais do `hist-service` ao vivo. `X-Empresa-Id` presente em todas as requisicoes. Nenhum `TODO[INT-FE-HIST-001]` remanescente apos ativacao.

## Criterios de aceite

- [ ] `hist-service` acessivel via Gateway: `curl -H 'Authorization: Bearer <token>' http://gwy/hist/audit-records` retorna 200
- [ ] FE HIST exibe dados reais (nao mock) em staging/producao
- [ ] Header `X-Empresa-Id` presente em todas as requisicoes HTTP do modulo HIST (verificavel via proxy/DevTools)
- [ ] `cd frontend && npx nx lint hist && npx nx test hist && npx nx build neuroassist-ai-web` — todos exit 0

## Condicoes de desbloqueio

- `hist-service` deployado com Spring Boot 3.5 + PostgreSQL
- Gateway route `/hist/**` configurada apontando para `hist-service`
- Eureka registration de `hist-service` ativa
- Contrato de API alinhado com FE team (AuditRecordResponse schema)

## Dependencias

- BE: hist-service deployment (microservico nao scaffoldado / nao deployado)
- BE: Gateway route configuration
- BE: Eureka registration

## Bloqueia

- HIST-FE-004 (HistClientHttpAdapter ativacao)

## Estimativa

M (depende de infra BE)

## Prioridade

P5 (DEFERRED)
