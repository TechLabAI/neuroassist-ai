# SPEC-04 — Data Model & Migrations

> Service: `hist-service` · Module: HIST · Refs: ADR-003 (shared), ADR-004, ADR-006
> Contract: `contracts/data-model.md` · EARS notation (D-057).

## Objetivo

Congelar o modelo de dados do ledger e a estratégia de migração centralizada no MIG, incluindo a postura
de imutabilidade por GRANT.

## Escopo

**In:** tabelas `audit_record`, `audit_idempotency`, `stream_head`; índices; constraints; GRANT posture;
plano de migração; mapeamento de schema. **Out:** DDL executável (vive em `data-model.md` como blocos
```sql```); provisioning operacional do MIG.

## Decisões técnicas

- **Schema-per-empresa** (`t_<empresa>`); nenhum dado de negócio HIST no `neuroassist_global` (só
  extensions). Sem FK cross-schema, sem chain cross-tenant.
- **PK** `uuid_generate_v7()` (time-ordered). **Idempotência** por `UNIQUE (fact_id)` + tabela
  `audit_idempotency`. **Chain** por `UNIQUE (stream_key, sequence_no)` + `UNIQUE (record_hash)`.
- **Imutabilidade** defendida em 3 camadas (ADR-004): domínio sem mutação, aplicação só append, banco sem
  UPDATE/DELETE grant no ledger (`data-model.md §4`).
- **Migrações centralizadas no MIG** (ADR-003 shared): HIST autora `V<n>__hist_*.sql`; nenhum DDL no boot.
- `stream_head` é cache derivado (é atualizável; fora da postura de imutabilidade).

## Casos de borda

- Novo tenant provisionado pelo MIG → tabelas HIST materializadas com o mesmo conjunto de migrações.
- Tentativa de UPDATE/DELETE direto no banco pelo papel do serviço → negada pelo grant (defense-in-depth).
- Colisão de `record_hash` (praticamente impossível em SHA-256) → `UNIQUE (record_hash)` rejeita e o append
  retenta / falha explicitamente.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL store every `AuditRecord`, idempotency entry and stream head in the caller's tenant
  schema `t_<empresa>` and SHALL keep no HIST business data in `neuroassist_global`.
- THE SYSTEM SHALL enforce one record per `factId` via `UNIQUE (fact_id)` and one position per stream via
  `UNIQUE (stream_key, sequence_no)`.
- THE SYSTEM SHALL author schema changes as MIG-applied `V<n>__hist_*.sql` migrations and SHALL NOT run DDL
  at boot.
- IF the service DB role attempts `UPDATE` or `DELETE` on `audit_record`/`audit_idempotency`, THEN THE
  DATABASE SHALL deny it (grant posture, ADR-004).
- THE SYSTEM SHALL treat `stream_head` as a rebuildable derived cache and SHALL keep it consistent with the
  `audit_record` stream tip after every successful append.

## Nota LGPD (aberto)

Erasure sub-tenant por paciente preservando a cadeia é questão aberta (crypto-shredding do `payload`
mantendo o esqueleto hasheado). Decidida em SPEC-06 §Open + legal. No MVP, erasure = drop do schema do
tenant no off-boarding.
