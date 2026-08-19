# DASH-FE-003 — Unit Tests Scaffold + Coverage Baseline

**Type:** Test
**Priority:** P2 (High)
**Module:** DASH
**Layer:** Frontend
**Created:** 2026-08-19
**Status:** OPEN

---

## Titulo

Create unit test files covering DashHomeFacade, NotificationsFacade, guards, and mock adapters.

## Descricao

The DASH lib has 0 `.spec.ts` files. `passWithNoTests: true` in `jest.config.js` silences the absence. The module has no verifiable evidence of correctness from automated tests. Mock adapters return deterministic data and are safe for isolated unit testing without real HTTP.

## Requisitos

1. `frontend/libs/dash/src/lib/dash.facade.spec.ts` — DashHomeFacade behavior
2. `frontend/libs/dash/src/lib/notifications.facade.spec.ts` — NotificationsFacade behavior
3. `frontend/libs/dash/src/lib/access/dash.guard.spec.ts` — Wave-0 bypass guards
4. `frontend/libs/dash/src/lib/api/dash-summary-mock.adapter.spec.ts` — DashSummaryMockAdapter contract
5. `frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.spec.ts` — DashNotificationsMockAdapter contract

### Cenarios obrigatorios — DashHomeFacade

- `load()` → status muda para 'loading' depois para 'loaded'; `patient()` nao-null
- `load()` com port retornando lista vazia → status = 'empty'
- `load()` com port lancando erro → status = 'error'; `error()` nao-null
- `retry()` chama `load()` com o mesmo estado
- `loading` computed = true durante 'loading', false fora
- `hasDegradedSections` = true quando `degradedSections.length > 0`

### Cenarios obrigatorios — NotificationsFacade

- `load()` → status 'loading' → 'loaded'; `notifications()` nao-vazio
- `load()` com lista vazia → status = 'empty'
- `load()` com erro → status = 'error'; `error()` nao-null
- `acknowledge(id)` → remove notificacao da lista apos confirmacao do port
- `acknowledge(id)` com lista tornando-se vazia → status = 'empty'
- `acknowledge(id)` com erro → nao-fatal; notificacao permanece na lista
- `retry()` chama `load()`
- `count` computed = numero de notifications em lista

### Cenarios obrigatorios — Guards (Wave-0)

- `authGuard`: retorna `true` quando `DASH_AUTH_SESSION` = null (Wave-0 bypass)
- `authGuard`: retorna `UrlTree` para `/entrada` quando `session.token === null`
- `dashGuard`: retorna `true` quando `DASH_ACCESS_DECISION` = null (Wave-0 bypass)
- `roleGuard`: retorna `true` quando `DASH_ACCESS_DECISION` = null (Wave-0 bypass)

### Cenarios obrigatorios — Mock Adapters

- `DashSummaryMockAdapter.getSummary()` retorna Observable com `DashSummaryResponse` (tenantId, caregiver, patients[0])
- `DashNotificationsMockAdapter.listUnacknowledged()` retorna Observable com `notifications` nao-vazio
- `DashNotificationsMockAdapter.acknowledge(id)` retorna Observable<void>; remove item internamente

## Aceitacao

- [ ] 5 arquivos `.spec.ts` criados
- [ ] >= 20 testes passando
- [ ] `npx nx test dash` exit 0

## Criterio de Conclusao

```bash
cd frontend && npx nx test dash
# Exit code: 0
# Tests: >= 20 PASS, 0 FAIL
```

## Arquivos a criar

```
frontend/libs/dash/src/lib/dash.facade.spec.ts
frontend/libs/dash/src/lib/notifications.facade.spec.ts
frontend/libs/dash/src/lib/access/dash.guard.spec.ts
frontend/libs/dash/src/lib/api/dash-summary-mock.adapter.spec.ts
frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.spec.ts
```

## Notas de Implementacao

- Usar `TestBed.configureTestingModule` com providers substituindo ports por `jest.fn()` retornando `of(mockData)` (RxJS)
- Para facades: mock `DASH_SUMMARY_PORT` e `DASH_NOTIFICATIONS_PORT` via `useValue`
- Para guards: mock `DASH_AUTH_SESSION` e `DASH_ACCESS_DECISION` via `useValue`; mock `Router` via `jasmine.createSpyObj` ou `jest.fn()`
- Nao usar `HttpClientTestingModule` — adapters HTTP sao stubs e nao sao testados neste ciclo
- `takeUntilDestroyed()` em facades usa `DestroyRef` — Angular 18 injeta automaticamente em TestBed

## Referencias

- Plano: `docs/architecture/frontend/plans/plan-FE-DASH.md` P2
- Facades: `frontend/libs/dash/src/lib/dash.facade.ts`, `notifications.facade.ts`
- Guards: `frontend/libs/dash/src/lib/access/dash.guard.ts`
- Mock adapters: `frontend/libs/dash/src/lib/api/dash-*-mock.adapter.ts`
- Referencia HIST: `frontend/libs/hist/src/lib/hist.facade.spec.ts` (padrao seguido)
