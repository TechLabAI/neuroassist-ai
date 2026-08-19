# HIST-FE-003 -- E2E: unskip e implementar 13 cenarios Playwright

**Status:** PENDENTE
**Tipo:** TEST
**Maturidade:** 1 -- Estrutura criada; assertions pendentes
**Done/Total:** 0/13 cenarios implementados

## Contexto

O app `hist-e2e` foi criado com Playwright configurado e 2 arquivos de spec (8 + 5 cenarios). Todos os 13 cenarios estao marcados `test.skip()` com `TODO[FE-HIST-P9]`, e as assertions atuais sao triviais (`toHaveURL(/.*/)` — nao testam comportamento real). A rota `/historico` esta wired em `neuroassist-ai-web` com `provideHist({ adapter: 'mock' })`, o que permite execucao de e2e sem backend real.

## Spec de referencia

- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P4
- `frontend/apps/hist-e2e/src/history-timeline.spec.ts` — 8 cenarios
- `frontend/apps/hist-e2e/src/feedback-verdict.spec.ts` — 5 cenarios

## Escopo

- [ ] Remover `test.skip()` de todos os 13 cenarios
- [ ] Implementar assertions em `history-timeline.spec.ts` (8 cenarios):
  - Deep-link `/historico` renderiza heading "Historico"
  - Rota sem periodo aplica periodo padrao (aba "Semana" aria-selected)
  - Cada linha mostra rotulo legivel e selo de urgencia
  - Trocar periodo reinicia paginacao (spinner + reload)
  - Periodo vazio mostra "Nenhum evento neste periodo"
  - Falha de carregamento mostra alerta + botao "Tentar novamente"
  - Carregar mais anexa proxima pagina
  - Guarda Wave-0 nao bloqueia acesso
- [ ] Implementar assertions em `feedback-verdict.spec.ts` (5 cenarios):
  - Marcar veredito com sucesso mostra badge terminal
  - Falha na escrita mantem item nao avaliado
  - Conflito 409 reconcilia item com veredito do backend
  - Selo de verificacao de cadeia visivel para coordenador (Wave-0)
  - Selo de verificacao oculto para cuidador comum (Wave-0 skip ou sempre passa)

## Resultado esperado

`cd frontend && npx nx e2e hist-e2e` retorna exit 0 com 13/13 cenarios PASS (0 skip, 0 fail).

## Criterios de aceite

- [ ] `cd frontend && npx nx e2e hist-e2e` — exit code 0
- [ ] 13/13 cenarios PASS, 0 skip, 0 fail
- [ ] Assertions usam seletores semanticos (`getByRole`, `getByText`, `getByLabel`)
- [ ] Cenarios Wave-0 documentados com comentario `// Wave-0: ...`

## Arquivos afetados

- `frontend/apps/hist-e2e/src/history-timeline.spec.ts`
- `frontend/apps/hist-e2e/src/feedback-verdict.spec.ts`

## Dependencias

- HIST-FE-001 (lint PASS — rota /historico funcional no shell)
- `neuroassist-ai-web` servindo em localhost:4200 com rota `/historico` wired
- `provideHist({ adapter: 'mock' })` ativo (sem dependencia de backend)

## Riscos

- RISCO P4-01: Rota `/historico` deve estar em `app.routes.ts`; verificar antes das assertions
- RISCO P4-02: Mock adapter deve retornar dados suficientes para todos os estados (loading, loaded, empty, error)
- RISCO P4-03: Cenarios de verificacao de cadeia podem requerer simplificacao Wave-0

## Estimativa

M

## Prioridade

P4
