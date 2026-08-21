# HIST-006 -- RecordAuditFactService: golden path append (idempotência + chaining)

## Contexto

Coração do caminho de escrita: implementa `RecordAuditFactUseCase.append`. O fluxo é
determinístico: (1) validar, (2) idempotência, (3) resolver correção, (4) lock do tip,
(5) chain, (6) commit atômico. É o serviço de aplicação mais crítico do HIST (ADR-004,
ADR-005, ADR-006).

## Spec de referencia

- SPEC-02 (fluxo append em 8 passos)
- SPEC-05 (idempotência e concorrência)
- ADR-004, ADR-005, ADR-006
- `contracts/api-contract.md §2.1 EARS`

## Escopo

**`com.neuroassist.hist.application.service.RecordAuditFactService` (`@Service`):**
1. Validar `factType` (via `FactType.of()`) e `payload` (sem chaves biométricas proibidas).
2. Validar `occurredAt` (não futuro além de 5 min de skew).
3. Consultar `IdempotencyPort.findByFactId(factId)` — se presente, buscar e retornar `200`.
4. Se `correctsFactId != null`: resolver via `AuditLedgerPort.findByFactId()` no tenant — senão `CorrectsTargetNotFoundException`.
5. `StreamHeadPort.lockAndFetchTip(streamKey)` — lock pessimista; retorna `StreamTip?`.
6. Calcular `sequenceNo` (tip.lastSequenceNo+1 ou 0) e `prevHash` (tip.lastRecordHash ou genesis).
7. `HashChainPort.computeHash(prevHash, record)` → `recordHash`.
8. `AuditLedgerPort.insert(record)` + `IdempotencyPort.register(factId, id)` + `StreamHeadPort.upsertTip(...)` — em uma transação (`@Transactional`).
9. Retornar `AuditRecordResult` com flag `created=true`.
- Converter `UniqueConstraintViolation(fact_id)` em `200` idempotente (race-condition handler).
- Converter `UniqueConstraintViolation(stream_key, sequence_no)` em retry (SPEC-05).

## Resultado esperado

Teste de integração com Testcontainers PostgreSQL cobre: novo append (201), replay (200),
correção, corrida de sequence_no (retry bem-sucedido).

## Criterios de aceite

- [ ] Append com `factId` inédito persiste exatamente 1 `AuditRecord` e 1 `IdempotencyEntry`.
- [ ] Replay do mesmo `factId` não cria segundo record; retorna o original.
- [ ] `recordHash = sha256(prevHash ‖ canonical(record v1))` verificável.
- [ ] Primeiro record do stream usa `prevHash = genesis (64 zeros)`.
- [ ] Segundo record usa `prevHash = record_hash do anterior`.
- [ ] Corrida de dois appends simultâneos no mesmo stream produz `sequenceNo` consecutivos sem buraco.
- [ ] `@Transactional` cobre insert + idempotency + stream_head (rollback total se falhar).
- [ ] `occurredAt` no futuro por >5 min lança `FutureOccurredAtException`.

## Dependencias

- HIST-002 (AuditRecord, exceções)
- HIST-003 (RecordAuditFactUseCase)
- HIST-004 (ports)

## Estimativa

L

## Prioridade

P2
