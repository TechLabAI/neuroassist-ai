# HIST-010 -- JPA adapters: AuditLedgerAdapter, IdempotencyAdapter, StreamHeadAdapter

## Contexto

Adaptadores outbound que implementam os ports via Spring Data JPA. A postura de
imutabilidade (ADR-004) se manifesta aqui: `AuditLedgerAdapter` usa um `JpaRepository`
que **não** herda `save(entity)` genérico para update — apenas `insertOnly()` via
EntityManager.persist (nunca merge). O lock pessimista do stream-tip é feito via
`@Lock(PESSIMISTIC_WRITE)` em `StreamHeadRepository.lockByStreamKey`.

## Spec de referencia

- SPEC-04 (tabelas + postura de imutabilidade §4)
- SPEC-05 (StreamHeadPort: SELECT FOR UPDATE)
- ADR-004 (sem UPDATE/DELETE)
- `generator-config.yml` — `outbound_ports`

## Escopo

**Pacote `com.neuroassist.hist.infrastructure.persistence`:**
- `AuditLedgerAdapter` (`@Component`): implementa `AuditLedgerPort`. Usa `EntityManager.persist()` (não `save()`). `findById` → `Optional.ofNullable`. `findPageByFilter` → JPQL dinâmico com `CriteriaBuilder` ou `@Query` com Specifications.
- `AuditRecordRepository` (`JpaRepository<AuditRecordEntity, UUID>`): read-only queries; `@Query findByFactId(factId)`.
- `IdempotencyAdapter` (`@Component`): implementa `IdempotencyPort`. Usa `IdempotencyRepository.findById(factId)` e `persist()`.
- `StreamHeadAdapter` (`@Component`): implementa `StreamHeadPort`. `lockAndFetchTip`: `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint("javax.persistence.lock.timeout", "5000"))`. `upsertTip`: `save(entity)` — `stream_head` é updatable.
- Mapeamento bidirecional entre `AuditRecord` (domínio) e `AuditRecordEntity` (JPA): classe `AuditRecordMapper`.

## Resultado esperado

Testes de integração (Testcontainers) cobrindo: insert persiste + findById recupera + tentativa
de update via papel de banco é rejeitada pelo GRANT + lock pessimista serializa dois appends.

## Criterios de aceite

- [ ] `AuditLedgerAdapter.insert()` usa `EntityManager.persist()`, não `.merge()` ou `.save()`.
- [ ] `AuditLedgerAdapter` não expõe método `update` ou `delete`.
- [ ] `StreamHeadAdapter.lockAndFetchTip` adquire lock pesismista; dois threads simultâneos serializam.
- [ ] `findPageByFilter` respeita todos os filtros de `TimelineFilter` (subjectType, factType, from/to, etc.).
- [ ] `AuditRecordMapper` converte ida e volta sem perda de campo.
- [ ] Todos os adapters operam no schema `t_<empresa>` do tenant corrente.

## Dependencias

- HIST-009 (entities)
- HIST-004 (port interfaces a implementar)
- HIST-011 (migrations devem existir antes dos testes)

## Estimativa

M

## Prioridade

P3
