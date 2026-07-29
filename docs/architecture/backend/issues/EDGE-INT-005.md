# EDGE-INT-005 -- Integration with MIG — contribute EDGE DDL (V0700..V0799) to centralized migration service

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
EDGE nao roda Flyway localmente — contribui scripts para MIG (neuroassist-ai-migrations). A migration V0700 deve ser incluida no MIG e executada como parte do provisionamento de tenant. Range reservado: V0700-V0799, nao colide com PAC (V0001..) ou GEST (V0400..). Hub-integration §3.

## Spec de referencia
- SPEC-04, hub-integration.md

## Escopo
- [ ] copiar `V0700__edge_initial_schema.sql` (gerado em EDGE-011) para o diretorio de migrations do MIG service (`empresa/`)
- [ ] verificar que o MIG service inclui o script no range correto (apos V0699 e antes de V0800)
- [ ] executar `./gradlew :services:neuroassist-ai-migrations:flywayMigrate` em schema de teste e confirmar que as 4 tabelas EDGE sao criadas
- [ ] documentar o range V0700-V0799 no README do MIG service

## Resultado esperado
MIG aplica V0700 corretamente em qualquer tenant novo; as 4 tabelas EDGE existem apos provisionamento.

## Criterios de aceite
- [ ] Script V0700 incluido no MIG service sem conflito de versao
- [ ] `flywayMigrate` executa sem erros em PostgreSQL 16 limpo
- [ ] As 4 tabelas EDGE existem apos migration (dispositivo_edge, dispositivo_enrollment, dispositivo_credential, dispositivo_edge_transition)
- [ ] Range V0700-V0799 documentado no MIG README

## Dependencias
- EDGE-011 (precede)

## Estimativa
S

## Prioridade
P3
