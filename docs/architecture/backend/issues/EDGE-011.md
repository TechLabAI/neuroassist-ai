# EDGE-011 -- Generate Flyway migration V0700__edge_initial_schema.sql (4 tables, pgcrypto extension, indexes, constraints) and contribute to MIG

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
EDGE nao roda Flyway localmente — contribui scripts para o servico centralizado MIG (neuroassist-ai-migrations, range V0700..V0799). A migration inicial cria as 4 tabelas do data-model.md na ordem correta: pgcrypto em schema global, depois tabelas empresa em ordem de FK. Range nao colide com PAC (V0001..) e GEST (V0400..).

## Spec de referencia
- SPEC-04

## Escopo
- [ ] executar `python scripts/be-migration-generator.py --service edge-service` para gerar o SQL das 4 tabelas a partir de `data-model.md`
- [ ] verificar e corrigir o SQL gerado: pgcrypto extension (schema global), `dispositivo_edge`, `dispositivo_enrollment`, `dispositivo_credential`, `dispositivo_edge_transition` (nessa ordem para respeitar FKs)
- [ ] copiar script para `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0700__edge_initial_schema.sql`
- [ ] adicionar bloco de extension em migration global `V0010__pgcrypto.sql` (ou adicionar IF NOT EXISTS ao existente)
- [ ] verificar que o script aplica sem erros em PostgreSQL 16 limpo com todos indexes e constraints do data-model.md

## Resultado esperado
Script V0700 aplica sem erros e cria as 4 tabelas com todos indexes e constraints do data-model.md.

## Criterios de aceite
- [ ] Script aplica sem erros em PostgreSQL 16 (verificar com Testcontainers no EDGE-019)
- [ ] Indice parcial `ux_dispositivo_edge_active_pairing` criado corretamente
- [ ] Constraint `ux_dispositivo_credential_active` (ativo unico por device) criado
- [ ] Tabela `dispositivo_edge_transition` sem coluna `updated_at` (append-only)
- [ ] Range V0700-V0799 sem colisao com PAC (V0001..) e GEST (V0400..)

## Dependencias
- EDGE-010 (precede)

## Estimativa
S

## Prioridade
P3
