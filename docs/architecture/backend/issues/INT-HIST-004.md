# INT-HIST-004 -- MIG migration scripts integration test (V0001..V0005 apply cleanly)

## Contexto

Valida que os 5 scripts Flyway de HIST aplicam sem erro, na ordem correta, e produzem
o schema esperado (tabelas, constraints, índices, grants). Executado contra Testcontainers
PostgreSQL com o role `hist_service_role` criado.

## Spec de referencia

- SPEC-04 (migration plan §7 — ranges V0001..V0005)
- `contracts/data-model.md §7`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/migration/FlywayMigrationTest`:**
- Criar role `hist_service_role` antes de aplicar migrations (DDL de setup no test container init script).
- Aplicar V0001..V0005 via Flyway em um schema `t_test`.
- Assert pós-migration:
  - `audit_record` existe com 15 colunas, PK, `UNIQUE(fact_id)`, `UNIQUE(stream_key, sequence_no)`, `UNIQUE(record_hash)`, FK self `corrects_record_id`.
  - `audit_idempotency` existe com PK `fact_id` e FK → `audit_record.id`.
  - `stream_head` existe com PK `stream_key`.
  - GRANT: INSERT+SELECT em `audit_record` e `audit_idempotency` para `hist_service_role`; sem UPDATE/DELETE.
  - Tentativa de `UPDATE audit_record` pelo role → `ERROR: permission denied`.
- Flyway checksum stable: re-executar migrations → `No migration necessary` (sem re-run).

## Resultado esperado

V0001..V0005 aplicam em < 5s. Todos os asserts de schema passam. Re-execução é idempotente.

## Criterios de aceite

- [ ] Flyway aplica V0001..V0005 sem erro e sem checksum conflict.
- [ ] Tabelas `audit_record`, `audit_idempotency`, `stream_head` existem com as constraints documentadas.
- [ ] `UPDATE audit_record SET fact_type='x' WHERE id=...` como `hist_service_role` → negado pelo banco.
- [ ] Índices `ix_audit_record_timeline`, `ix_audit_record_fact_type`, `ix_audit_record_actor`, `ix_audit_record_recorded_at`, `ix_audit_record_stream` existem.
- [ ] Re-execução do Flyway = `No migration necessary`.

## Dependencias

- HIST-011 (scripts de migration — fonte dos arquivos .sql)
- HIST-001 (datasource Testcontainers)

## Estimativa

S

## Prioridade

P3
