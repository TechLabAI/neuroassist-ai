# TREI-002 -- Flyway migrations V0201-V0203 (shipped to MIG service)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

TREI não roda Flyway localmente — suas migrations são entregues ao serviço centralizado
**MIG** (`neuroassist-ai-migrations`), conforme doc 05 e data-model §6. Os scripts criam
as 4 tabelas privadas do TREI no schema `empresa` (`t_<empresa>`). A ordem é obrigatória:
`conv_no_arvore_fala` deve existir antes (FK de `trei_proposta_llm` e
`trei_conteudo_autoria`), garantida pela banda reservada V0201+ (após CONV V0001-V0003).

## Spec de referencia

- SPEC-04 (data-model-migrations §6 — plano de migrations, banda V0201-V0203)

## Escopo

- [ ] criar `src/main/resources/db/migration/V0201__trei_create_sessao_proposta.sql` — DDL de `trei_sessao_treinamento` e `trei_proposta_llm` (data-model §2.1-2.2) com todos os CHECKs, INDEXes e COMMENTs canônicos
- [ ] criar `src/main/resources/db/migration/V0202__trei_create_conteudo_autoria.sql` — DDL de `trei_conteudo_autoria` (data-model §2.3) incluindo CHECK G5 `(sub_estado<>'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL))` e UNIQUE(no_id,locale)
- [ ] criar `src/main/resources/db/migration/V0203__trei_create_publicacao_outbox.sql` — DDL de `trei_publicacao_outbox` (data-model §2.4) incluindo UNIQUE(no_id,locale,passo) e INDEX `ix_trei_outbox_due(estado,proxima_em)`
- [ ] garantir que os scripts não re-criam tabelas CONV (owned by CONV's migrations, §0)
- [ ] adicionar comentário de MIG coordination note (data-model §6) em cada script

## Resultado esperado

Três scripts SQL com DDL exatamente conforme data-model §2.1-2.4, prontos para serem
copiados ao MIG service. Nenhum script toca em `conv_*` tables. Banda V0201-V0203
globalmente única.

## Criterios de aceite

- [ ] Nenhum script contém `CREATE TABLE conv_*` (violaria a non-duplication rule, data-model §0)
- [ ] V0202 contém o CHECK G5 exato: `CHECK (sub_estado <> 'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL))`
- [ ] V0203 contém `UNIQUE (no_id, locale, passo)` no `trei_publicacao_outbox`
- [ ] `./gradlew :trei-service:compileJava` sai 0 (scripts não são executados no service, mas a estrutura é validada)

## Dependencias

- TREI-001 (precede — entidades devem estar definidas antes de verificar o DDL)

## Estimativa

M

## Prioridade

P1
