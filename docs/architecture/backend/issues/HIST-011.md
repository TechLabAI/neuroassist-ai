# HIST-011 -- Flyway migration scripts V0001..V0005 (empresa schema)

## Contexto

HIST não executa DDL no boot (ADR-003 shared). Em vez disso, autora scripts
`V<n>__hist_*.sql` que o serviço MIG (`neuroassist-ai-migrations`) aplica em cada
schema `t_<empresa>`. Este issue cria os 5 scripts conforme `data-model.md §7`.

## Spec de referencia

- SPEC-04 (Data Model & Migrations — migration plan §7)
- `contracts/data-model.md §0, §2, §4, §7`
- ADR-003 (shared — migrações centralizadas no MIG)

## Escopo

**Localização:** `hist-service/src/main/resources/db/migration/empresa/`
(subdiretório `empresa` — MIG aplica em `t_<empresa>` schemas):

- `V0001__hist_extensions.sql`: `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; CREATE EXTENSION IF NOT EXISTS "pgcrypto";` (schema: global — anotar comentário de roteamento).
- `V0002__hist_audit_record.sql`: `CREATE TABLE audit_record` completo com constraints e índices conforme `data-model.md §2.1`.
- `V0003__hist_audit_idempotency.sql`: `CREATE TABLE audit_idempotency` conforme §2.2.
- `V0004__hist_stream_head.sql`: `CREATE TABLE stream_head` conforme §2.3.
- `V0005__hist_immutability_grants.sql`: REVOKE UPDATE/DELETE + GRANT INSERT/SELECT no ledger conforme §4 (usando placeholder `${hist_service_role}`).
- `V0010+`: range reservado (comentário no script V0005).
- `application.yml`: `spring.flyway.enabled=false` (MIG roda; hist-service não roda DDL no boot).
- `application-test.yml`: `spring.flyway.enabled=true, spring.flyway.locations=classpath:db/migration/empresa` (Testcontainers roda as migrations nos testes).

## Resultado esperado

Testcontainers executa os 5 scripts sem erro. As tabelas existem com todas as constraints
e os GRANTs aplicados ao role `${hist_service_role}`.

## Criterios de aceite

- [ ] Flyway aplica V0001..V0005 sem checksum conflict ou erro SQL em Testcontainers PostgreSQL 15.
- [ ] `audit_record` tem `UNIQUE (fact_id)`, `UNIQUE (stream_key, sequence_no)`, `UNIQUE (record_hash)`, FK self `corrects_record_id`.
- [ ] `audit_idempotency` tem PK `fact_id` e FK → `audit_record.id`.
- [ ] `stream_head` tem PK `stream_key`.
- [ ] V0005 executa REVOKE UPDATE, DELETE ON `audit_record` e `audit_idempotency` de PUBLIC.
- [ ] `spring.flyway.enabled=false` em `application.yml` (hist-service não roda DDL no boot).
- [ ] `spring.flyway.enabled=true` em `application-test.yml` (testes rodam migrations).

## Dependencias

- HIST-001 (datasource configurado)

## Estimativa

M

## Prioridade

P3
