# HIST-009 -- JPA entities: AuditRecordEntity, IdempotencyEntryEntity, StreamHeadEntity

## Contexto

Entidades JPA mapeiam as tabelas do ledger (`audit_record`, `audit_idempotency`, `stream_head`)
para Kotlin/Hibernate. As entidades do ledger (`audit_record`, `audit_idempotency`) são
imutáveis por design — campos `val`, sem setter, sem `@PreUpdate`. `stream_head` é o único
updatable (cache derivado, ADR-004/SPEC-04).

## Spec de referencia

- SPEC-04 (Data Model & Migrations — tabelas e constraints)
- ADR-004 (append-only; sem UPDATE/DELETE)
- `contracts/data-model.md §2`

## Escopo

**Pacote `com.neuroassist.hist.infrastructure.persistence.entity`:**
- `AuditRecordEntity` (`@Entity @Table("audit_record")`): campos `val` onde possível; sem `@PreUpdate`; `@Column(updatable=false)` em todos os campos de negócio. Mapeamento JSON de `payload` via Hibernate `@Type(JsonType)` (Jackson/hypersistence-utils).
- `IdempotencyEntryEntity` (`@Entity @Table("audit_idempotency")`): PK `factId`, FK `recordId` → `AuditRecordEntity`.
- `StreamHeadEntity` (`@Entity @Table("stream_head")`): updatable; PK `streamKey`; `@Version` para OCC (ou lock pessimista explícito via `lockAndFetchTip`).
- Enum `SubjectTypeDb` mapeado para `varchar(20)`.
- Configuração de schema dinâmico (schema-per-empresa): implementar `CurrentTenantIdentifierResolver` + `MultiTenantConnectionProvider` (Spring/Hibernate multi-tenancy schema).

## Resultado esperado

`./gradlew :hist-service:test` (Testcontainers) valida que entidades persistem e recuperam
todos os campos sem perda. `AuditRecordEntity` não permite update após insert.

## Criterios de aceite

- [ ] `AuditRecordEntity` persiste com todos os 15 campos do `audit_record` sem NPE.
- [ ] `payload` (jsonb) serializa/deserializa como `Map<String, Any>` sem perda.
- [ ] `@Column(updatable=false)` em `factId`, `factType`, `subjectType`, `subjectId`, `recordHash`, `prevHash`, `occurredAt`, `recordedAt`, `streamKey`, `sequenceNo`.
- [ ] Tentativa de update direto no `AuditRecordEntity` é bloqueada pelo grant do banco (teste de integração).
- [ ] `StreamHeadEntity` atualiza `lastSequenceNo` e `lastRecordHash` corretamente.
- [ ] Schema resolver pina `t_<empresa>` conforme claim do tenant.

## Dependencias

- HIST-001 (scaffold + datasource)
- HIST-002 (domain model para mapeamento)
- HIST-011 (migrations criam as tabelas — executar antes dos testes JPA)

## Estimativa

M

## Prioridade

P3
