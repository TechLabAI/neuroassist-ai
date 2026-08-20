# INT-HIST-002 -- ACS ActorDirectory integration test: scope resolve + timeout + circuit breaker

## Contexto

Testa a integração outbound com ACS `ActorDirectory` em cenários reais: scope resolvido com
sucesso, timeout (2s), CB OPEN, e fall-closed para `hist:read`. Usa WireMock em modo
standalone com regras de cenário (response delay para timeout).

## Spec de referencia

- SPEC-06 (fail-closed em `hist:read` quando ACS indisponível)
- SPEC-10 (timeout 2s, Resilience4j CB)
- `contracts/integration-contracts.md §2`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/acs/AcsActorDirectoryIntegrationTest`:**
- Cenário 1 — ACS OK: WireMock retorna `[patientId1, patientId2]`; `GET /history` com `hist:read` filtra para esses 2 pacientes.
- Cenário 2 — ACS timeout: WireMock delay > 2000ms; `GET /history` com `hist:read` → `503 /errors/scope-unavailable`.
- Cenário 3 — ACS 500: WireMock status 500; adapter lança `ActorDirectoryUnavailableException` → 503.
- Cenário 4 — CB OPEN: após 5 falhas consecutivas (CB config reduzida p/ teste), próxima chamada → 503 imediato sem chamar WireMock.
- Cenário 5 — hist:read:all: `GET /history` com `hist:read:all` + ACS offline → 200 (não chama ACS).
- `HistMetrics.hist_acs_scope_resolution_total{outcome}` verificado após cada cenário.

## Resultado esperado

Todos os 5 cenários passam. Métricas de ACS são incrementadas conforme esperado.

## Criterios de aceite

- [ ] Cenário ACS OK: timeline filtrada para pacientes do scope (sem pacientes fora do scope).
- [ ] Cenário ACS timeout: resposta `503 /errors/scope-unavailable` em < 3s.
- [ ] Cenário ACS 500: resposta `503`.
- [ ] Cenário CB OPEN: resposta `503` imediata (< 50ms — sem HTTP call).
- [ ] Cenário `hist:read:all`: resposta `200` mesmo com ACS offline.
- [ ] `hist_acs_scope_resolution_total{outcome=success}` incrementa no cenário 1.
- [ ] `hist_acs_scope_resolution_total{outcome=timeout}` incrementa no cenário 2.

## Dependencias

- HIST-012 (AcsActorDirectoryClient + ActorDirectoryAdapter)
- HIST-008 (HistoryQueryService — usa ActorDirectoryPort)
- HIST-013 (GET /history controller)
- HIST-017 (métricas ACS)

## Estimativa

M

## Prioridade

P4
