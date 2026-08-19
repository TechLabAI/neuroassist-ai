# HIST-FE-004 -- HTTP adapter: ativar HistClientHttpAdapter apos deploy de hist-service

**Status:** BLOQUEADO -- aguardando hist-service deployment (INT-FE-HIST-001)
**Tipo:** FEATURE
**Maturidade:** 1 -- Stub implementado; ativacao bloqueada
**Done/Total:** 0/5 itens

## Contexto

O `HistClientHttpAdapter` existe em `frontend/libs/hist/src/lib/api/hist-client-http.adapter.ts` como stub que lanca `NotImplementedError`. O `provideHist()` usa `adapter: 'mock'` por padrao. A ativacao real requer que `hist-service` esteja deployado com rotas de Gateway expostas e o interceptor de `X-Empresa-Id` wired via `@neuroassist/shared/access-http-base`. Esta issue e bloqueada por `INT-FE-HIST-001`.

## Spec de referencia

- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P5
- `frontend/libs/hist/src/lib/api/hist-client-http.adapter.ts` — stub a completar
- `frontend/libs/hist/src/lib/api/hist-client.port.ts` — interface a implementar
- Commit 3e4328d — `acsHttpInterceptorFn` (padrao de referencia para multi-empresa)
- `docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` — dependencias declaradas

## Escopo

- [ ] Implementar `HistClientHttpAdapter.list(params)` via `HttpClient.get('/api/v1/hist/history', { params })`
- [ ] Implementar `HistClientHttpAdapter.get(id)` via `HttpClient.get('/api/v1/hist/history/:id')`
- [ ] Implementar `HistClientHttpAdapter.verify()` via `HttpClient.get('/api/v1/hist/history/verify')`
- [ ] Criar `histHttpInterceptorFn` que injeta `X-Empresa-Id` via `@neuroassist/shared/access-http-base` (mesmo padrao de `acsHttpInterceptorFn`)
- [ ] Substituir `provideHist({ adapter: 'mock' })` por `provideHist({ adapter: 'http' })` em `app.config.ts` de producao

## Resultado esperado

`GET /api/v1/hist/history` retorna lista de `AuditRecordResponse` real; timeline exibe dados ao vivo. Multi-empresa: `X-Empresa-Id` presente em todas as requisicoes HTTP.

## Criterios de aceite

- [ ] `cd frontend && npx nx lint hist && npx nx test hist && npx nx build neuroassist-ai-web` — todos exit 0
- [ ] Smoke test manual: `GET /hist/audit-records` retorna 200 com dados do tenant correto
- [ ] Header `X-Empresa-Id` presente nas requisicoes (verificavel via DevTools Network)
- [ ] Nenhum `TODO[INT-FE-HIST-001]` remanescente apos ativacao

## Arquivos afetados

- `frontend/libs/hist/src/lib/api/hist-client-http.adapter.ts`
- `frontend/libs/hist/src/lib/hist.providers.ts`
- `frontend/apps/neuroassist-ai-web/src/app/app.config.ts`

## Dependencias

- INT-FE-HIST-001 (hist-service deployment + Gateway route /hist/**)
- hist-service contratos de API alinhados com `hist.types.ts` (AuditRecordResponse)

## Estimativa

S

## Prioridade

P5 (DEFERRED)
