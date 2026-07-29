# INT-MIG-005 -- Guard de nao-dependencia do TechLab AI (sem Feign client, scan de dependencias)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
Adicionar um guard (scan de dependencias/build) que impeca a introducao de um Feign client ou dependencia do techlab-ai na MIG, reforcando a decisao N/A registrada em techlab-ai-integration.md.

## Spec de referencia
- SPEC-10

## Escopo
- [ ] implementar `build.gradle.kts`
- [ ] verificacao no `build.gradle.kts` (ou script de CI)

## Criterios de aceite
- [ ] o build falha se `spring-cloud-starter-openfeign` ou qualquer dependencia `techlab-ai` for adicionada a este servico

## Dependencias
Nenhuma.

## Estimativa
S

## Prioridade
P6
