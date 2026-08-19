# HIST-FE-006 -- Module docs: generator-config, issues formais e README operacional

**Status:** DONE -- concluido 2026-08-19 (P3)
**Tipo:** DOCS
**Maturidade:** 3 -- Implementado e verificado
**Done/Total:** 10/10 itens

## Contexto

O modulo HIST foi scaffolded diretamente sem passar pelo pipeline architect formal (vision, specs, backlog, scaffold). Nenhum documento de rastreabilidade existia: sem `generator-config.yml`, sem issues formais, sem README operacional. Sem esses artefatos, o autopilot e o implementador nao tem como referenciar trabalho concreto, e o estado do modulo so existe em `module-state.md` (gerenciado pelo Validator). Esta issue cria a baseline documental formal retroativamente.

## Spec de referencia

- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P3
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/generator-config.yml` — formato de referencia (modulo single-lib similar ao HIST)
- `docs/architecture/backend/issues/GEST-003.md` — formato de issue de referencia

## Escopo

- [x] Criar pasta `docs/architecture/frontend/modules/hist/frontend/frontend-spec/`
- [x] Criar `docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` com estado real do modulo
- [x] Criar `docs/architecture/frontend/issues/HIST-FE-001.md` (lint fix — DONE, retroativo)
- [x] Criar `docs/architecture/frontend/issues/HIST-FE-002.md` (unit tests — DONE, retroativo)
- [x] Criar `docs/architecture/frontend/issues/HIST-FE-003.md` (E2E assertions — PENDENTE)
- [x] Criar `docs/architecture/frontend/issues/HIST-FE-004.md` (HTTP adapter — BLOQUEADO)
- [x] Criar `docs/architecture/frontend/issues/HIST-FE-005.md` (feedback adapter — BLOQUEADO)
- [x] Criar `docs/architecture/frontend/issues/INT-FE-HIST-001.md` (integracao hist-service — BLOQUEADO)
- [x] Criar `docs/architecture/frontend/issues/INT-FE-HIST-002.md` (integracao evnt-service — BLOQUEADO)
- [x] Atualizar `frontend/libs/hist/README.md` com estado real (score, gates, dependencias, issues)

## Resultado esperado

Todos os 10 arquivos criados/atualizados com conteudo nao-trivial (> 10 linhas). Issues referenciam o plano e tem criterios de aceite verificaveis. `generator-config.yml` reflete o estado real do codigo (nao aspiracional). README informa o time sobre o estado atual do modulo.

## Criterios de aceite

- [x] `ls docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` — arquivo existe
- [x] `ls docs/architecture/frontend/issues/HIST-FE-{001..006}.md` — 6 issues criadas
- [x] `ls docs/architecture/frontend/issues/INT-FE-HIST-{001,002}.md` — 2 integration issues criadas
- [x] `cat frontend/libs/hist/README.md` — conteudo nao-placeholder (> 30 linhas, score + gates + issues)
- [x] Nenhuma mudanca de codigo de producao realizada (docs apenas)

## Arquivos criados/atualizados

- `docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` (novo)
- `docs/architecture/frontend/issues/HIST-FE-001.md` (novo)
- `docs/architecture/frontend/issues/HIST-FE-002.md` (novo)
- `docs/architecture/frontend/issues/HIST-FE-003.md` (novo)
- `docs/architecture/frontend/issues/HIST-FE-004.md` (novo)
- `docs/architecture/frontend/issues/HIST-FE-005.md` (novo)
- `docs/architecture/frontend/issues/HIST-FE-006.md` (este arquivo)
- `docs/architecture/frontend/issues/INT-FE-HIST-001.md` (novo)
- `docs/architecture/frontend/issues/INT-FE-HIST-002.md` (novo)
- `frontend/libs/hist/README.md` (atualizado — substituido placeholder)

## Dependencias

HIST-FE-001 (P1 DONE), HIST-FE-002 (P2 DONE) — documentados retroativamente.

## Estimativa

S

## Prioridade

P3
