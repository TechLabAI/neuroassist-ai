# SPEC-02 — Audit Fact Ingestion & Hash Chaining

> Service: `hist-service` · Module: HIST · Refs: ADR-004, ADR-005, ADR-006
> Contract: `contracts/api-contract.md §2`, `contracts/data-model.md` · EARS notation (D-057).

## Objetivo

Especificar o caminho de escrita write-once: como um audit fact submetido por um produtor
(EVNT/PAC/ACS/EDGE) vira um `AuditRecord` idempotente, posicionado e encadeado por hash no seu stream
`(tenant, subject)`.

## Escopo

**In:** validação do fato, idempotência por `factId`, atribuição de `sequenceNo`, cálculo de
`recordHash`, avanço do `stream_head`, correções (`correctsFactId`), catálogo de `factType`. **Out:**
consulta e verificação (SPEC-03); concorrência detalhada (SPEC-05, referenciada aqui).

## Decisões técnicas

- **Idempotência primeiro** (ADR-005): antes de qualquer chaining, consultar `audit_idempotency` por
  `factId`; se presente, retornar o registro original (`200`), sem segundo write nem avanço de sequência.
- **Chaining** (ADR-006): `stream_key = subject_type ':' subject_id`; `sequenceNo` = tip+1;
  `prevHash` = tip `recordHash` (ou genesis `0*64` no primeiro); `recordHash = sha256(prevHash ‖
  canonical(record))` com `canonical` v1 determinístico (`data-model.md §3`).
- **Write-once** (ADR-004): o agregado só expõe `append`; nenhuma operação de mutação; papel do banco sem
  UPDATE/DELETE no ledger.
- **factType controlado**: rejeitar tipo fora do vocabulário ativo (`422`).
- **LGPD**: rejeitar `payload` com chaves biométricas proibidas (`landmarks/image/frame/video/embedding`).

## Implementação (fluxo `append`)

```
1. Validar envelope (Bean Validation) + factType ∈ vocabulário + payload sem chaves proibidas.
2. Idempotência: SELECT audit_idempotency WHERE fact_id=? → se hit, retornar audit_record(record_id), 200.
3. (se correctsFactId != null) resolver record correspondente no tenant → correctsRecordId (senão 422).
4. Lock do tip do stream: SELECT stream_head WHERE stream_key=? FOR UPDATE (ou compute-from-record).
5. sequenceNo = head.last_sequence_no + 1 (ou 0); prevHash = head.last_record_hash (ou genesis).
6. recordHash = sha256(prevHash ‖ canonical(record v1)).
7. INSERT audit_record; INSERT audit_idempotency(fact_id, id); UPSERT stream_head. (mesma TX)
8. Emitir FatoAuditado (log+metric). Retornar 201 + Location.
```

## Casos de borda

- Corrida de dois `append` no mesmo stream → um vence o `UNIQUE (stream_key, sequence_no)`; o perdedor
  relê o tip e retenta (SPEC-05).
- Corrida de dois `append` com o mesmo `factId` → um vence `UNIQUE (fact_id)`; o perdedor converte a
  violação em resposta idempotente `200`.
- `occurredAt` no futuro além de 5 min de skew → `422` (`/errors/future-occurred-at`).
- `correctsFactId` aponta para registro de outro tenant → `422` (não encontrado no schema do tenant).

## Critérios de aceite (EARS)

- WHEN a valid fact with an unseen `factId` arrives, THE SYSTEM SHALL append a new `AuditRecord` with the
  next `sequenceNo` for its `streamKey`, compute `recordHash`, and respond `201`.
- WHEN a fact with an already-seen `factId` arrives, THE SYSTEM SHALL respond `200` with the original
  record and SHALL NOT create a second record nor advance the stream `sequenceNo`.
- THE SYSTEM SHALL set the first record of any stream with `prevHash` equal to the genesis constant and
  SHALL set every subsequent `prevHash` to the prior record's `recordHash`.
- THE SYSTEM SHALL compute `recordHash` as `sha256(prevHash ‖ canonical(record))` using canonicalization
  version `v1`.
- IF `factType` is not in the active vocabulary, THEN THE SYSTEM SHALL reject with `422`
  (`/errors/unknown-fact-type`).
- IF `payload` carries any forbidden biometric key, THEN THE SYSTEM SHALL reject with `422`
  (`/errors/forbidden-payload`).
- WHERE `correctsFactId` is present and resolvable in the tenant, THE SYSTEM SHALL append a corrective
  record with `correctsRecordId` set, leaving the original record unchanged (ADR-004).
- THE SYSTEM SHALL guarantee `recordedAt` is not earlier than the prior record's `recordedAt` in the same
  stream.
