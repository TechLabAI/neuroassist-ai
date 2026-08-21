# SPEC-03 — History Query, Feedback-Verdict Query & Chain Verification

> Service: `hist-service` · Module: HIST · Refs: ADR-004, ADR-006
> Contract: `contracts/api-contract.md §2.2, §3` · EARS notation (D-057).

## Objetivo

Especificar o caminho de leitura: consulta paginada/filtrada da timeline, consulta específica de veredito
de feedback (via filtro de `factType`), fetch por id, e a verificação de cadeia sob demanda.

## Escopo

**In:** `GET /audit-facts/{id}`, `GET /history` (filtros + paginação + ordenação + scope de cuidador),
consulta de veredito de feedback como caso especial de `/history`, `GET /history/verify`. **Out:** escrita
(SPEC-02); resolução de scope ACS detalhada (SPEC-10 / integration-contracts).

## Decisões técnicas

- **Timeline** ordenada por `recorded_at` (índice `ix_audit_record_timeline` / `ix_audit_record_recorded_at`),
  default `DESC`. Filtros combinados por AND: `subjectType(+subjectId)`, `factType` (OR-set), `actorCaregiverId`,
  `from`/`to`.
- **Feedback-verdict query**: HIST é *sink*; o veredito de feedback é **de propriedade do EVNT**
  (`Evento` + verdict). Em HIST ele aparece como **fato auditado** com `factType ∈
  {evento.confirmado, evento.descartado, evento.feedback.registrado}`. A consulta de "veredito" é portanto
  `GET /history?subjectType=paciente&subjectId=…&factType=evento.confirmado&factType=evento.descartado&factType=evento.feedback.registrado`.
  HIST **não** recalcula nem reinterpreta o veredito — devolve o snapshot no `payload`. (Gap G1 da visão:
  o *owner* de escrita do feedback é evnt-service; HIST apenas espelha.)
- **Scope de cuidador** (SPEC-06/SPEC-10): `hist:read` sem `hist:read:all` restringe aos pacientes do
  escopo ACS do chamador; ACS indisponível → fail-closed `503`.
- **Verificação** (ADR-006): recomputa hashes do stream desde o genesis; retorna `intact` | `broken@N` |
  `empty`, sem mutar nada.

## Implementação

- `HistoryQueryService.queryTimeline(filter, page)` → `Page<AuditRecordResponse>`.
- `ChainVerificationService.verifyStream(streamKey, window)` → itera registros ordenados por `sequenceNo`,
  valida `prevHash` linkage e `recordHash == sha256(prevHash ‖ canonical(record))`; primeiro fail define
  `brokenAtSequenceNo`.

## Casos de borda

- `subjectId` sem `subjectType` → `400` (`/errors/incomplete-subject-filter`).
- `from > to` → `400` (`/errors/invalid-time-window`).
- Stream vazio em `/verify` → `200` `status:"empty"`, `recordsVerified:0`.
- Registro de correção aparece na timeline com `correctsRecordId` preenchido; o original permanece visível.
- `size` acima de 200 → clamp para 200 (não erro).

## Critérios de aceite (EARS)

- THE SYSTEM SHALL return timeline results only from the caller's tenant schema, ordered by `recordedAt`
  per `?order` (default `desc`), paginated per `?page`/`?size` (size clamped to 200).
- WHEN `subjectId` is supplied without `subjectType`, THE SYSTEM SHALL reject with `400`.
- WHEN a feedback-verdict query filters by `factType ∈ {evento.confirmado, evento.descartado,
  evento.feedback.registrado}`, THE SYSTEM SHALL return the matching audit records with the producer's
  verdict snapshot in `payload` and SHALL NOT recompute or reinterpret the verdict.
- WHERE a caller holds `hist:read` but not `hist:read:all`, THE SYSTEM SHALL restrict results to the
  caregiver's ACS-resolved patient scope; IF ACS is unavailable, THEN THE SYSTEM SHALL respond `503`
  (`/errors/scope-unavailable`) and SHALL NOT return unscoped results.
- WHEN a non-empty stream satisfies every hash and linkage check, THE SYSTEM SHALL respond
  `status:"intact"` with `recordsVerified` equal to the stream length.
- IF any record fails its hash or `prevHash` linkage, THEN THE SYSTEM SHALL respond `status:"broken"` with
  `brokenAtSequenceNo` set to the first failing `sequenceNo`.
- WHEN the requested `id` exists only in another tenant, THE SYSTEM SHALL respond `404` (no cross-tenant
  leak).
