# HIST-005 -- REST controllers: ingestion surface (POST /audit-facts, GET /audit-facts/{id}, GET /fact-types)

## Contexto

Adaptadores REST inbound para a superfície de ingestão. Traduzem HTTP → use-case command
e use-case result → HTTP response, incluindo mapeamento de exceções de domínio para
`application/problem+json` (RFC 7807). Neste issue cobrem-se os três endpoints da
superfície de ingestão + catálogo de vocabulário. A superfície de histórico está em HIST-013.

## Spec de referencia

- SPEC-01 (endpoints #1, #2, #5)
- SPEC-02 (respostas 201/200/422 de POST /audit-facts)
- `contracts/api-contract.md §2`

## Escopo

**Pacote `com.neuroassist.hist.api`:**
- `AuditFactController` (`@RestController`):
  - `POST /api/v1/hist/audit-facts` → chama `RecordAuditFactUseCase.append` → `201 Created` (novo) ou `200 OK` (idempotente). Header `Location: /api/v1/hist/audit-facts/{id}` em 201.
  - `GET /api/v1/hist/audit-facts/{id}` → `QueryHistoryUseCase.findById` → `200` ou `404`.
- `FactTypeCatalogController` (`@RestController`):
  - `GET /api/v1/hist/fact-types` → `FactTypeCatalogUseCase.listActive` → `200 [...]`.
- `HistProblemDetailHandler` (`@ControllerAdvice`):
  - `UnknownFactTypeException` → `422 /errors/unknown-fact-type`.
  - `ForbiddenPayloadKeyException` → `422 /errors/forbidden-payload`.
  - `FutureOccurredAtException` → `422 /errors/future-occurred-at`.
  - `CorrectsTargetNotFoundException` → `422 /errors/corrects-target-not-found`.
  - `MethodArgumentNotValidException` → `400 /errors/validation`.
  - Qualquer outro → `500` sem stack trace.
- Propagação de `X-Correlation-Id`: gerado se ausente; ecoado na resposta.

## Resultado esperado

Controllers respondem conforme a tabela de status de `api-contract.md §2`. Erros retornam
`application/problem+json` com `type`, `status`, `correlationId` e sem stack trace.

## Criterios de aceite

- [ ] `POST /api/v1/hist/audit-facts` com body válido retorna `201` e `Location`.
- [ ] `POST` com `factId` já visto retorna `200` sem novo registro.
- [ ] `GET /api/v1/hist/audit-facts/{id}` com id inexistente retorna `404 application/problem+json`.
- [ ] `POST` com `factType` desconhecido retorna `422 /errors/unknown-fact-type`.
- [ ] `POST` com payload contendo `"image"` retorna `422 /errors/forbidden-payload`.
- [ ] Resposta de erro nunca contém stack trace, SQL, ou id interno.
- [ ] `X-Correlation-Id` é gerado e ecoado quando ausente na requisição.
- [ ] `GET /api/v1/hist/fact-types` retorna lista com pelo menos 10 tipos do vocabulário v1.

## Dependencias

- HIST-003 (use-case interfaces)
- HIST-002 (exceções de domínio mapeadas no handler)

## Estimativa

M

## Prioridade

P1
