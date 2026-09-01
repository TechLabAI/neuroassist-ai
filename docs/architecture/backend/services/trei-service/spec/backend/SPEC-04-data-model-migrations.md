# SPEC-04 — Data Model + Migrations

> EARS acceptance criteria (D-057). Canonical DDL:
> [`../../contracts/data-model.md`](../../contracts/data-model.md).

## Objetivo

Definir as tabelas **privadas** de TREI e como TREI escreve nas tabelas **compartilhadas** do CONV, e como
as migrations chegam ao serviço centralizado MIG.

## Escopo

- **In:** DDL de `trei_sessao_treinamento`, `trei_proposta_llm`, `trei_conteudo_autoria`,
  `trei_publicacao_outbox`; regras de escrita nas tabelas `conv_*`; plano de migração (banda reservada MIG).
- **Out:** DDL das tabelas `conv_*` (owned pelo CONV — não re-declarar, data-model §0).

## Decisões técnicas

- **Não duplicar** as tabelas da árvore: TREI escreve em `conv_arvore_fala`/`conv_no_arvore_fala`/
  `conv_no_conteudo` no mesmo schema; declara DDL só das 4 tabelas próprias.
- `trei_conteudo_autoria` guarda o sub-estado fino (`rascunho|aguardando_aprovacao|publicando|ativo|
  descartado`) e o `embedding_model_id` (G5) — que `conv_no_conteudo` não modela.
- CHECK DDL: `sub_estado='ativo'` exige `embedding_model_id` e `embedding_ref` não-nulos (gate G5).
- Migrations centralizadas no MIG; banda reservada `V0201..V0203`; rodam **após** as tabelas `conv_*`
  existirem (FK em `conv_no_arvore_fala`).

## Implementação

- Blocos ```` ```sql ```` executáveis em data-model §2 são o contrato canônico consumido por
  `be-migration-generator.py` (Flyway) e `be-entity-generator.py` (JPA).
- Comentário `-- schema: empresa` em cada bloco roteia o DDL para o subdir por-tenant.

## Casos de borda

- Colisão de versão Flyway com CONV/outros serviços → banda reservada + confirmação com o dono do MIG.
- Erasure LGPD por `paciente_id` cascateia TREI PG + `conv_*` + ES + áudio (coordenado, data-model §8).

## Critérios de aceite (EARS)

- THE SYSTEM SHALL declare DDL only for the four `trei_*` tables and SHALL write into the CONV-owned
  `conv_*` tables without re-declaring them.
- IF `trei_conteudo_autoria.sub_estado = 'ativo'`, THEN `embedding_model_id` AND `embedding_ref` SHALL both
  be non-null (DDL CHECK — G5 gate).
- THE SYSTEM SHALL keep `conv_no_conteudo.status = 'pendente'` WHILE the authoring sub-state is `rascunho`
  or `aguardando_aprovacao`.
- THE SYSTEM SHALL ship migrations to MIG in the reserved band and SHALL order them after the `conv_*`
  tables (FK dependency).
