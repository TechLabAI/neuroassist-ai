<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Centralized Flyway across all schemas

## Status
aceita

## Contexto
NeuroAssist AI is multi-tenant with `schema-per-tenant` isolation: a shared global schema
(`neuroassist_global`) plus one schema per empresa (`t_<tenantId>`). Several services touch the
database. Two options exist for who owns schema evolution:
1. **Per-service Flyway** — each service migrates its own tables on startup.
2. **Centralized migrations service** — one service owns Flyway for every schema.

Per-service Flyway causes races on shared/global objects, makes "bring a new tenant schema to
head" nobody's job, and scatters DDL privilege across every runtime service — unacceptable for a
healthtech, LGPD-bound platform that must audit every structural change and guarantee tenant
isolation. The module registry already records the intended stance: MIG =
"Centralized Flyway (all schemas)".

## Decisão
Vamos usar um **serviço de migrações centralizado** (`neuroassist-ai-migrations`, MIG) como
**único** detentor de DDL. Ele possui o Flyway do schema global e o template de schema de tenant,
e é o único componente com credenciais de DDL. Serviços de negócio sobem com roles somente-DML
contra schemas já migrados; nunca executam DDL nem embutem Flyway. A ordem de deploy garante que
MIG leva os schemas a head antes de qualquer serviço de negócio iniciar.

## Consequências
**Positivas**
- Estado de schema determinístico e reproduzível em todos os schemas.
- Privilégio de DDL concentrado numa única identidade (least privilege nos demais serviços).
- "Levar a head" (global e por tenant) tem dono claro; provisionamento de nova empresa vira um
  passo único.
- Trilha de auditoria unificada (histórico Flyway por schema) — relevante para LGPD.

**Negativas / trade-offs aceitos**
- MIG é uma dependência temporal de deploy (todos assumem schema em head); a ordem de deploy
  passa a importar.
- "Migrate all tenants" cresce linearmente com o nº de empresas (janela de migração maior);
  mitigado com aplicação por schema + lock + fail-fast.
- Acoplamento organizacional: mudanças de schema de qualquer serviço passam pelo repositório/fluxo
  de MIG.
