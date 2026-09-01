# CONV-009 -- Criar JPA repositories e adapters de persistência (ArvoreFala, NoArvoreFala, NoConteudo, AuditOutbox)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Implementa os adapters de persistência que ligam os ports de domínio ao PostgreSQL via Spring
Data JPA. Os repositories são Spring Data interfaces estendendo `JpaRepository`; os adapters
implementam quaisquer ports de leitura declarados em `domain/port/`. O CONV **não tem** ports de
escrita de `ArvoreFala`/`NoArvoreFala`/`NoConteudo` (authoring é do TREI); o único adapter de
escrita é o do `AuditOutbox` (insert, update status). Queries com tenant-scope usam schema-per-tenant
do monorepo (`TenantContextHolder`).

## Spec de referencia

- SPEC-04 (data-model-migrations — modelo de dados)
- `contracts/data-model.md` §2

## Escopo

- [ ] Criar `infrastructure/persistence/repository/ArvoreFalaRepository.java` (Spring Data: `findByPacienteId(UUID pacienteId): Optional<ArvoreFala>`)
- [ ] Criar `infrastructure/persistence/repository/NoArvoreFalaRepository.java` (`findByArvoreId(UUID arvoreId): List<NoArvoreFala>`)
- [ ] Criar `infrastructure/persistence/repository/NoConteudoRepository.java` (`findByNoIdAndLocale(UUID noId, String locale): Optional<NoConteudo>`; `findByNoIdInAndLocale(List<UUID> noIds, String locale): List<NoConteudo>` — para carregar todos os conteúdos de uma árvore em uma query)
- [ ] Criar `infrastructure/persistence/repository/AuditOutboxRepository.java` (`save(AuditOutbox)`, `findByClientEventId(String clientEventId): Optional<AuditOutbox>`, `findByStatusOrderByCreatedAt(StatusOutbox status): List<AuditOutbox>` — para o sweep de retry)
- [ ] Criar `infrastructure/persistence/adapter/AuditOutboxPersistenceAdapter.java` — implementa operações de escrita do outbox chamadas pelo `NodeSelectionService` e pelo `OutboxRetrySweepService`
- [ ] Criar `infrastructure/persistence/config/JpaConfig.java` se necessário para configuração de schema-per-tenant

## Resultado esperado

Repositories e adapters de persistência funcionando contra PostgreSQL de test container; queries tenant-scoped.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS com teste de integração Spring Data (test container PostgreSQL)
- [ ] `NoConteudoRepository.findByNoIdInAndLocale` carrega conteúdos de toda a árvore em uma única query (não N queries por nó — verificável via `@QueryHints` ou `@Query`)
- [ ] `AuditOutboxRepository.findByClientEventId` retorna `Optional.empty()` quando `client_event_id IS NULL` (idempotência por partial unique index, não por código)
- [ ] Nenhum repository em `infrastructure/` é acessado diretamente pela camada `api/` (ArchUnit RULE — infra não escapa para api)

## Dependencias

- CONV-002 (entidades JPA)
- CONV-008 (migrations — schema deve existir para testes de integração)

## Estimativa

M

## Prioridade

P3
