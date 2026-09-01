# CONV-008 -- Criar Flyway migrations V0001-V0003 (pgcrypto, tree tables, audit outbox) — shipped para MIG

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O CONV não roda seu próprio Flyway em runtime — as migrations são **shipped para o serviço MIG**
(`neuroassist-ai-migrations`) que provisiona todos os schemas (doc 05 / `data-model.md` §5).
Esta issue cria os 3 scripts SQL do CONV, derivados do DDL canônico do `data-model.md`, prontos
para serem integrados ao MIG. O CONV `build.gradle.kts` inclui Flyway como `runtimeOnly` para
validação de schema em ambiente local, mas não executa migrations diretamente em produção.

## Spec de referencia

- SPEC-04 (data-model-migrations — plano de migrations)
- `contracts/data-model.md` §5 (scripts V0001-V0003 e seus escopos)

## Escopo

- [ ] Criar `src/main/resources/db/migration/V0001__conv_extensions.sql` — schema global: `CREATE EXTENSION IF NOT EXISTS pgcrypto`
- [ ] Criar `src/main/resources/db/migration/V0002__conv_create_tree_tables.sql` — schema empresa: DDL de `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo` com todos os índices e constraints do `data-model.md` §2.1-2.3
- [ ] Criar `src/main/resources/db/migration/V0003__conv_create_audit_outbox.sql` — schema empresa: DDL de `conv_audit_outbox` com índice parcial `uq_conv_outbox_client_event` do `data-model.md` §2.4
- [ ] Validar scripts com `./gradlew :conv-service:flywayValidate` (usando H2/PostgreSQL test container) — sem erros de sintaxe
- [ ] Documentar no README das migrations que estes scripts devem ser copiados/referenciados no `neuroassist-ai-migrations` (MIG)

## Resultado esperado

3 scripts SQL executáveis, com DDL idêntico ao canônico do `data-model.md`, prontos para integração no MIG.

## Criterios de aceite

- [ ] `./gradlew :conv-service:flywayValidate` (com test container PostgreSQL) retorna SUCCESS
- [ ] `V0002` cria `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo` com todos os índices (`ix_conv_arvore_paciente`, `ix_conv_no_arvore`, `ix_conv_no_parent`, `ix_conv_no_engine_ref`, `uq_conv_no_root`, `ix_conv_conteudo_no`, `ix_conv_conteudo_locale`)
- [ ] `V0003` cria `conv_audit_outbox` com índice parcial `uq_conv_outbox_client_event WHERE client_event_id IS NOT NULL`
- [ ] Nenhum script usa `CREATE TABLE IF NOT EXISTS` (Flyway não reexecuta scripts bem-sucedidos; IFs são desnecessários e mascaram erros)

## Dependencias

- CONV-002 (entidades JPA — DDL deve ser coerente com o mapeamento JPA)

## Estimativa

M

## Prioridade

P3
