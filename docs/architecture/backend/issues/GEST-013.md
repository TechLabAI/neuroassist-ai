# GEST-013 -- Generate Flyway migration V0400 for GEST DDL (4 tables: tipo_gesto, tipo_gesto_transition, detection_profile, detection_profile_primitive) and contribute to MIG service

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
GEST nao roda Flyway localmente — contribui scripts para o servico centralizado MIG (`neuroassist-migrations`). O DDL canonical esta em `data-model.md` (blocos sql) e deve ser materializado pelo `be-migration-generator.py`. A migration abrange os 4 tabelas do bounded context GEST mais o bloco de extensao pgcrypto (global schema). Faixa reservada: V0400..V0499.

## Spec de referencia
- SPEC-04, data-model.md (blocos DDL), hub-integration.md §3

## Escopo
- [ ] rodar `python scripts/be-migration-generator.py --service gest-service` para gerar `V0400__gest_initial_schema.sql` a partir dos blocos sql de data-model.md
- [ ] revisar o arquivo gerado e garantir que a ordem e: extensao pgcrypto (global), tipo_gesto, tipo_gesto_transition, detection_profile, detection_profile_primitive
- [ ] copiar o arquivo para `backend/services/gest-service/src/main/resources/db/migration/empresa/V0400__gest_initial_schema.sql`
- [ ] adicionar comentario no topo do arquivo documentando a faixa reservada e o servico dono

## Resultado esperado
`V0400__gest_initial_schema.sql` aplicavel a um schema de tenant em branco via `psql` sem erros; todas as constraints e indices presentes.

## Criterios de aceite
- [ ] `psql -f V0400__gest_initial_schema.sql` em schema vazio executa sem erros
- [ ] `\d tipo_gesto` mostra constraint `ck_tipo_gesto_supported_needs_ref` e index parcial `ux_tipo_gesto_nome_active`
- [ ] `\d detection_profile` mostra `ux_profile_version` e index parcial `ux_profile_single_active`
- [ ] `\d tipo_gesto_transition` mostra FK com ON DELETE RESTRICT para tipo_gesto

## Dependencias
GEST-002, GEST-003 (entidades definem o schema).

## Estimativa
S

## Prioridade
P3
