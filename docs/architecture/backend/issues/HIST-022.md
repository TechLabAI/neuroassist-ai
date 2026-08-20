# HIST-022 -- Concurrency hardening: retry em stream-tip lock contention + idempotency race

## Contexto

Em carga real, dois appends simultâneos no mesmo stream podem contender pelo `stream_head`
lock ou produzir `UNIQUE(stream_key, sequence_no)` collision após o lock ser liberado.
O retry precisa ser limitado (max 3 tentativas) e registrado em log/metric. A conversão de
`UniqueConstraintViolation(fact_id)` em 200 idempotente também precisa de hardening contra
race (SPEC-05).

## Spec de referencia

- SPEC-05 (Idempotency & Concurrency — casos de borda de corrida)
- SPEC-02 (fluxo append — passo 7 menciona retry)

## Escopo

**`RecordAuditFactService` (complemento de HIST-006):**
- Envolver o append em loop de retry: `maxAttempts=3`, intervalo `50ms` (exponential backoff opcional).
- Capturar `DataIntegrityViolationException` com constraint `uq_audit_record_stream_seq`:
  - Re-ler tip via `StreamHeadPort.lockAndFetchTip` e tentar novamente.
  - Após `maxAttempts` exceeded: lançar `StreamLockContentionException` → 503 `/errors/stream-lock-contention`.
- Capturar `DataIntegrityViolationException` com constraint `uq_audit_record_fact_id`:
  - Converter em 200 idempotente: buscar record via `AuditLedgerPort.findByFactId(factId)`.
- Metric: `hist_append_retry_total{reason=stream_seq|fact_id_race}` (Counter).
- Log: `WARN` em retry, `ERROR` em exaustão.

## Resultado esperado

Teste de concorrência com `CountDownLatch` e 10 threads no mesmo stream: zero gaps em
`sequenceNo`, zero duplicatas, zero falhas não-esperadas. Metric `hist_append_retry_total`
incrementa conforme o esperado.

## Criterios de aceite

- [ ] 10 threads simultâneas appendando para o mesmo streamKey produzem `sequenceNo` 0..9 sem gap.
- [ ] Nenhum `sequenceNo` duplicado após 10 appends concorrentes.
- [ ] Retry limitado a 3 tentativas; exaustão resulta em 503.
- [ ] `DataIntegrityViolationException(fact_id)` convertida em 200 idempotente (não 500).
- [ ] `hist_append_retry_total` incrementa em cada retry.
- [ ] Log `WARN` emitido em cada retry com `attempt` e `streamKey`.

## Dependencias

- HIST-006 (RecordAuditFactService — este issue adiciona retry wrapper)
- HIST-017 (métrica hist_append_retry_total a adicionar)

## Estimativa

M

## Prioridade

P9
