# INT-MIG-002 -- Gate do pipeline de deploy baseado em exit code

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Cablear o pipeline de deploy para tratar a MIG como job de gate: bloquear o rollout dos servicos de negocio quando o exit code for diferente de 0 (integration-contracts.md §2).

## Spec de referencia
- SPEC-10

## Escopo
- [ ] implementar `.github/workflows/deploy.yml`
- [ ] step de CI que invoca `--mode=migrate-all` e verifica o exit code

## Criterios de aceite
- [ ] o pipeline de deploy interrompe o rollout dos servicos de negocio quando a MIG sai com codigo != 0
- [ ] o relatorio JSON da execucao fica disponivel como artefato do pipeline

## Dependencias
Depende de MIG-001, MIG-006.

## Estimativa
S

## Prioridade
P3
