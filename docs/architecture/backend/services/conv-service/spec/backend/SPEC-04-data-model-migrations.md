# SPEC-04 — Data Model and Migrations

## Objetivo
Fixar o modelo relacional do CONV (schema-per-tenant) e o plano de migração via MIG. DDL canônica
executável em [`../../contracts/data-model.md`](../../contracts/data-model.md). Resolve **G2** (tabela
normalizada `conv_no_conteudo` vs JSON).

## Escopo
**In:** tabelas `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo`, `conv_audit_outbox`;
extensão `pgcrypto`; mapeamento schema-level; plano Flyway `V0001..V0003`.
**Out:** índice Elasticsearch (design/index-design; não é migração Flyway); escrita de conteúdo (TREI).

## Decisões técnicas
- **G2 RESOLVIDO: tabela normalizada `conv_no_conteudo`** (unique `no_id, locale`), não coluna JSON —
  permite ativar/consultar/indexar cada locale de forma independente (`ADR-005`).
- Estrutura locale-invariante em `conv_no_arvore_fala`; conteúdo por locale em `conv_no_conteudo`.
- `paciente_id`/`engine_feature_ref` são referências **sem FK cross-service** (`ADR-013`).
- Migração centralizada no MIG (Flyway); CONV não roda migrator local (doc 05).
- Única tabela escrita em runtime pelo CONV: `conv_audit_outbox`.

## Implementação
- Blocos ```sql``` de data-model §0/§2 são o contrato do `be-migration-generator.py` (Flyway) e do
  `be-entity-generator.py` (JPA). Comentário `-- schema: global|empresa` roteia o DDL.
- Root único via `CREATE UNIQUE INDEX … WHERE parent_id IS NULL`.
- Idempotência do outbox via unique parcial em `client_event_id`.

## Casos de borda
- Novo schema de tenant → MIG provisiona as tabelas empresa-scoped.
- `audio_ref` presente sem `audio_formato` → violação de CHECK (rejeitado).
- Locale fora de {pt,en} ou status fora de {ativo,pendente} → CHECK rejeita.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL persist the speech tree in the per-tenant schema `t_<empresa>` with no `tenant_id` column (schema is the boundary).
- THE SYSTEM SHALL enforce `UNIQUE (no_id, locale)` on `conv_no_conteudo` so each node has at most one content row per locale.
- IF `audio_ref` is present, THEN THE SYSTEM SHALL require `audio_formato` in {mp3,opus}.
- THE SYSTEM SHALL enforce exactly one root per tree via the partial unique index on `parent_id IS NULL`.
- THE SYSTEM SHALL reference `paciente_id` and `engine_feature_ref` without a cross-service foreign key.
