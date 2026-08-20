# HIST-008 -- HistoryQueryService + ChainVerificationService: caminho de leitura completo

## Contexto

Dois serviços de aplicação cobrem o caminho de leitura: `HistoryQueryService` (timeline
paginada + findById) e `ChainVerificationService` (recomputa hashes do stream e retorna
intact/broken/empty). Ambos operam dentro do tenant via `AuditLedgerPort` e são
somente-leitura — sem `@Transactional` de escrita.

## Spec de referencia

- SPEC-03 (History Query, Feedback-Verdict Query & Chain Verification)
- SPEC-06 (scope de cuidador via ACS — fail-closed)
- SPEC-10 (integração ACS: `hist:read` vs `hist:read:all`)
- `contracts/api-contract.md §3`

## Escopo

**`com.neuroassist.hist.application.service.HistoryQueryService` (`@Service`):**
- Implementa `QueryHistoryUseCase.findById` e `queryTimeline`.
- `queryTimeline`: valida que `subjectId` sem `subjectType` lança erro (`400`); `from > to` lança erro (`400`); `size` clamped a 200 (sem erro).
- Se caller tem `hist:read` mas não `hist:read:all`: chama `ActorDirectoryPort.resolvePatientScope` e injeta filtro de `subjectId ∈ scope`; se ACS lança `ActorDirectoryUnavailableException` → propaga como `503`.
- Ordena por `recordedAt` conforme `order=desc|asc`.

**`com.neuroassist.hist.application.service.ChainVerificationService` (`@Service`):**
- Implementa `VerifyChainUseCase.verifyStream`.
- Itera registros do stream ordenados por `sequence_no` ASC.
- Verifica: `record.prevHash == previousRecordHash` e `record.recordHash == sha256(record.prevHash ‖ canonical(record))`.
- Retorna `intact` se todos OK; `broken` com `brokenAtSequenceNo` no primeiro falho; `empty` se stream sem records.
- **Nunca muta** nenhum registro durante verificação.

## Resultado esperado

Testes de integração com Testcontainers: timeline filtrada, scope de cuidador, chain intact
de N records, chain broken em sequência N, stream empty.

## Criterios de aceite

- [ ] `queryTimeline` sem filtros retorna todos os records do tenant ordenados `recordedAt DESC`.
- [ ] `subjectId` sem `subjectType` resulta em `400 /errors/incomplete-subject-filter`.
- [ ] `from > to` resulta em `400 /errors/invalid-time-window`.
- [ ] `size=300` é clampado para 200 sem erro.
- [ ] Consulta com `hist:read` filtra por escopo ACS; `hist:read:all` não chama ACS.
- [ ] ACS indisponível em leitura de cuidador resulta em `503 /errors/scope-unavailable`.
- [ ] `verifyStream` em stream de 5 records com hashes corretos retorna `status=intact, recordsVerified=5`.
- [ ] `verifyStream` em stream com record 3 adulterado retorna `status=broken, brokenAtSequenceNo=3`.
- [ ] `verifyStream` em stream vazio retorna `status=empty, recordsVerified=0`.

## Dependencias

- HIST-003 (QueryHistoryUseCase, VerifyChainUseCase)
- HIST-004 (AuditLedgerPort, ActorDirectoryPort)
- HIST-007 (HashChainPort usado na verificação)
- HIST-006 (dados de teste dependem de RecordAuditFactService)

## Estimativa

M

## Prioridade

P2
