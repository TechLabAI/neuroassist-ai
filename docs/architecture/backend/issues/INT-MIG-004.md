# INT-MIG-004 -- Registro no Eureka (REG) da face HTTP interna

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Registrar a face HTTP interna da MIG no Eureka (REG) para descoberta por ACS e operadores, sem tornar isso pre-requisito do job core (hub-integration.md §3).

## Spec de referencia
- SPEC-06, SPEC-10

## Escopo
- [ ] implementar `infrastructure/EurekaConfig.java`
- [ ] `EurekaConfig`

## Criterios de aceite
- [ ] a face HTTP interna se registra no Eureka quando habilitada
- [ ] o job core de migracao funciona mesmo com o Eureka indisponivel

## Dependencias
Depende de MIG-002.

## Estimativa
S

## Prioridade
P6
