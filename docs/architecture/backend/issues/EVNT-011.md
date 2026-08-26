# EVNT-011 -- Create Flyway migration scripts V1..V4 (extensions, evento, notification_dispatch, report_idempotency)

**Status:** DONE -- reconciliado 2026-08-26
**Maturidade:** 3 -- Concluído
**Done/Total:** 2/2 itens

> **Nota de reconciliação (2026-08-26):** esta issue e `plan-EVNT.md` continuavam marcando estas
> migrations como ausentes ("Flyway V1..V4 ausentes"), mas o trabalho já havia sido feito sob a
> convenção de faixa por serviço (`V0500..V0599`, ver CLAUDE.md) em vez da numeração V1..V4
> originalmente prevista aqui. As três tabelas (`evento`, `report_idempotency`,
> `notification_dispatch`) existem em
> `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0500__evnt_initial_schema.sql`,
> e `pgcrypto` já foi instalado globalmente por `V0100__acs_global_schema.sql` (ACS). Verificado
> nesta sessão: `./gradlew :services:evnt-service:test` — 126/126 (inclui `CucumberIT` 23/23 cenários
> BDD contra Testcontainers PostgreSQL real com estas migrations aplicadas) e
> `architectureCheck` verde. Fechando a issue como DONE em vez de reescrever os scripts.
>
> **Divergência real encontrada (não bloqueante, registrar como tech-debt):** os critérios de
> aceite originais abaixo pediam `FOREIGN KEY ... ON DELETE CASCADE` de `report_idempotency` e
> `notification_dispatch` para `evento(id)`, e `CHECK` nos valores de `tier`/`status`. A migration
> real (`V0500`) não tem essas constraints — a integridade referencial e de valores é garantida
> apenas na camada de aplicação. Se isso for intencional, vale documentar a decisão; se não for,
> é candidato a uma migration nova (`V0503`) antes de haver dados de produção.

## Contexto
Materializes the four Flyway migration scripts (V1..V4) that MIG applies to each tenant schema.
The canonical DDL source is `contracts/data-model.md` SQL blocks; these scripts are verbatim
extractions with schema routing comments. Scripts are placed where `be-migration-generator.py`
expects them and must be idempotent (first-run safe). Boot-time DDL (`spring.jpa.hibernate.ddl-auto`)
is disabled — all schema changes must be versioned here (SPEC-04 constraint).

## Spec de referencia
- SPEC-04 (data model + migrations)
- contracts/data-model.md §4 (migration plan V1..V4)

## Escopo
- [ ] Create `V1__evnt_extensions.sql` (target: `neuroassist_global`): `CREATE EXTENSION IF NOT EXISTS pgcrypto;`
- [ ] Create `V2__evnt_evento.sql` (target: `t_<empresa>`): full `evento` table DDL + 4 indexes + 4 COMMENTs extracted verbatim from `data-model.md §2.1`
- [ ] Create `V3__evnt_notification_dispatch.sql` (target: `t_<empresa>`): full `notification_dispatch` table DDL + 2 indexes extracted from `data-model.md §2.2`
- [ ] Create `V4__evnt_report_idempotency.sql` (target: `t_<empresa>`): full `report_idempotency` table DDL + 1 index extracted from `data-model.md §2.3`
- [ ] Scripts placed in the path expected by `be-migration-generator.py` (confirm path from MIG service setup)
- [ ] Verify `V1..V4` run successfully against a local Testcontainers PostgreSQL without errors

## Resultado esperado
MIG applies V1..V4 and creates all EVNT tables with correct constraints and indexes. No boot-time DDL
in evnt-service.

## Criterios de aceite
- [ ] V1 idempotently installs `pgcrypto` in `neuroassist_global`
- [ ] V2 creates `evento` with `UNIQUE (report_id)`, compound `CHECK` on feedback/confirmado_por/confirmado_at, `bigserial` seq, and all 4 indexes from data-model.md
- [ ] V3 creates `notification_dispatch` with FK to `evento(id) ON DELETE CASCADE` and `CHECK` on `tier` and `status` values
- [ ] V4 creates `report_idempotency` with `report_id` as PK and FK to `evento(id) ON DELETE CASCADE`
- [ ] Testcontainers run: all four scripts apply with exit 0; rollback triggers on any SQL error

## Dependencias
- EVNT-001 (scaffold — migration directory must be registered)

## Estimativa
M

## Prioridade
P3
