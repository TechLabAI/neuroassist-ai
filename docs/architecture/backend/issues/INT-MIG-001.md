# INT-MIG-001 -- Pre-flight check PostgreSQL (conectividade + role privilegiada + registry legivel) -> exit 3

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Implementar o pre-flight check obrigatorio antes de qualquer DDL: conectividade com PostgreSQL, role privilegiada assumida e tabela `tenant` legivel (integration-contracts.md §1).

## Spec de referencia
- SPEC-10, SPEC-02

## Escopo
- [ ] implementar `infrastructure/PreflightChecker.java`
- [ ] `PreflightChecker`

## Criterios de aceite
- [ ] falha de conectividade, de role ou de leitura do registry resulta em exit 3 antes de qualquer DDL
- [ ] o pre-flight roda no inicio de toda invocacao, independente do `--mode`

## Dependencias
Depende de MIG-001, MIG-005.

## Estimativa
S

## Prioridade
P3
