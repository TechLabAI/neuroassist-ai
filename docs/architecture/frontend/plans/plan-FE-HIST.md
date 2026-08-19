# Plano FE — HIST (neuroassist-ai-web)

> Gerado por: `/fe-planner` (Pipeline B, Etapa B3) | 2026-08-19
> Score baseline: **63/100 — Consistente** | Issues formais criadas: 0 (convertidas neste plano)
> Tipo de planejamento: **INCREMENTAL ESPECIFICO** — código funcional scaffolded; gaps isolados e claros
> Contexto: ES indisponível; fonte: module-state.md (2026-08-19 BASELINE) + leitura direta do código

---

## Status das prioridades

- **P1** — Lint fix: selector prefix + unused imports — pendente (FE-HIST-001)
- **P2** — Unit tests scaffold + coverage baseline — pendente (FE-HIST-002)
- **P3** — Module docs: issues formais + generator-config + README — pendente (FE-HIST-006)
- **P4** — E2E: unskip + implementar 13 cenários Playwright — pendente (FE-HIST-003)
- **P5** — Integração backend HTTP (DEFERRED — bloqueado) — pendente (FE-HIST-004/005, INT-FE-HIST-001/002)

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

HIST é a superfície de auditoria imutável do NeuroAssist AI. Exibe a linha do tempo de todos os eventos detectados — gestos de emergência e comandos de rotina — com marcação de veredito não-otimista (real / falso_positivo). A trilha é append-only e requer permissão `hist:read:all` para acesso. A verificação de integridade da cadeia (ADR-006) está definida na porta mas não exposta na UI ainda. O módulo HIST complementa EVNT (que captura e alarma) com auditoria histórica e feedback de qualidade.

### Estado atual observado

O código core foi scaffolded diretamente sem passar pelo pipeline architect formal (vision, specs, backlog, scaffold). O resultado é funcional: facade signals-based com todos os métodos (`load`, `setPeriod`, `loadMore`, `markFeedback`, `retry`), componente standalone OnPush com template Tailwind completo, tipos de domínio completos, ports e adapters (mock funcionando, HTTP stubs aguardando backend), routes + providers + descriptor + nav-config. O módulo está integrado no shell `neuroassist-ai-web`. Nenhuma issue formal existe; nenhum spec doc existe. Gates: lint FAIL (1 error, 2 warnings), test PASS (0 testes executados — `passWithNoTests: true`), e2e SKIP (13/13 cenários marcados `.skip`).

### Continuidade da última rodada

Não há histórico de execução anterior neste módulo (plano inaugural). Ponto de entrada: BASELINE 2026-08-19, score 63/100.

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|---|---|---|
| Estrutural (código) | OK | lib criada com tags `scope:hist, type:feature` corretas; provider factory; routes wired |
| Lint | FAIL | 1 error: selector prefix `hist-` → deve ser `lib-`; 2 warnings: imports não usados |
| Funcional (facade + component) | OK | Todos os métodos implementados; template com todos os estados; non-optimistic feedback |
| Unit tests | CRITICO | 0 arquivos `.spec.ts`; `passWithNoTests: true` mascara ausência |
| E2E | PENDENTE | 13 cenários definidos; todos `.skip`; Playwright config pronto; `hist-e2e` app existe |
| HTTP Integration | BLOQUEADO | `provideHist({ adapter: 'mock' })`; TODO[INT-FE-HIST-001] pending hist-service deploy |
| Docs | CRITICO | Nenhum spec doc; nenhuma issue formal; sem `generator-config.yml`; README placeholder |
| Multi-empresa | PARCIAL | Padrão documentado; adapters HTTP stub; interceptor não ativado (aguarda backend) |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

NAO. Os adapters HTTP (`HistClientHttpAdapter`, `HistFeedbackHttpAdapter`) são stubs com `TODO[INT-FE-HIST-001]`. O padrão `@neuroassist/shared/access-http-base` + `X-Empresa-Id` está documentado na arquitetura mas nao está wired porque hist-service e evnt-service nao estao deployados. Acao neste ciclo: manter stubs; acrescentar comentário `TODO[INT-FE-HIST-001]: add X-Empresa-Id via access-http-base interceptor` nos adapters HTTP em P5. Os adapters mock nao precisam do interceptor (dev/test).

---

## 1. Principais achados

### Pontos fortes

- `HistoryFacade`: signals-based correto; `.asReadonly()` em todos os sinais públicos; `markFeedback` non-optimistic com reconciliação 409; `retry()` re-executa o último período; `loadMore()` guarda com `#hasMore` + status check
- `HistoryTimelineComponent`: standalone, OnPush, template com todos os 5 estados (loading/skeleton, loaded, empty, error+retry, items+loadMore); feedback não-otimista por item com `markingState(id)` por signal
- Portas e adapters bem estruturados: `HistClientPort` (list, get, verify) + `HistFeedbackPort` (markFeedback); mock adapters retornam dados completos; toggle via `HistProviderConfig.adapter`
- Tags Nx corretas: `scope:hist, type:feature`; barrel `index.ts` exporta public API
- `hist-e2e` app já existe com `playwright.config.ts` e 2 spec files (8 + 5 cenários com stubs)

### Gaps

1. **Lint error (BLOQUEADOR):** `selector: 'hist-history-timeline'` deve ser `selector: 'lib-history-timeline'` — ESLint rule `@angular-eslint/component-selector` requer prefixo `lib` (`.eslintrc.json` do `libs/hist/`)
2. **Lint warnings (a remover):** `signal` importado mas não usado em `history-timeline.component.ts`; `takeUntilDestroyed` importado mas não usado em `hist.facade.ts`
3. **Unit tests ausentes:** 0 arquivos `.spec.ts`; cobertura 0%; `passWithNoTests: true` oculta ausência
4. **Docs ausentes:** sem `docs/architecture/frontend/modules/hist/frontend/frontend-spec/`; sem issues formais; sem `generator-config.yml`; README é placeholder
5. **E2E stubs:** 13/13 cenários marcados `test.skip()` com `TODO[FE-HIST-P9]`; assertions são `await expect(page).toHaveURL(/.*/)` (trivial, não testa nada)
6. **HTTP adapters stubs:** `HistClientHttpAdapter` e `HistFeedbackHttpAdapter` lançam NotImplementedError; `provideHist` usa `'mock'` por padrão

### Riscos

- **Risco P4 (e2e):** E2E requer app rodando em localhost:4200; se o shell não exportar a rota `/historico`, os testes falham por 404. Verificar que `HIST_ROUTES` está wired em `app.routes.ts` antes de iniciar P4.
- **Risco P5 (bloqueio externo):** Dependência de `hist-service` + GWY + `evnt-service` não deployados. Este risco nao bloqueia P1-P4.
- **Wave-0 bypass:** `histGuard` e `authGuard` estao em modo bypass; testes de guard devem verificar comportamento Wave-0 (nao autenticacao real), documentando claramente a expectativa atual.

### Aderencia as regras Nx (boundaries, tax-map, naming)

- Tags `scope:hist, type:feature` corretas. Boundaries: biblioteca `libs/hist` nao importa de outros módulos feature; exports via `index.ts`. Nenhuma violação de boundary detectada no código existente.
- A partir de P1: selector corrigido para `lib-history-timeline` — conforme `nx-boundaries.md` (prefixo `lib-` para componentes de library).

---

## 2. Estrategia geral

### Objetivo macro

Fechar os gates de qualidade do módulo HIST (lint zero, cobertura unit baseline, e2e 13/13) e criar a estrutura documental formal (issues, generator-config), mantendo os adapters HTTP em stub até hist-service e evnt-service serem deployados. O módulo deve estar PR-ready ao final de P4.

### Ordem das prioridades

| P | Titulo | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Lint fix: selector + imports | Bloqueador: 1 error impede PR; remover warnings antes de criar testes | FE-HIST-001 |
| P2 | Unit tests scaffold | Zero cobertura é gap crítico de qualidade; testa código já implementado | FE-HIST-002 |
| P3 | Module docs + issues formais | Baseline documental; rastreabilidade; generator-config.yml para pipelines futuros | FE-HIST-006 |
| P4 | E2E: unskip + implementar | 13 cenários skipped; `hist-e2e` app pronto; Playwright config OK | FE-HIST-003 |
| P5 | HTTP integration (deferred) | Bloqueado por backend; nao executa neste ciclo | FE-HIST-004, FE-HIST-005, INT-FE-HIST-001, INT-FE-HIST-002 |

---

## 3. Plano por prioridade

### Prioridade 1 — Lint fix: selector prefix + unused imports

Status: pendente
Issues cobertas: FE-HIST-001
Categoria: ESTRUTURAL

#### Objetivo

Fazer `npx nx lint hist` sair com 0 errors e 0 warnings.

#### Problemas identificados

1. `history-timeline.component.ts`, linha 29: `selector: 'hist-history-timeline'` viola `@angular-eslint/component-selector` (`prefix: "lib"` em `.eslintrc.json`). Correcao: `selector: 'lib-history-timeline'`.
2. `history-timeline.component.ts`, linha 5: `signal` importado de `@angular/core` mas nao usado no corpo da classe. Remover do import.
3. `hist.facade.ts`, linha 9: `takeUntilDestroyed` importado de `@angular/core/rxjs-interop` mas nao chamado em nenhum método. Remover do import. (`DestroyRef` em linha 6 pode ser removido também se só era usado para `takeUntilDestroyed`, mas `destroyRef` é injetado na linha 96 — verificar se é utilizado; se nao, remover também.)

#### Correcoes mandatorias

- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.ts`:
  - Remover `signal` da lista de imports de `@angular/core`
  - Mudar `selector: 'hist-history-timeline'` → `selector: 'lib-history-timeline'`
- `frontend/libs/hist/src/lib/hist.facade.ts`:
  - Remover `takeUntilDestroyed` do import `@angular/core/rxjs-interop`
  - Se a linha de import ficar vazia (nenhum outro import de `rxjs-interop`), remover a linha inteira
  - Verificar se `DestroyRef` e `this.destroyRef` sao usados em algum método; se nao houver chamada real, remover o inject e o import

#### Arquivos-alvo

- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.ts`
- `frontend/libs/hist/src/lib/hist.facade.ts`

#### Contrato de tipo

Sem alteracao de contratos — apenas limpeza de imports e renomeacao de selector. Componente nao muda comportamento.

#### Sequencia de execucao

1. Editar `history-timeline.component.ts`: remover `signal` do import list; mudar valor de `selector`
2. Editar `hist.facade.ts`: remover imports nao utilizados
3. Verificar: `cd frontend && npx nx lint hist` — deve retornar 0 errors, 0 warnings

#### Generators Nx obrigatorios

Nenhum.

#### Politica de testes

Nenhum novo teste nesta prioridade. Lint verifica estaticamente.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. P1 e puramente lint — sem alteracoes de runtime.

#### Criterios de aceite

```bash
cd frontend && npx nx lint hist
# Exit code: 0
# Output: nenhum erro, nenhum warning
```

#### Riscos

Baixo. Alteracoes cirurgicas de import + string de selector. Nenhum comportamento muda.

#### Instrucao para implementador

Abrir os dois arquivos, fazer as remocoes de import e a renomeacao do selector. Rodar `npx nx lint hist` para confirmar gate verde. Nao alterar logica, template, ou contratos.

---

### Prioridade 2 — Unit tests scaffold + coverage baseline

Status: pendente
Issues cobertas: FE-HIST-002
Categoria: VALIDACAO

#### Objetivo

Criar arquivos `.spec.ts` cobrindo os contratos comportamentais do módulo HIST. Meta: >= 20 testes passando, exercendo facade, component, guards e mock adapters.

#### Problema

0 arquivos `.spec.ts` existem. `passWithNoTests: true` no `jest.config.js` silencia a ausência. O módulo nao tem nenhuma evidência verificável de corretude.

#### Resultado esperado

`cd frontend && npx nx test hist` retorna exit 0 com >= 20 testes PASS documentados.

#### Problemas identificados

Ausência total de cobertura unit. Os mocks (`HistClientMockAdapter`, `HistFeedbackMockAdapter`) retornam dados predeterminados e sao seguros para teste isolado (sem HttpClient real). A facade usa `async/await` + `lastValueFrom` — testável com mocks do port injetado.

#### Correcoes mandatorias

Criar os seguintes arquivos (nenhum existe):

1. `frontend/libs/hist/src/lib/hist.facade.spec.ts` — testa HistoryFacade
2. `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.spec.ts` — testa HistoryTimelineComponent
3. `frontend/libs/hist/src/lib/access/hist.guard.spec.ts` — testa histGuard (Wave-0 behavior)
4. `frontend/libs/hist/src/lib/api/hist-client-mock.adapter.spec.ts` — contrato do mock client

#### Arquivos-alvo

```
frontend/libs/hist/src/lib/hist.facade.spec.ts
frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.spec.ts
frontend/libs/hist/src/lib/access/hist.guard.spec.ts
frontend/libs/hist/src/lib/api/hist-client-mock.adapter.spec.ts
```

#### Contrato de tipo dos testes

**hist.facade.spec.ts — cenarios obrigatorios:**
- `load('semana')` → status muda para 'loading' depois para 'loaded'; items nao vazios
- `load()` com port retornando lista vazia → status = 'empty'
- `load()` com port lancando erro → status = 'error'; `error()` nao-null
- `setPeriod('hoje')` → chama load() com o novo período
- `loadMore()` → quando hasMore = true, anexa items ao final da lista
- `markFeedback(id, 'real')` → sets marking state 'marking' → 'marked'; item.verdict atualizado
- `markFeedback(id, 'real')` com erro 409 → reconcilia verdict; marking = 'marked'
- `retry()` → chama load() com o ultimo período

**history-timeline.component.spec.ts — cenarios obrigatorios:**
- Renderiza titulo "Histórico"
- Renderiza 3 abas de período (hoje, semana, mes)
- Mostra skeleton quando status = 'loading' e items = []
- Mostra mensagem de erro e botao "Tentar novamente" quando status = 'error'
- Mostra "Nenhum evento neste período" quando status = 'empty'
- Mostra lista de items quando status = 'loaded'
- Clicar "Tentar novamente" chama facade.retry()
- Clicar aba "hoje" chama facade.setPeriod('hoje')

**hist.guard.spec.ts — cenarios obrigatorios:**
- histGuard retorna `true` (Wave-0 bypass ativo)

**hist-client-mock.adapter.spec.ts — cenarios obrigatorios:**
- `list(params)` retorna Observable com items e paginacao
- `get(id)` retorna Observable com registro
- `verify()` retorna Observable com resultado de verificacao

#### Sequencia de execucao

1. Criar `hist.facade.spec.ts` com mocks do `HIST_CLIENT_PORT` e `HIST_FEEDBACK_PORT` via `TestBed` providers
2. Criar `history-timeline.component.spec.ts` com `TestBed.configureTestingModule` e mock da `HistoryFacade`
3. Criar `hist.guard.spec.ts` verificando comportamento Wave-0
4. Criar `hist-client-mock.adapter.spec.ts` com TestBed básico
5. Rodar `npx nx test hist` — confirmar >= 20 PASS

#### Generators Nx obrigatorios

Nenhum. Criar arquivos `.spec.ts` diretamente.

#### Politica de testes

Unit obrigatório. Isolamento com mocks de ports. Nao usar HttpClientTestingModule (adapters HTTP sao stubs nao testados neste ciclo). Usar `TestBed` e `jest.fn()` / manual mocks.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. Testes unitários usam mock adapters que nao fazem HTTP.

#### Criterios de aceite

```bash
cd frontend && npx nx test hist
# Exit code: 0
# Tests: >= 20 PASS, 0 FAIL
```

#### Riscos

- `HistoryFacade` usa `lastValueFrom` com Observables dos ports — garantir que os mocks retornem `Observable<T>` (usar `of()` do RxJS nos mocks).
- Se `DestroyRef` for removido em P1 (porque era só usado com `takeUntilDestroyed`), verificar que o TestBed nao precisa prover `DestroyRef` — Angular 18 injeta automaticamente.

#### Instrucao para implementador

Criar os 4 arquivos spec listados. Usar `TestBed.configureTestingModule` com providers que substituem os ports por mocks (`jest.fn()` retornando `of(mockData)`). Nao usar `HttpClientTestingModule`. Focar em comportamento observable (signal values após chamadas async). Rodar `npx nx test hist` ao final.

---

### Prioridade 3 — Module docs: issues formais + generator-config + README

Status: pendente
Issues cobertas: FE-HIST-006
Categoria: ESTRUTURAL

#### Objetivo

Criar a estrutura documental formal do módulo HIST: issues rastreáveis, `generator-config.yml` para pipelines futuros, README operacional.

#### Problema

O módulo nao passou pelo pipeline architect formal. Nao existe:
- `docs/architecture/frontend/modules/hist/frontend/frontend-spec/` (pasta de specs)
- Issues formais `HIST-FE-*.md`, `INT-FE-HIST-*.md`
- `generator-config.yml`
- README atualizado (placeholder atual)

Sem rastreabilidade de issues, o estado do módulo só existe no `module-state.md` (que o Validator gerencia). Issues formais permitem que o autopilot e o implementador referenciem trabalho concreto.

#### Resultado esperado

Pasta `docs/architecture/frontend/modules/hist/frontend/frontend-spec/` criada com `generator-config.yml`. Issues criadas em `docs/architecture/frontend/issues/`. README de `frontend/libs/hist/README.md` atualizado com estado real.

#### Arquivos-alvo

```
docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml
docs/architecture/frontend/issues/HIST-FE-001.md
docs/architecture/frontend/issues/HIST-FE-002.md
docs/architecture/frontend/issues/HIST-FE-003.md
docs/architecture/frontend/issues/HIST-FE-004.md
docs/architecture/frontend/issues/HIST-FE-005.md
docs/architecture/frontend/issues/HIST-FE-006.md
docs/architecture/frontend/issues/INT-FE-HIST-001.md
docs/architecture/frontend/issues/INT-FE-HIST-002.md
frontend/libs/hist/README.md
```

#### Contrato de tipo (estrutura dos documentos)

**generator-config.yml — campos obrigatorios:**
```yaml
module: HIST
layer: fe
lib_name: "@neuroassist/hist"
lib_path: "frontend/libs/hist"
prefix: lib
tags:
  - scope:hist
  - type:feature
facade: HistoryFacade
component: HistoryTimelineComponent
route_path: /historico
adapter_toggle: HistProviderConfig
dependencies:
  client_port: HIST_CLIENT_PORT
  feedback_port: HIST_FEEDBACK_PORT
  http_backend: hist-service
  feedback_backend: evnt-service
```

**Issues HIST-FE-001..006 — estrutura:**
- Título, tipo (BUG/TEST/FEATURE/DOCS), prioridade, status (DONE/PENDENTE/BLOQUEADO)
- Descrição do gap
- Critério de aceite (comando nx exato)
- Arquivos afetados
- Referência ao plano (plan-FE-HIST.md, P<N>)

**INT-FE-HIST-001 — Integração HTTP hist-service:**
- Status: BLOQUEADO (aguardando hist-service deployment)
- Dependências BE: hist-service, Gateway route /hist/**
- Acao: substituir `provideHist({ adapter: 'http' })` em producao; implementar `histHttpInterceptorFn` com `X-Empresa-Id`

**INT-FE-HIST-002 — Integração feedback evnt-service:**
- Status: BLOQUEADO (aguardando evnt-service feedback endpoint)
- Dependências BE: evnt-service `POST /events/{id}/feedback`
- Acao: ativar `HistFeedbackHttpAdapter`; wiring X-Empresa-Id

**README — secoes obrigatorias:**
- Status do módulo (score, gates)
- Responsabilidades
- Como usar (`provideHist()`)
- Como rodar testes (`npx nx test hist`, `npx nx lint hist`)
- Dependências BE (hist-service, evnt-service) e status (BLOQUEADO)
- Issues abertas (links para HIST-FE-*.md)

#### Sequencia de execucao

1. Criar pasta `docs/architecture/frontend/modules/hist/frontend/frontend-spec/`
2. Criar `generator-config.yml` com campos acima
3. Criar `HIST-FE-001.md` .. `HIST-FE-006.md` em `docs/architecture/frontend/issues/`
4. Criar `INT-FE-HIST-001.md`, `INT-FE-HIST-002.md`
5. Atualizar `frontend/libs/hist/README.md` com estado real

#### Generators Nx obrigatorios

Nenhum.

#### Politica de testes

Nao aplicavel para docs. Verificar que todos os arquivos existem e sao nao-vazios.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao aplicavel nesta prioridade.

#### Criterios de aceite

```bash
# Verificar existencia dos arquivos
ls docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml
ls docs/architecture/frontend/issues/HIST-FE-001.md
ls docs/architecture/frontend/issues/INT-FE-HIST-001.md
ls frontend/libs/hist/README.md
# Cada arquivo deve ter conteudo nao-trivial (> 10 linhas)
```

#### Riscos

Baixo. Criacao de arquivos de doc sem dependencias de runtime.

#### Instrucao para implementador

Criar os arquivos listados com conteudo real (nao placeholders). Issues devem referenciar o plano e ter criterios de aceite verificaveis. O `generator-config.yml` deve refletir o estado real do código (nao aspiracional).

---

### Prioridade 4 — E2E: unskip + implementar 13 cenarios Playwright

Status: pendente
Issues cobertas: FE-HIST-003
Categoria: VALIDACAO

#### Objetivo

Implementar assertions reais nos 13 cenários Playwright (8 em `history-timeline.spec.ts` + 5 em `feedback-verdict.spec.ts`), removendo os `test.skip()` e os TODOs[FE-HIST-P9]. Todos os 13 devem passar.

#### Problema

Os 2 spec files existem com todos os cenários definidos mas marcados `test.skip()`. As assertions sao triviais (`toHaveURL(/.*/)`) e nao testam nenhum comportamento real.

#### Resultado esperado

`npx nx e2e hist-e2e` retorna exit 0 com 13/13 cenários PASS.

#### Arquivos-alvo

```
frontend/apps/hist-e2e/src/history-timeline.spec.ts  (8 cenários)
frontend/apps/hist-e2e/src/feedback-verdict.spec.ts  (5 cenários)
```

#### Contrato de tipo (selectors e assertions esperadas)

**history-timeline.spec.ts:**
- "Deep-link do Início abre o histórico de hoje": `goto('/historico')` → `getByRole('heading', { name: 'Histórico' })` visível
- "Rota sem período aplica o período padrão": página carrega → aba "Semana" tem `aria-selected="true"`
- "Cada linha mostra o rótulo legível e o selo de urgência": itens visíveis com `.rounded-2xl` e badge de urgência
- "Trocar de período reinicia a paginação": clicar aba "Hoje" → lista recarrega (verificar spinner + itens)
- "Período vazio mostra estado tranquilizador": mock retorna lista vazia → `getByText('Nenhum evento neste período')` visível
- "Falha de carregamento mostra erro com nova tentativa": forcar erro → `getByRole('alert')` + botao "Tentar novamente"
- "Carregar mais anexa a próxima página": botao "Carregar mais" → lista cresce
- "Guarda de vínculo bloqueia acesso sem vínculo": Wave-0 bypass → `/historico` acessível (nao redireciona)

**feedback-verdict.spec.ts:**
- "Marcar veredito com sucesso mostra estado terminal": clicar "Foi real?" → botao desaparece; badge "✓ Marcado real" aparece
- "Falha na escrita de feedback mantém o item não avaliado": forcar erro → "Erro. Tentar de novo?" visível
- "Conflito 409 reconcilia o item com o veredito do backend": botao de marcar reconcilia sem crash
- "Selo de verificação da cadeia visível para coordenador": permissao `hist:read:all` → selo visível (Wave-0: qualquer usuário vê)
- "Selo de verificação da cadeia oculto para cuidador comum": permissao restrita → selo oculto (Wave-0: skip ou sempre passa)

#### Sequencia de execucao

1. Confirmar que `npx nx serve neuroassist-ai-web` sobe em localhost:4200 com rota `/historico` wired e `provideHist({ adapter: 'mock' })` ativo
2. Implementar assertions em `history-timeline.spec.ts` (8 cenários)
3. Implementar assertions em `feedback-verdict.spec.ts` (5 cenários)
4. Rodar `cd frontend && npx nx e2e hist-e2e` — confirmar 13/13 PASS

#### Generators Nx obrigatorios

Nenhum. Arquivos spec existem; apenas implementar assertions.

#### Politica de testes

E2E Playwright com `baseURL: http://localhost:4200`. Mock adapter ativo (nao requer backend). Usar seletores semânticos (`getByRole`, `getByText`, `getByLabel`) preferindo a seletores CSS. Cenários que dependem de permissão RBAC real: adaptar para comportamento Wave-0 (bypass ativo) e documentar com comentário `// Wave-0: ...`.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. E2E usa mock adapter; nao faz chamadas reais com X-Empresa-Id.

#### Criterios de aceite

```bash
cd frontend && npx nx e2e hist-e2e
# Exit code: 0
# Tests: 13/13 PASS (0 skip, 0 fail)
```

#### Riscos

- **RISCO P4-01:** Rota `/historico` deve estar registrada em `app.routes.ts` do shell. Se nao estiver, a página retorna 404 e todos os testes falham. Verificar antes de implementar assertions.
- **RISCO P4-02:** Mock adapter precisa retornar dados suficientes para cobrir todos os estados (loading, loaded, empty, error). Verificar `HistClientMockAdapter.list()` — se retornar sempre a mesma lista, cenários de estado vazio/erro requerem override do mock nos testes.
- **RISCO P4-03:** Cenários de "verificacao da cadeia" (Wave-0) podem ser simplificados para verificar apenas que a UI nao crasha, documentando a expectativa real com comentário.

#### Instrucao para implementador

Verificar primeiro que o app serve em localhost:4200 com rota `/historico` acessível. Abrir os 2 spec files; remover `test.skip` de cada cenário; implementar assertions usando `getByRole/getByText/getByLabel`. Para estados que o mock nao suporta naturalmente (erro, vazio), usar `page.route()` do Playwright para interceptar chamadas e retornar respostas específicas. Rodar `npx nx e2e hist-e2e` ao final.

---

### Prioridade 5 — Integração backend HTTP (DEFERRED)

Status: BLOQUEADO
Issues cobertas: FE-HIST-004, FE-HIST-005, INT-FE-HIST-001, INT-FE-HIST-002
Categoria: WIRING

#### Objetivo

Ativar os adapters HTTP (`HistClientHttpAdapter`, `HistFeedbackHttpAdapter`) quando hist-service e evnt-service estiverem deployados. Configurar `X-Empresa-Id` via `@neuroassist/shared/access-http-base`.

#### Condicao de desbloqueio

- `hist-service` deployado com rotas de GWY expostas
- `evnt-service` feedback endpoint disponível (`POST /events/{id}/feedback`)
- Gateway route `/hist/**` configurado

#### Acoes (nao executar agora)

1. Substituir `provideHist({ adapter: 'mock' })` por `provideHist({ adapter: 'http' })` em producao (`app.config.ts`)
2. Implementar `histHttpInterceptorFn` que adiciona `X-Empresa-Id: {{ empresaId }}` via `HIST_EMPRESA_CONTEXT` token — mesmo padrao de `acsHttpInterceptorFn` (commit 3e4328d)
3. Completar `HistClientHttpAdapter`: implementar `list()`, `get()`, `verify()` via `HttpClient`; URL base `${ACCESS_HTTP_BASE}/hist/audit-records`
4. Completar `HistFeedbackHttpAdapter`: implementar `markFeedback()` via `HttpClient`; URL `${ACCESS_HTTP_BASE}/evnt/events/{id}/feedback`
5. Promover `HIST_EMPRESA_CONTEXT` de Wave-0 stub para `EmpresaContextService` real
6. Remover `TODO[INT-FE-HIST-001]` após wiring validado

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

SIM — obrigatório em P5. Usar `@neuroassist/shared/access-http-base` como prefixo de URL e `histHttpInterceptorFn` como interceptor que injeta `X-Empresa-Id`. Declarar `isMock: false` após ativacao.

#### Criterios de aceite (quando desbloqueado)

```bash
cd frontend && npx nx lint hist && npx nx test hist && npx nx build neuroassist-ai-web
# Todos exit 0
# Smoke test manual: POST /hist/audit-records retorna 200; feedback marca veredito
```

#### Riscos

- Contrato da API hist-service pode diferir dos DTOs em `hist.types.ts` (AuditRecordResponse) — verificar com BE team antes de implementar
- evnt-service feedback endpoint pode ter schema diferente de `FeedbackWriteDto` — alinhar contrato

#### Instrucao para implementador

Nao executar ate que `hist-service` + GWY + `evnt-service` estejam confirmados como deployados. Quando desbloqueados, seguir o padrao `acsHttpInterceptorFn` (commit 3e4328d) para consistencia de multi-empresa.

---

## 4. Dependencias externas

| Dependencia | Status | Issues | Bloqueia |
|---|---|---|---|
| hist-service (backend) | NAO DEPLOYADO | INT-FE-HIST-001 | P5 |
| Gateway route /hist/** | NAO CONFIGURADO | INT-FE-HIST-001 | P5 |
| evnt-service feedback endpoint | NAO DISPONIVEL | INT-FE-HIST-002 | P5 |
| @neuroassist/shared/access-http-base | STUB Wave-0 | INT-FE-HIST-001/002 | P5 |
| neuroassist-ai-web (app shell) | DISPONIVEL | — | P4 (rota /historico) |

**P1, P2, P3, P4 sao totalmente independentes de backend.** Apenas P5 requer backend.

---

## 5. Restricoes arquiteturais

- **RULE-NX-01 (scope boundary):** `libs/hist` nao pode importar de outros `scope:*` (exceto `scope:shared`)
- **RULE-NX-02 (type boundary):** `type:feature` pode depender de `type:data-access`, `type:ui`, `type:util`; nao o inverso
- **Selector prefix:** `lib-*` obrigatorio (`.eslintrc.json` da lib) — corrigido em P1
- **OnPush obrigatorio:** `ChangeDetectionStrategy.OnPush` em todos os componentes — ja aplicado
- **Signals readonly:** `.asReadonly()` em todos os sinais públicos da facade — ja aplicado
- **Sem HttpClient direto em componentes:** injetado apenas em adapters/services — ja respeitado
- **Non-optimistic feedback:** facade marca verdict apenas apos confirmacao do backend — ja implementado

---

## 6. Referencias

- `docs/architecture/frontend/modules/hist/frontend/module-state.md` — BASELINE 2026-08-19
- `docs/architecture/frontend/standards/nx-boundaries.md` — regras de boundary e prefix
- `docs/architecture/shared/multi-empresa.md` — padrao X-Empresa-Id (relevante para P5)
- `docs/architecture/frontend/adr/ADR-006.md` — chain verification (se existir)
- `frontend/libs/hist/src/lib/hist.facade.ts` — SPEC-03 §3/§4 implementado
- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.ts` — SPEC-02 implementado
- `frontend/apps/hist-e2e/` — Playwright config + spec files (P4)
- Commit 3e4328d — `acsHttpInterceptorFn` (referencia de padrao para P5)
