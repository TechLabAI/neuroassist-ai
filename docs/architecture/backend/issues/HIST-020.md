# HIST-020 -- BDD step definitions: audit-fact-ingestion + idempotency-and-concurrency features

## Contexto

Implementação dos step definitions Cucumber para as features de ingestão e idempotência.
Os arquivos `.feature` já estão em `tests/cucumber/`. Os steps exercitam o serviço
via MockMvc ou RestAssured com Testcontainers PostgreSQL em background.

## Spec de referencia

- SPEC-08 (BDD Test Scenarios)
- `tests/cucumber/audit-fact-ingestion.feature`
- `tests/cucumber/idempotency-and-concurrency.feature`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/bdd/`:**
- `AuditFactIngestionSteps` (implementa steps de `audit-fact-ingestion.feature`):
  - Given: tenant válido, JWT com scope `hist:write`.
  - When: POST /api/v1/hist/audit-facts com body construído.
  - Then: assert status code, `Location` header, `factId` na resposta, `recordHash` non-null.
  - Cenários: ingestion success, unknown factType (422), forbidden payload (422), future occurredAt (422), correction with correctsFactId.
- `IdempotencySteps` (implementa steps de `idempotency-and-concurrency.feature`):
  - Given/When/Then: mesmo factId submetido duas vezes → primeiro 201, segundo 200 com mesmo `id`.
  - Cenário de corrida: dois threads com mesmo factId → exatamente 1 record criado.
  - Cenário de corrida de stream: dois factIds diferentes no mesmo stream simultâneos → sequenceNo consecutivos.
- `CucumberSpringConfiguration` (`@CucumberContextConfiguration @SpringBootTest`).
- `application-test.yml`: Testcontainers PostgreSQL, Flyway enabled, WireMock para ACS/JWK.

## Resultado esperado

`./gradlew :hist-service:test` roda os cenários Cucumber e todos passam. Relatório HTML
gerado em `build/reports/cucumber/`.

## Criterios de aceite

- [ ] Todos os cenários de `audit-fact-ingestion.feature` passam com 0 falhas.
- [ ] Todos os cenários de `idempotency-and-concurrency.feature` passam com 0 falhas.
- [ ] Cenário de corrida (concorrência) usa ≥ 2 threads e verifica atomicidade.
- [ ] Nenhum cenário viola a postura write-once (nenhum UPDATE/DELETE é feito).
- [ ] Relatório Cucumber gerado sem cenários pendentes (sem `@Pending` nos steps).

## Dependencias

- HIST-006 (RecordAuditFactService implementado)
- HIST-009, HIST-010, HIST-011 (persistência funcional)
- HIST-014, HIST-015 (security + tenant filter para JWT nos testes)

## Estimativa

M

## Prioridade

P8
