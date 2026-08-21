# HIST-007 -- HashChainPort implementation: SHA-256 + canonicalization v1

## Contexto

`HashChainPort` é o adaptador puro que computa `sha256(prevHash ‖ canonical(record))`.
A canonicalization v1 é determinística: JSON com campos em ordem fixa (definida em
`data-model.md §3`), sem `recordedAt`/`id`/`record_hash`/`prev_hash`. `payload` usa
RFC 8785 JCS (JSON Canonicalization Scheme). Sem Spring, sem JPA — 100% testável em unitário.

## Spec de referencia

- SPEC-02 (seção "chaining", fórmula `sha256(prevHash ‖ canonical(record))`)
- ADR-006 (Hash-Chained Tamper-Evidence)
- `contracts/data-model.md §3` (campos incluídos/excluídos, ordem, RFC 8785)

## Escopo

**`com.neuroassist.hist.infrastructure.hash.Sha256HashChainAdapter` (`@Component`):**
- Implementa `HashChainPort`.
- `canonical(record: AuditRecord): String` — JSON determinístico com campos em ordem:
  `fact_id, fact_type, subject_type, subject_id, actor_caregiver_id, actor_label_snapshot,
  payload (JCS), occurred_at (ISO-8601 UTC), stream_key, sequence_no, corrects_record_id`.
- `computeHash(prevHash, record)` = `hex(sha256(prevHash.toByteArray(UTF-8) +
  canonical(record).toByteArray(UTF-8)))` (lowercase hex 64 chars).
- `FactTypeCatalogService` (implementação de `FactTypeCatalogUseCase`): carrega vocabulário
  v1 de `application.yml` (`hist.fact-types`) para ser configurável sem recompilação.

## Resultado esperado

Teste unitário fixo com vetor de referência: dado prevHash=genesis e um record-sample,
o hash produzido é idêntico a um valor calculado de forma independente.

## Criterios de aceite

- [ ] `canonical(record)` produz a mesma string em duas chamadas com o mesmo record (determinístico).
- [ ] `canonical` exclui os campos `recorded_at`, `id`, `record_hash`, `prev_hash`.
- [ ] `computeHash` retorna lowercase hex de 64 caracteres.
- [ ] Teste com vetor de referência fixo: `computeHash(genesis, recordSample) == expectedHex`.
- [ ] `payload` usa JCS (chaves ordenadas lexicograficamente, sem espaços extras).
- [ ] `FactTypeCatalogService.listActive()` retorna os 10 tipos do vocabulário v1 seed.

## Dependencias

- HIST-004 (HashChainPort interface)
- HIST-002 (AuditRecord)

## Estimativa

M

## Prioridade

P2
