# HIST-013 -- REST controllers: history surface (GET /history, GET /history/verify)

## Contexto

Adaptadores REST inbound para a superfície de histórico — consumida por DASH e pela
biblioteca FE `@neuroassist/hist`. O `HistoryController` traduz os parâmetros de query
para `TimelineFilter` e repassa para `QueryHistoryUseCase.queryTimeline`. O
`ChainVerificationController` mapeia `subjectType`/`subjectId` para `VerifyChainUseCase`.
A scope-restriction (hist:read vs hist:read:all) é resolvida em HIST-008; aqui o
controller apenas extrai o scope do JWT e passa via SecurityContext.

## Spec de referencia

- SPEC-01 (endpoints #3, #4)
- SPEC-03 (history query + chain verification)
- `contracts/api-contract.md §3`

## Escopo

**`com.neuroassist.hist.api.HistoryController` (`@RestController`):**
- `GET /api/v1/hist/history` com query params conforme `api-contract.md §3.1`: subjectType, subjectId, factType (repeatable), actorCaregiverId, from, to, order, page, size. Response: `{ items, page, size, total }`.
- `GET /api/v1/hist/history/verify` com subjectType, subjectId, from, to. Response: `ChainVerificationResult`.
- Extrair escopo do `Authentication` principal (claims `hist:read` vs `hist:read:all`) e passar para o service.
- Adicionar ao `HistProblemDetailHandler` (HIST-005): `MissingSubjectTypeException` → `400 /errors/incomplete-subject-filter`; `InvalidTimeWindowException` → `400 /errors/invalid-time-window`; `ActorDirectoryUnavailableException` → `503 /errors/scope-unavailable`.

## Resultado esperado

MockMvc testa: timeline paginada retorna 200 com envelope `{items, page, size, total}`;
verify de stream intacto retorna `{status:"intact"}`; subjectId sem subjectType retorna 400;
ACS indisponível retorna 503.

## Criterios de aceite

- [ ] `GET /history` sem filtros retorna 200 com envelope de paginação.
- [ ] `GET /history?subjectId=<uuid>` (sem subjectType) retorna `400 /errors/incomplete-subject-filter`.
- [ ] `GET /history?from=T1&to=T0` (from>to) retorna `400 /errors/invalid-time-window`.
- [ ] `GET /history/verify?subjectType=paciente&subjectId=<uuid>` retorna `200 {status:"intact"|"broken"|"empty"}`.
- [ ] Quando `ActorDirectoryUnavailableException` é lançada, controller retorna `503 /errors/scope-unavailable`.
- [ ] `size=300` é aceito e clampado a 200 na camada de serviço (não 400).

## Dependencias

- HIST-003 (QueryHistoryUseCase, VerifyChainUseCase)
- HIST-005 (HistProblemDetailHandler — adicionar novos mappings)
- HIST-008 (services implementados)

## Estimativa

M

## Prioridade

P4
