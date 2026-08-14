# Plano FE — GEST (neuroassist-ai-web)

> Gerado por: `/fe-planner` (pipeline B, etapa B3) | 2026-08-12
> Baseline score: **52/100 — Parcial** | Issues DONE: 12.5/33 (~38%)

---

## Status das prioridades

- **P1** — Nx Build Targets — pendente (BLOQUEADOR CRÍTICO)
- **P2** — Unit Tests (Facade + Adapters + Guard) — pendente
- **P3** — ADR-006 Interim Detector Banner — pendente
- **P4** — UX Polish — Loading/Error States + Navigation Breadcrumb — pendente
- **P5** — Multi-empresa Preparatory + Nx Boundary Rules + Observability — pendente
- **P6** — E2E Tests (Playwright) — pendente

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

GEST é a camada de integração de detecção de gesto do NeuroAssist AI. Expõe uma
única superfície de usuário — `GestureLiveTestComponent` — que é composta nas páginas
de calibração do PAC e onboarding. GEST é um módulo embedded (`showInNav: false`);
sem entrada de navegação standalone. O Edge Agent real (EAR/BRI via WS — ADR-006) é
o backend de runtime; sem inferência local no browser.

### Estado atual observado

O código funcional existe e está wired ao PAC (composition slot GEST-INT-005 concluído).
`GestureTestFacade`, `EdgeDetectionHttpAdapter`, `EdgeDetectionMockAdapter`,
`EligibilityHttpAdapter` e `GestureLiveTestComponent` foram implementados na rodada
anterior (PoC-to-real, branch `gest/p3-real-implementation-and-fe-wiring`).
O bloqueio é infraestrutural: `libs/gest/project.json` tem `targets: {}` — nenhum
gate (lint/test/build/e2e) pode ser executado, impedindo qualquer validação de CI.

### Continuidade da última rodada

Última implementação: wiring do GEST live-test no PAC calibração (commit `1830f75`).
Próxima lacuna crítica: build infra → unit tests → ADR-006 compliance → UX → wiring.

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|----------|--------|---------------|
| Estrutural | PARCIAL | targets:{} em project.json; sublib split adiado (deliberado) |
| Funcional | PARCIAL | Facade+adapters funcionam; ADR-006 banner não implementado |
| Testes | CRÍTICO | Zero .spec.ts; nenhum gate de qualidade executável |
| Integração | PARCIAL | PAC wired; onboarding slot pendente; boundary rules faltando |
| Observabilidade | BAIXO | Sem telemetria |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

**NÃO.** `access-http-base` e `empresa-context` são stubs Wave-0 (constante
`ACCESS_HTTP_BASE = '/api'`; interface mínima `EmpresaContext`). O gap é
**workspace-wide**, não apenas GEST. A ação planejada em P5 é preparatória:
injetar `EmpresaContext` em `EligibilityHttpAdapter` e adicionar o ponto de
injeção do header `X-Empresa-Id` comentado, pronto para quando o stub for
elevado. Wiring completo do interceptor está bloqueado em tarefa cross-equipe.

---

## 1. Principais achados

### Pontos fortes

- Facade signals-first com 7 mapeamentos estado→VM implementados corretamente
- Port/adapter pattern limpo; adapters real + mock separados e funcionais
- Invariante honest-degraded (ADR-005) cumprida: nunca emite "recognized" falso
- Ciclo de vida MediaStream correto: `acquireCamera`/`releaseCamera` + `takeUntilDestroyed()`
- Composição no PAC (`pac-comando-editor-page`) funcionando em produção

### Gaps

1. `project.json` com `targets: {}` — nenhum gate executável (BLOQUEADOR)
2. Zero arquivos `.spec.ts` — lógica crítica não testada
3. ADR-006 (aceito 2026-08-12) requer banner EAR/BRI interim — não implementado
4. Estados de loading/erro/vazio ausentes (GEST-FE-019..020)
5. Breadcrumb de navegação ausente (GEST-FE-018)
6. Sem telemetria (GEST-FE-021)
7. Regras ESLint `depConstraints` para `scope:gest` ausentes (GEST-INT-002)
8. Multi-empresa: ponto de injeção `X-Empresa-Id` não preparado
9. Slot de composição onboarding não confirmado (GEST-INT-006)
10. Sem testes Playwright; gate GEST-FE-026 não verificável (GEST-FE-022..026)

### Riscos

- `GEST-INT-004` (bridge token/auth WS) está explicitamente EXCLUÍDO deste plano —
  decisão cross-equipe GEST+EDGE pendente; continua com TODO em `gest.providers.ts`.
- Sublib split (7 sublibs per generator-config.yml) está ADIADO — o monolito
  funciona corretamente e o split introduz risco sem benefício imediato para o
  usuário; revisar quando P6 (E2E) estiver verde.
- PAC usa `scope:pac` importando `GestureLiveTestComponent` de `scope:gest` — isso é
  permitido porque a importação ocorre em `scope:web` (host page); a regra a
  enforçar via ESLint é: `scope:pac` libs internas NÃO importam `scope:gest`.

### Aderência às regras Nx

| Regra | Estado |
|-------|--------|
| RULE-NX-01: tags presentes | OK (`scope:gest`, `type:feature`) |
| RULE-NX-02: targets não vazios | FAIL (targets: {}) |
| RULE-NX-03: depConstraints | FAIL (sem regra para scope:gest) |
| RULE-NX-04: standalone components | OK |
| RULE-NX-05: signals readonly | OK |
| RULE-NX-06: sem HttpClient em components | OK (somente em adapters) |

---

## 2. Estratégia geral

### Objetivo macro

Elevar GEST de score 52 → 75+ (Consolidado) em um único ciclo de implementação,
desbloqueando os gates de CI, cobrindo a lógica crítica com unit tests, cumprindo
ADR-006, e estabelecendo os wiring de observabilidade e boundary enforcement.

### Decisão sobre sublib split

ADIADO. O monolito `libs/gest/` é deliberado ("pragmatic port first"). O split em
7 sublibs requer mover todos os imports, recriar project.json por sublib, e atualizar
tsconfig paths — risco alto, benefício zero para o usuário. Revisão agendada após P6.

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|--------|--------|-----------------|
| P1 | Nx Build Targets | BLOQUEADOR — sem targets nenhum gate executa | GEST-FE-003 |
| P2 | Unit Tests — Facade + Adapters + Guard | Zero cobertura; lógica crítica não verificável | GEST-FE-007, FE-008, FE-017 |
| P3 | ADR-006 Interim Detector Banner | Compliance obrigatório com ADR aceito 2026-08-12 | GEST-FE-011, FE-013 |
| P4 | UX Polish — Loading/Error + Navigation | Estados de UX ausentes; breadcrumb pendente | GEST-FE-018, FE-019, FE-020 |
| P5 | Multi-empresa Prep + Boundary Rules + Observability | Preparar interceptor; enforçar boundaries; telemetria | GEST-INT-002, FE-021 |
| P6 | E2E Tests (Playwright) | Gate GEST-FE-026 obrigatório; stream lifecycle | GEST-FE-022..026 |

---

## 3. Plano por prioridade

---

### Prioridade 1 — Nx Build Targets (BLOQUEADOR CRÍTICO)

**Status:** pendente
**Issues cobertas:** GEST-FE-003 (parcial — targets do project.json)
**Categoria:** ESTRUTURAL

#### Objetivo

Adicionar os targets `lint`, `test` e `build` ao `frontend/libs/gest/project.json`,
tornando executáveis os gates `npx nx lint gest`, `npx nx test gest` e
`npx nx build gest`. Sem estes targets, nenhuma outra prioridade pode ser validada.

#### Problema identificado

`frontend/libs/gest/project.json` tem `"targets": {}` — campo vazio. O workspace Nx
não consegue derivar targets automaticamente para uma library Angular sem configuração
explícita quando não há `@nx/angular:ng-packagr-lite` nem `@nx/jest:jest` declarados.
PAC (`libs/pac/project.json`) tem o mesmo problema, mas não é escopo deste plano.

#### Correções mandatórias

1. Adicionar target `lint` usando `@nx/eslint:lint` com padrão de arquivos `**/*.ts`.
2. Adicionar target `test` usando `@nx/jest:jest` apontando para `jest.config.ts` da lib.
3. Adicionar target `build` usando `@nx/angular:ng-packagr-lite` (buildable lib).
4. Criar `frontend/libs/gest/jest.config.ts` se não existir (verificar antes de criar).
5. Criar `frontend/libs/gest/tsconfig.spec.json` se não existir.
6. Verificar que `frontend/tsconfig.base.json` tem path mapping para `@neuroassist/gest`.

#### Arquivos-alvo

- `frontend/libs/gest/project.json` — targets a preencher
- `frontend/libs/gest/jest.config.ts` — criar se ausente
- `frontend/libs/gest/tsconfig.spec.json` — criar se ausente
- `frontend/tsconfig.base.json` — verificar path `@neuroassist/gest`

#### Contrato de tipo

Nenhum tipo novo. A modificação é exclusivamente de configuração Nx/Jest.

#### Sequência de execução

1. Ler `frontend/libs/gest/project.json` atual (confirmar `targets: {}`)
2. Ler outro lib com targets completos como referência (ex: `frontend/libs/shared/contracts/project.json`)
3. Copiar estrutura de targets adaptada para scope `gest`
4. Criar `jest.config.ts` se ausente — referência: outro lib do workspace
5. Criar `tsconfig.spec.json` se ausente
6. Verificar `tsconfig.base.json` paths
7. Rodar `npx nx test gest` — esperado: 0 testes encontrados, mas exit 0 (sem falha de config)
8. Rodar `npx nx build gest` — esperado: build success (lib vazia de outputs mas estrutura válida)
9. Rodar `npx nx lint gest` — esperado: exit 0 (sem regras violadas por ora)

#### Generators Nx obrigatórios

Nenhum generator de lib novo. Usar apenas edição direta do `project.json`.
Se preferir via CLI para garantir compatibilidade de schema:
```bash
cd frontend
npx nx g @nx/angular:configuration --project=gest
```

#### Política de testes

Esta fase não cria .spec.ts — apenas habilita a infra para que os testes possam ser
executados. Testes reais são criados em P2.

#### Multi-empresa

Não aplicável nesta fase.

#### Critérios de aceite

```bash
cd frontend
npx nx lint gest          # exit 0
npx nx test gest          # exit 0 (0 test suites, sem crash de config)
npx nx build gest         # exit 0 (dist/libs/gest gerado)
```

#### Riscos

- A estrutura exata dos targets pode diferir entre versões do Nx. Verificar
  `node_modules/nx/schemas/project-schema.json` para o schema correto.
- `tsconfig.spec.json` pode precisar referenciar `tsconfig.lib.json` que por sua vez
  referencia `tsconfig.json` — verificar cadeia antes de criar.

#### Instrução para implementador

Não usar `nx generate` para recriar a lib (isto apagaria o código existente). Editar
`project.json` diretamente com os targets de lint/test/build derivados de um lib de
referência do workspace. Verificar que `jest.config.ts` usa `preset: '../../../jest.preset.js'`
(raiz do workspace frontend) e não paths absolutos. Ao final, executar os três gates
acima e reportar saída.

---

### Prioridade 2 — Unit Tests: Facade, Adapters, Guard

**Status:** pendente
**Issues cobertas:** GEST-FE-007, GEST-FE-008, GEST-FE-017
**Categoria:** VALIDACAO

#### Objetivo

Criar arquivos `.spec.ts` para os 5 componentes de lógica crítica do módulo GEST,
cobrindo os caminhos principais e os edge cases de segurança (fail-closed em
emergência, honest-degraded invariant, teardown de MediaStream).

#### Problema identificado

Zero arquivos `.spec.ts` em `frontend/libs/gest/`. A lógica da facade (7 mapeamentos
estado→VM, transitions de state machine, teardown) e dos adapters (fallback degraded,
fail-open vs fail-closed por urgência) não tem cobertura automatizada. A guard
`gestDeviceOnlineGuard` também não foi testada.

#### Correções mandatórias

Criar os seguintes arquivos de teste (um arquivo por unidade):

1. `frontend/libs/gest/src/lib/gest.facade.spec.ts`
   — state machine: 7 signal→VM mappings, startTest→stopTest lifecycle, teardown via
   DestroyRef, subscriber limpo após releaseCamera

2. `frontend/libs/gest/src/lib/api/edge-detection-http.adapter.spec.ts`
   — openStream emite mensagens do WS; handlesa version mismatch (403 → degraded);
   close cancela subscription

3. `frontend/libs/gest/src/lib/api/edge-detection-mock.adapter.spec.ts`
   — mockStream emite sequência determinística de eventos; fecha corretamente

4. `frontend/libs/gest/src/lib/api/eligibility-http.adapter.spec.ts`
   — check returns eligible:true na resposta ok;
   — CRÍTICO: backend unreachable + urgencia=emergencia → eligible:false (fail-closed);
   — backend unreachable + urgencia!=emergencia → eligible:true (fail-open)

5. `frontend/libs/gest/src/lib/access/gest-device-online.guard.spec.ts`
   — guard retorna true quando device online; false/redirect quando offline

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/gest.facade.spec.ts` (criar)
- `frontend/libs/gest/src/lib/api/edge-detection-http.adapter.spec.ts` (criar)
- `frontend/libs/gest/src/lib/api/edge-detection-mock.adapter.spec.ts` (criar)
- `frontend/libs/gest/src/lib/api/eligibility-http.adapter.spec.ts` (criar)
- `frontend/libs/gest/src/lib/access/gest-device-online.guard.spec.ts` (criar)

#### Contrato de tipo

Os testes devem usar apenas tipos já exportados via `@neuroassist/shared/contracts`.
Mocks das dependências via `jest.fn()`. TestBed para componentes Angular, instância
direta para POJOs/services sem DI pesada.

Para `GestureTestFacade`: usar `TestBed.configureTestingModule` com provider mock do
`EDGE_DETECTION_PORT`:

```typescript
// padrão de mock do port
const mockPort: EdgeDetectionPort = {
  openStream: jest.fn(),
  mockStream: jest.fn(),
  close: jest.fn(),
};
```

#### Sequência de execução

1. Ler cada arquivo de implementação a testar (facade, adapters, guard)
2. Identificar os casos de teste a partir da lógica implementada
3. Criar spec files na ordem: eligibility adapter → mock adapter → http adapter → facade → guard
4. Executar `npx nx test gest` após cada arquivo criado
5. Todos os testes devem passar antes de avançar para P3

#### Política de testes

Unit obrigatório. Cobertura mínima de linha: 70% para facade e eligibility adapter
(casos críticos de segurança). Sem mocks de ES/HTTP externos — usar `HttpClientTestingModule`
para o eligibility adapter.

#### Multi-empresa

Não aplicável nesta fase.

#### Critérios de aceite

```bash
cd frontend
npx nx test gest          # exit 0, >= 15 testes passando
npx nx test gest --coverage  # cobertura >= 70% para facade + eligibility adapter
```

#### Riscos

- `GestureTestFacade` injeta `DestroyRef` via `inject()` — pode exigir `runInInjectionContext`
  no TestBed. Solução: usar `TestBed.runInInjectionContext(() => new GestureTestFacade())`.
- `EdgeDetectionHttpAdapter` usa WebSocket — mockar via `jest.spyOn(global, 'WebSocket')`.

#### Instrução para implementador

Ler os arquivos de implementação completos antes de escrever os testes. Não inventar
contratos — usar apenas o que `GestureTestFacade`, `EdgeDetectionHttpAdapter`,
`EligibilityHttpAdapter`, e `gestDeviceOnlineGuard` efetivamente fazem. O caso mais
crítico é o fail-closed do eligibility adapter (emergência com backend offline) —
garantir que este teste existe e passa.

---

### Prioridade 3 — ADR-006 Interim Detector Banner

**Status:** pendente
**Issues cobertas:** GEST-FE-011 (atualização do componente), GEST-FE-013 (DegradedBannerComponent)
**Categoria:** FUNCIONAL

#### Objetivo

Implementar o requisito obrigatório do ADR-006 (aceito 2026-08-12): quando o edge
agent responder com `detectorType: "ear_bri"` no handshake WS, o live-test panel deve
exibir um banner de aviso informando que a calibração usa o detector interim EAR/BRI,
não o ONNX definitivo.

#### Problema identificado

ADR-006 especifica: "A caregiver-facing banner in the live-test panel must state:
'Calibrating with gesture detection (preview mode — accuracy will improve with ONNX
classifier)' when `edgeLiveBridge` is active and `agentVersion` does not include an
ONNX classifier." O campo `detectorType: "ear_bri" | "onnx"` deve vir no handshake
WS. O `GestureTestVM` não tem este campo; o facade não o lê; o componente não o renderiza.

#### Correções mandatórias

1. Adicionar `detectorType: 'ear_bri' | 'onnx' | null` ao tipo `GestureTestVM` em
   `@neuroassist/shared/contracts` (se GestureTestVM é definido lá) ou em
   `frontend/libs/gest/src/lib/` se for local.
2. Atualizar `GestureTestFacade` para ler o campo `detectorType` da mensagem de
   handshake WS e atualizar o signal `_state`.
3. Atualizar `EdgeDetectionHttpAdapter.openStream` para parsear o campo `detectorType`
   do primeiro frame do WS (handshake frame) e emiti-lo no stream.
4. No `GestureLiveTestComponent` (ou no `DegradedBannerComponent` extraído), renderizar
   um `<div class="gest-interim-banner">` quando `vm().detectorType === 'ear_bri'`.
5. O texto do banner deve ser exatamente (em pt-BR):
   "Calibrando com detecção de gesto (modo preview — precisão melhorará com classificador ONNX)."

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/gest.facade.ts` — atualizar `_state` para incluir `detectorType`
- `frontend/libs/gest/src/lib/api/edge-detection-http.adapter.ts` — parsear handshake frame
- `frontend/libs/gest/src/lib/feature-live-test/gesture-live-test.component.ts` — renderizar banner
- `@neuroassist/shared/contracts` ou local: tipo `GestureTestVM` — adicionar `detectorType`

#### Contrato de tipo

```typescript
// Extensão obrigatória de GestureTestVM
interface GestureTestVM {
  // ... campos existentes ...
  detectorType: 'ear_bri' | 'onnx' | null;  // null = handshake não recebido ainda
}

// Handshake frame esperado do WS (primeiro frame após conexão)
interface EdgeAgentHandshake {
  type: 'handshake';
  agentVersion: string;
  detectorType: 'ear_bri' | 'onnx';
}
```

#### Sequência de execução

1. Localizar onde `GestureTestVM` é definido (shared/contracts ou local)
2. Adicionar campo `detectorType` ao tipo
3. Atualizar `initialVm()` na facade: `detectorType: null`
4. Atualizar `EdgeDetectionHttpAdapter.openStream` para:
   - Parsear o primeiro frame do WS como `EdgeAgentHandshake`
   - Se `frame.type === 'handshake'`, emitir um evento especial ao facade com `detectorType`
   - Continuar emitindo `EdgeRecognitionSignal` para frames subsequentes
5. Atualizar facade para capturar o `detectorType` e atualizar `_state`
6. Atualizar `GestureLiveTestComponent` template: adicionar `@if (vm().detectorType === 'ear_bri')` com div do banner
7. Adicionar unit test em `gest.facade.spec.ts` e `edge-detection-http.adapter.spec.ts` para o handshake frame

#### Política de testes

Adicionar casos ao spec da facade (P2) cobrindo: handshake com `detectorType:"ear_bri"` → `vm().detectorType === 'ear_bri'`; handshake com `"onnx"` → `null` banner; sem handshake → `detectorType === null` → sem banner.

#### Multi-empresa

Não aplicável nesta fase.

#### Critérios de aceite

```bash
cd frontend
npx nx test gest          # exit 0, testes de handshake passando
npx nx build gest         # exit 0
# Inspeção visual: abrir /pac/comandos/novo → live-test panel exibe banner quando backend retorna detectorType:"ear_bri"
```

#### Riscos

- O `EdgeDetectionMockAdapter` não emite handshake hoje — quando `edgeLiveBridge:off`,
  o `detectorType` permanece `null` e o banner não aparece. Isso é correto
  (mock não representa o interim EAR/BRI). Documentar no código.
- Se `GestureTestVM` está em `@neuroassist/shared/contracts`, a modificação
  pode afetar outros módulos que usam o tipo. Verificar usos antes de alterar.

#### Instrução para implementador

ADR-006 é uma decisão aceita — não é opcional. O banner deve aparecer em produção
quando o edge agent responder com EAR/BRI. Não usar i18n separado — o texto hardcoded
em pt-BR é aceitável para este módulo no estágio atual.

---

### Prioridade 4 — UX Polish: Loading/Error States + Navigation Breadcrumb

**Status:** pendente
**Issues cobertas:** GEST-FE-018, GEST-FE-019, GEST-FE-020
**Categoria:** FUNCIONAL

#### Objetivo

Implementar os estados de loading, vazio, e erro no `GestureLiveTestComponent`, e o
breadcrumb de navegação para a rota thin host `/gest`.

#### Problema identificado

O componente não renderiza estados intermediários:
- Sem loading spinner enquanto `state === 'starting'`
- Sem mensagem de erro visual quando `state === 'error'`
- Sem estado vazio (`state === 'idle'` sem comando draft selecionado)
- Sem breadcrumb na rota `/gest` (GEST-FE-018)

#### Correções mandatórias

1. Em `GestureLiveTestComponent`: adicionar `@if (vm().state === 'starting')` com
   `<na-spinner>` (ou classe equivalente de `@neuroassist/shared/ui-feedback`).
2. Adicionar `@if (vm().state === 'error')` com `<na-error-display>` ou div de erro
   contendo `vm().message` + botão "Tentar novamente" que dispara `facade.retry()`.
3. Adicionar `@if (!vm().commandDraftRef && vm().state === 'idle')` com mensagem
   "Selecione um comando para iniciar o teste de gesto."
4. Em `gest.routes.ts`: adicionar `data: { breadcrumb: 'Teste de Gesto' }` à rota `/gest`.
5. Se `GestDevHostComponent` usa o breadcrumb: adicionar `data: { breadcrumb: 'Dev Host' }`.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/feature-live-test/gesture-live-test.component.ts`
- `frontend/libs/gest/src/lib/feature-live-test/gesture-live-test.component.html`
  (se template separado; se inline no .ts, editar lá)
- `frontend/libs/gest/src/lib/gest.routes.ts`

#### Contrato de tipo

Nenhum tipo novo. Usar `GestureTestVM.state` já existente na facade.
Usar componentes de `@neuroassist/shared/ui-feedback` para spinner e error display.
Se esses componentes não existirem ainda (Wave-0), usar divs com classes CSS inline.

#### Sequência de execução

1. Ler `gesture-live-test.component.ts` completo para entender template atual
2. Identificar os `@if` já existentes para `vm().state`
3. Adicionar os blocos de loading, error, e vazio sem remover lógica existente
4. Atualizar `gest.routes.ts` com `data.breadcrumb`
5. Rodar `npx nx build gest` para confirmar que não há erros de compilação
6. Rodar `npx nx test gest` — checar que nenhum teste existente quebrou

#### Política de testes

Unit para o componente: adicionar casos em `gesture-live-test.component.spec.ts` (criar
se não existir) cobrindo: `state:'starting'` → spinner visível; `state:'error'` → erro
visível; `commandDraftRef:''` → mensagem vazio visível.

#### Multi-empresa

Não aplicável nesta fase.

#### Critérios de aceite

```bash
cd frontend
npx nx build gest         # exit 0
npx nx test gest          # exit 0
# Inspeção: npx nx serve neuroassist-ai-web → /gest renderiza breadcrumb "Teste de Gesto"
# Inspeção: ao iniciar stream, spinner aparece antes do primeiro frame
# Inspeção: ao simular erro, mensagem de erro + botão "Tentar novamente" visíveis
```

#### Riscos

- `@neuroassist/shared/ui-feedback` pode não ter spinner/error components prontos (stub Wave-0).
  Solução: usar `<span class="gest-loading">Carregando...</span>` como placeholder.

#### Instrução para implementador

Não adicionar estado ao componente — todo estado vem da facade via `vm()` signal.
O componente deve ser puramente reativo: `@if (vm().state === 'starting') { ... }`.
O botão "Tentar novamente" chama `facade.retry()` — verificar se este método existe
na facade antes de usá-lo; se não existir, criar o stub vazio e documentar como GEST-FE-020.

---

### Prioridade 5 — Multi-empresa Preparatory + Nx Boundary Rules + Observability

**Status:** pendente
**Issues cobertas:** GEST-INT-002, GEST-FE-021
**Categoria:** WIRING

#### Objetivo

(A) Preparar `EligibilityHttpAdapter` para injeção de `X-Empresa-Id` assim que
`@neuroassist/shared/access-http-base` for elevado de stub Wave-0.
(B) Adicionar regras ESLint `depConstraints` para enforçar que `scope:gest` não é
importado diretamente por libs `scope:pac` (apenas via host `scope:web`).
(C) Adicionar telemetria mínima via `pipeline_events.py` pattern no facade: emitir
eventos start-test, stop-test, verdict, degraded-activated.

#### Problema identificado

**A — Multi-empresa:** `EligibilityHttpAdapter` usa `HttpClient` diretamente sem
o header `X-Empresa-Id`. O `@neuroassist/shared/empresa-context` existe mas é stub.
O ponto de injeção precisa ser preparado agora para que o wiring seja trivial depois.

**B — Boundary rules:** O `.eslintrc.json` do workspace não tem regra
`depConstraints` proibindo `scope:pac` → `scope:gest`. Sem esta regra, imports
acidentais passam sem detecção.

**C — Observabilidade:** `GestureTestFacade` não emite eventos de telemetria.
GEST-FE-021 especifica 4 eventos: start-test, stop-test, verdict, degraded-activated.

#### Correções mandatórias

**A — Preparatório multi-empresa:**
1. Em `EligibilityHttpAdapter`: injetar `EmpresaContext` de `@neuroassist/shared/empresa-context`.
2. Adicionar o header `X-Empresa-Id` como comentário estruturado (`// TODO[MULTI-EMPRESA]`)
   indicando exatamente onde injetar quando o stub for elevado:
   ```typescript
   // TODO[MULTI-EMPRESA]: quando @neuroassist/shared/access-http-base implementar
   // o interceptor HttpInterceptorFn, remover esta injeção manual e confiar no interceptor.
   // Por ora, empresaContext.id é injetado mas não enviado (stub retorna null).
   private readonly empresaContext = inject(EmpresaContext);
   ```
3. Não bloquear a chamada HTTP se `empresaContext.id === null` — continuar sem o header.

**B — Nx boundary rules:**
1. Localizar `.eslintrc.json` ou `eslint.config.js` do workspace frontend.
2. Adicionar à seção `depConstraints` (plugin `@nx/enforce-module-boundaries`):
   ```json
   {
     "sourceTag": "scope:pac",
     "onlyDependOnLibsWithTags": ["scope:pac", "scope:shared", "scope:web"],
     "notDependOnLibsWithTags": ["scope:gest"]
   }
   ```
   (O host page `scope:web` pode importar `scope:gest`; libs internas de `scope:pac` não.)
3. Rodar `npx nx lint pac` para confirmar que as imports existentes (host page) não são flagadas.

**C — Observabilidade:**
1. Em `GestureTestFacade`: criar método privado `emitTelemetry(event: GestTelemetryEvent)`.
2. Tipo local (não precisa de shared):
   ```typescript
   type GestTelemetryEvent =
     | { type: 'start-test'; comandoDraftRef: string }
     | { type: 'stop-test'; duration_ms: number }
     | { type: 'verdict'; outcome: GestureTestOutcome; detectorType: 'ear_bri' | 'onnx' | null }
     | { type: 'degraded-activated'; reason: DegradedReason };
   ```
3. Chamar `emitTelemetry` nos pontos de transição da state machine:
   - `startTest()` → emit `start-test`
   - `stopTest()` → emit `stop-test` com duração
   - Transição para `state:'recognized'` ou `state:'not-recognized'` → emit `verdict`
   - Transição para `degraded:true` → emit `degraded-activated`
4. Implementação inicial: `console.info('[GEST-TELEMETRY]', event)` — integração com
   backend de telemetria real é GEST-FE-021 deferred; este stub garante o contrato.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/api/eligibility-http.adapter.ts` (injetar EmpresaContext)
- `frontend/.eslintrc.json` ou `frontend/eslint.config.js` (boundary rules)
- `frontend/libs/gest/src/lib/gest.facade.ts` (telemetria)

#### Contrato de tipo

```typescript
// Local em gest.facade.ts — não exportar
type GestTelemetryEvent =
  | { type: 'start-test'; comandoDraftRef: string }
  | { type: 'stop-test'; duration_ms: number }
  | { type: 'verdict'; outcome: GestureTestOutcome; detectorType: 'ear_bri' | 'onnx' | null }
  | { type: 'degraded-activated'; reason: DegradedReason };
```

#### Sequência de execução

1. Ler `frontend/.eslintrc.json` para localizar `depConstraints` existentes
2. Adicionar regra de boundary para `scope:gest`
3. Rodar `npx nx lint gest` + `npx nx lint pac` para confirmar
4. Editar `eligibility-http.adapter.ts` para injetar `EmpresaContext`
5. Editar `gest.facade.ts` para adicionar `emitTelemetry`
6. Rodar `npx nx test gest` para confirmar que testes de P2 ainda passam
7. Rodar `npx nx build gest` para confirmar compilação

#### Política de testes

Adicionar 1 unit test para `emitTelemetry`: `startTest()` deve chamar o evento
`start-test`. Usar `jest.spyOn(console, 'info')` para verificar.

#### Multi-empresa

Sim — esta fase implementa o ponto de injeção preparatório do `EmpresaContext`.
Não adiciona `X-Empresa-Id` ao header HTTP ainda (stub retorna `null`).

#### Critérios de aceite

```bash
cd frontend
npx nx lint gest          # exit 0
npx nx lint pac           # exit 0 (nenhuma violação nova de boundary)
npx nx test gest          # exit 0
npx nx build gest         # exit 0
# Inspeção: console do browser exibe [GEST-TELEMETRY] ao iniciar/parar teste
```

#### Riscos

- A regra `notDependOnLibsWithTags` pode não existir no schema da versão Nx atual.
  Alternativa: usar `bannedExternalImports` ou formular como `onlyDependOnLibsWithTags`
  com lista explícita. Verificar schema antes de aplicar.

#### Instrução para implementador

A injeção de `EmpresaContext` em `EligibilityHttpAdapter` não deve bloquear a chamada
HTTP. O objetivo é preparar o código para o upgrade futuro, não implementar o header
agora. Adicionar o `TODO[MULTI-EMPRESA]` exatamente onde o header seria adicionado
para que o próximo implementador saiba exatamente o que fazer.

---

### Prioridade 6 — E2E Tests (Playwright)

**Status:** pendente
**Issues cobertas:** GEST-FE-022, GEST-FE-023, GEST-FE-024, GEST-FE-025, GEST-FE-026
**Categoria:** VALIDACAO

#### Objetivo

Criar os 5 spec files Playwright para o módulo GEST, com foco no gate obrigatório
GEST-FE-026 (stream-no-cache-between-runs: nenhum frame de stream deve vazar entre
instâncias de `GestureLiveTestComponent`).

#### Problema identificado

Zero arquivos Playwright em `docs/architecture/frontend/modules/gest/frontend/e2e/`
(ou equivalente). O gate GEST-FE-026 não está verificado — risk de memory leak e de
frames "antigos" visíveis em nova sessão de teste.

#### Correções mandatórias

1. Localizar ou criar projeto `gest-e2e` no workspace Nx.
2. Criar arquivos spec Playwright:

   - `gest-simple-recognized.spec.ts` — happy path: gesto simples reconhecido
   - `gest-composite.spec.ts` — gesto composto (2 steps)
   - `gest-degraded-offline.spec.ts` — device offline → banner degraded + sem crash
   - `gest-camera-error.spec.ts` — câmera não autorizada → erro UX correto
   - `gest-stream-no-cache.spec.ts` — **gate obrigatório GEST-FE-026**: abrir live-test,
     parar, reabrir → nenhum frame do stream anterior deve ser visível; WS fechado e
     reconectado com subscription limpa

3. Usar mock do edge agent WS para os testes (não depender do edge-agent real):
   - Criar `playwright/fixtures/edge-agent-mock.ts` que simula o WS handshake + frames

#### Arquivos-alvo

- `frontend/e2e/gest/gest-simple-recognized.spec.ts` (criar)
- `frontend/e2e/gest/gest-composite.spec.ts` (criar)
- `frontend/e2e/gest/gest-degraded-offline.spec.ts` (criar)
- `frontend/e2e/gest/gest-camera-error.spec.ts` (criar)
- `frontend/e2e/gest/gest-stream-no-cache.spec.ts` (criar — GATE OBRIGATÓRIO)
- `frontend/e2e/fixtures/edge-agent-mock.ts` (criar)

#### Contrato de tipo

Os testes E2E usam `Page` do Playwright. Sem tipos adicionais do módulo GEST
(tests são caixa-preta). O mock WS usa a API `page.route()` do Playwright para
interceptar `ws://localhost:*`.

#### Sequência de execução

1. Verificar que o projeto `gest-e2e` existe no workspace ou criar via Nx generator
2. Criar fixture `edge-agent-mock.ts`
3. Criar `gest-stream-no-cache.spec.ts` primeiro (gate obrigatório)
4. Criar os outros 4 specs
5. Rodar `npx nx e2e gest-e2e` — esperado: todos passando com mock WS

#### Generators Nx obrigatórios (se gest-e2e não existe)

```bash
cd frontend
npx nx g @nx/playwright:configuration --project=gest-e2e
```

#### Política de testes

E2E obrigatório para esta prioridade. Unit tests não são criados aqui (P2 já cobriu).

#### Multi-empresa

Não aplicável nesta fase.

#### Critérios de aceite

```bash
cd frontend
npx nx e2e gest-e2e       # exit 0, 5 spec files passando
# Resultado específico GEST-FE-026:
# "stream-no-cache-between-runs" spec: PASS
```

#### Riscos

- Playwright com WS mock pode ser complexo; verificar se o workspace já tem
  configuração de mock WS em outros módulos antes de criar do zero.
- Se `gest-e2e` não existe, o generator pode criar estrutura incompatível com a
  convenção do workspace. Verificar `neuroassist-ai-web-e2e` como referência.

#### Instrução para implementador

Focar no gate GEST-FE-026 primeiro — este é o único spec que bloqueia a validação
de produção. Os outros 4 specs são complementares. Se o prazo for curto, entregar
GEST-FE-026 + gest-degraded-offline.spec.ts (risco mais alto de segurança) e marcar
os demais como pendentes na issue.

---

## 4. Dependências externas

| Dependência | Tipo | Status | Impacto |
|-------------|------|--------|---------|
| `edge-agent-ws.py` backend | WS | Implementado, smoke-tested | P3 (banner) depende do campo `detectorType` no handshake |
| `gest-service` (eligibility endpoint) | REST | Implementado | P2 (tests), P5 (multi-empresa) |
| `@neuroassist/shared/contracts` (GestureTestVM, types) | Tipo | Disponível | P3 requer extensão de GestureTestVM |
| `@neuroassist/shared/access-http-base` | Interceptor | Wave-0 stub | P5 preparatório; wiring completo bloqueado |
| `@neuroassist/shared/empresa-context` | Signal | Wave-0 stub | P5 preparatório |
| GEST-INT-004 (bridge token) | Auth WS | EXCLUÍDO deste plano | Cross-equipe GEST+EDGE; TODO em gest.providers.ts |
| GEST-INT-006 (onboarding slot) | Composição | Pendente | Fora deste ciclo; depende de módulo onboarding |

---

## 5. Restrições arquiteturais

| Regra | Descrição | Enforcement |
|-------|-----------|-------------|
| RULE-NX-01 | Tags `scope:gest` + `type:feature` obrigatórios | project.json (já OK) |
| RULE-NX-02 | Targets não vazios | Correção em P1 |
| RULE-NX-03 | `scope:pac` libs internas NÃO importam `scope:gest` | ESLint depConstraints — P5 |
| RULE-NX-04 | Standalone components, OnPush | Já cumprido |
| RULE-NX-05 | Signals readonly expostos | Já cumprido |
| RULE-ARCH-01 | `HttpClient` somente em adapters (data-access layer) | Já cumprido |
| RULE-ARCH-02 | Sem @Transactional / Spring annotations (FE não aplica) | N/A |
| ADR-005 | Honest-degraded invariant: nunca emite "recognized" fake | Cumprido; testes em P2 verificam |
| ADR-006 | Banner EAR/BRI interim quando `detectorType:"ear_bri"` | Implementar em P3 |

**Sublib split:** EXPLICITAMENTE ADIADO. A estrutura monolítica `libs/gest/` é válida
neste ciclo. Revisão após P6 (E2E verde).

**GEST-INT-004 (bridge token):** EXPLICITAMENTE EXCLUÍDO. Decisão cross-equipe GEST+EDGE;
não planejar nem implementar neste ciclo.

---

## 6. Referências

- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/generator-config.yml`
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-006-ear-bri-interim-detector.md` (**novo — leitura obrigatória antes de P3**)
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-005-live-test-via-edge-agent-stream.md`
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md`
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-08-e2e-scenarios.md`
- `docs/architecture/frontend/modules/gest/frontend/module-state.md`
- `docs/architecture/frontend/standards/nx-boundaries.md`
- `docs/architecture/shared/multi-empresa.md`
