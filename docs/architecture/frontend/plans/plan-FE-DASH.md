# Plano FE — DASH (neuroassist-ai-web)

> Gerado por: `/fe-planner` (Pipeline B, Etapa B3) | 2026-08-19
> Score baseline: **47/100 — Parcial** | Issues formais criadas pelo baseline: DASH-FE-001, DASH-FE-002, INT-FE-DASH-001
> Tipo de planejamento: **INCREMENTAL ESPECIFICO** — scaffolding parcial (~40%); gaps claros e isolados; arch backlog completo (36 issues FE-DASH-001..030 + INT-FE-DASH-001..006)
> Contexto: ES indisponivel; fonte: module-state.md (2026-08-19 BASELINE) + leitura direta do codigo

---

## Status das prioridades

- **P1** — Lint fix: selector prefix + warnings do mock adapter — pendente (DASH-FE-001)
- **P2** — Unit tests scaffold + coverage baseline — pendente (DASH-FE-003)
- **P3** — DashStreamClient: SSE + long-poll fallback — pendente (DASH-FE-002)
- **P4** — Injection tokens: EVNT activeEvent + EDGE deviceBadge — pendente (INT-FE-DASH-001)
- **P5** — Shell integration + bootstrap lifecycle — pendente (FE-DASH-018/019/020)
- **P6** — E2E: flows principais — pendente (FE-DASH-024/025/026/028/030)
- **P7** — HTTP integration (DEFERRED — bloqueado por dash-service backend)

---

## 0. Leitura executiva do estado atual

### Papel do modulo FE

DASH e o painel padrao de cuidado: a superficia que o cuidador abre e em menos de 2 segundos sabe que o paciente esta bem. Responsabilidades:
- Home status page (`/`) — StatusCard, overview do paciente, dispositivo online
- Notification center (`/notificacoes`) — lista e acknowledge de notificacoes
- Entrada page (`/entrada`) — chromeless brand entry (sem autenticacao requerida)

O modulo opera como landing padrao (`/`), declara a regiao `dash.global-interrupt` para overlays de emergencia (EVNT) e e o host do centro de notificacoes. A honestidade do signal `calm` — derivado de `EVNT.activeEvent`, nunca do aggregate — e o invariante de seguranca mais critico do produto.

### Estado atual observado

O scaffolding existente cobre: `dash.descriptor.ts`, `dash.routes.ts` (3 rotas), `dash.providers.ts` (factory `provideDash()`), `dash.types.ts` (todos os tipos de dominio), `DashHomeFacade` (signals-based com load/retry/computed), `NotificationsFacade` (signals-based com load/acknowledge/retry), portas e adapters (mock + HTTP stub), `authGuard` + `dashGuard` + `roleGuard` (Wave-0 bypass), `EntradaComponent`, `HomeStatusComponent`, `NotificationCenterComponent`. O codigo funcional existe mas nao foi validado por testes. `DashStreamClient` (SSE) nao foi scaffolded.

Gates BASELINE (2026-08-19): lint=FAIL (3 erros de selector + 2 warnings), test=PASS (0 testes — `passWithNoTests: true`), build=PASS.

### Continuidade da ultima rodada

Nao ha historico de implementacao anterior neste modulo (plano inaugural do ciclo de Implement). Ponto de entrada: BASELINE 2026-08-19, score 47/100.

### Diagnostico executivo

| Dimensao | Status | Gap principal |
|---|---|---|
| Estrutural (projeto) | OK | Nx project.json com tags `scope:dash, type:feature` corretas; boundaries nao violadas |
| Lint | FAIL (BLOQUEADOR) | 3 erros de selector: `dash-*` deve ser `lib-*`; 2 warnings no mock adapter |
| Funcional (facades + components) | PARCIAL | DashHomeFacade + NotificationsFacade scaffolded; comportamentos nao testados; SSE client ausente |
| Unit tests | CRITICO | 0 arquivos `.spec.ts`; `passWithNoTests: true` mascara ausencia |
| SSE (real-time) | CRITICO | `DashStreamClient` nao criado; P2 FE-DASH-008 pendente |
| Guards | PARCIAL | authGuard + dashGuard + roleGuard criados com Wave-0 bypass; nao testados |
| Cross-module tokens | MISSING | `DASH_ACTIVE_EVENT_TOKEN` e `DASH_DEVICE_BADGE_TOKEN` nao criados |
| Shell integration | MISSING | `DashShellIntegrationService` e `dash.bootstrap.ts` nao iniciados |
| E2E | MISSING | Specs Playwright nao implementados; app shell tem rota DASH wired |
| HTTP adapters | STUB | `DashSummaryHttpAdapter` + `DashNotificationsHttpAdapter` sao stubs compilaveis; dash-service nao deployado |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

PARCIAL. O `module-state.md` confirma que o padrao `@neuroassist/shared/access-http-base` + `X-Empresa-Id` esta documentado no plano arquitetural e `provideDash()` contempla o toggle `adapter: 'http'`. Os adapters HTTP (`DashSummaryHttpAdapter`, `DashNotificationsHttpAdapter`) sao stubs compilaveis que aguardam dash-service (porta 8090). O interceptor `X-Empresa-Id` sera implementado em P7 (deferred) junto com os adapters HTTP reais. Os adapters mock nao precisam do interceptor. Em P3 (DashStreamClient), a auth SSE nao usa headers customizados (limitacao da API `EventSource`) — coordenar com BE sobre cookie ou query-param JWT antes de implementar.

---

## 1. Principais achados

### Pontos fortes

- `DashHomeFacade`: signals-based correto; `.asReadonly()` em todos os sinais publicos; `load()` + `retry()` implementados; computeds (`loading`, `patient`, `device`, `latestEvent`, `unacknowledgedCount`, `hasDegradedSections`) derivados de forma limpa
- `NotificationsFacade`: signals-based correto; `acknowledge()` nao-otimistico (evicta da lista somente apos confirmacao do port); `retry()` correto
- `HomeStatusComponent` e `NotificationCenterComponent`: standalone, OnPush, templates com todos os estados (loading, error, empty, loaded); injecao de facades via `inject()` sem `@Input()`
- Portas e adapters bem estruturados: `DASH_SUMMARY_PORT` + `DASH_NOTIFICATIONS_PORT` com InjectionToken; mock adapters com dados realistas (Marcio Amaral, seed data); HTTP stubs compilaveis
- Guards (Wave-0): `authGuard`, `dashGuard`, `roleGuard` implementados com bypass seguro; padrao alinhado com ACS e HIST
- `dash.providers.ts`: `provideDash({ adapter: 'mock' | 'http' })` factory padrao; toggle limpo; `TODO[INT-FE-DASH-004..005]` documentados

### Gaps

1. **Lint (BLOQUEADOR):** `selector: 'dash-entrada'`, `'dash-home-status'`, `'dash-notification-center'` violam `@angular-eslint/component-selector` (`prefix: "lib"` em `.eslintrc.json`). Correcao: `lib-entrada`, `lib-home-status`, `lib-notification-center`.
2. **Lint (warnings):** `DashNotificationsMockAdapter.poll()` declara `_waitSeconds` e `_cursor` com underscore mas ESLint pode flagiar dependendo da config. Verificar e corrigir se necessario.
3. **Unit tests ausentes:** 0 arquivos `.spec.ts`; cobertura 0%; `passWithNoTests: true` oculta ausencia.
4. **SSE client ausente:** `DashStreamClient` (FE-DASH-008) nao scaffolded; feature-flag `dash.sseStream` declarada no descriptor mas sem implementacao.
5. **Injection tokens cross-module ausentes:** `DASH_ACTIVE_EVENT_TOKEN` e `DASH_DEVICE_BADGE_TOKEN` nao existem em `access/dash-access-tokens.ts`; `DashHomeFacade` tem `TODO[INT-FE-DASH-001]` e `TODO[INT-FE-DASH-002]` mas nao injeta os tokens.
6. **Shell integration ausente:** `integration/` vazia; `DashShellIntegrationService` e `dash.bootstrap.ts` nao criados.
7. **E2E ausente:** Specs Playwright nao implementados; arch backlog FE-DASH-024..030 nao iniciados.

### Riscos

- **R1 — calm honesty:** `DashHomeFacade` ainda nao tem `calm` computed derivado de `activeEvent` (EVNT). A honestidade do StatusCard depende disso. Mitigacao em P4: criar token com `signal(null)` como default seguro.
- **R2 — SSE auth:** `EventSource` nao aceita headers customizados (JWT via header). Coordenar com BE sobre mecanismo (cookie ou query-param) antes de implementar P3. Sem decisao, implementar long-poll apenas como fallback padrao.
- **R3 — dash-service nao deployado:** Todos os adapters HTTP sao stubs. HTTP integration (P7) e totalmente bloqueada por backend.
- **R4 — cross-module bloqueio externo:** `DASH_ACTIVE_EVENT_TOKEN` pode ser wired em safe default (P4) mas o valor real depende de EVNT FE scaffolding (externo a este ciclo).

### Aderencia as regras Nx (boundaries, tax-map, naming)

- Tags `scope:dash, type:feature` corretas em `project.json`. Boundaries: `libs/dash` nao importa de outros `scope:*` (apenas `scope:shared`); cross-module via InjectionToken em `scope:web`. Nenhuma violacao de boundary detectada no codigo existente.
- A partir de P1: selectors corrigidos para `lib-entrada`, `lib-home-status`, `lib-notification-center`.

---

## 2. Estrategia geral

### Objetivo macro

Fechar o gate de lint (bloqueador), criar baseline de testes unitarios, implementar o client SSE real-time, criar os tokens de injecao cross-module com safe defaults, e iniciar a integracao com o shell. O modulo deve estar funcional com mocks e PR-ready ao final de P5. E2E cobre os flows principais em P6. HTTP integration aguarda dash-service backend (P7 deferred).

### Ordem das prioridades

| P | Titulo | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Lint fix: selector prefix + warnings | Bloqueador: 3 erros impedem PR; correcoes cirurgicas | DASH-FE-001 |
| P2 | Unit tests scaffold | 0 testes e gap critico de qualidade; testa codigo ja implementado | DASH-FE-003 |
| P3 | DashStreamClient: SSE + long-poll fallback | Feature critica para notificacoes real-time; P2 FE-DASH-008 do arch backlog | DASH-FE-002 |
| P4 | Injection tokens: EVNT activeEvent + EDGE deviceBadge | Bloqueador de calm honesty (INT-FE-DASH-001); safe defaults ate EVNT/EDGE scaffolded | INT-FE-DASH-001 |
| P5 | Shell integration + bootstrap lifecycle | DashShellIntegrationService + dash.bootstrap.ts (FE-DASH-018/019/020) | FE-DASH-018, FE-DASH-019, FE-DASH-020 |
| P6 | E2E: flows principais | Valida fluxos de usuario; arch backlog FE-DASH-024..030 (subconjunto principal) | FE-DASH-024, FE-DASH-025, FE-DASH-026, FE-DASH-028, FE-DASH-030 |
| P7 | HTTP integration (DEFERRED) | Bloqueado por dash-service nao deployado; nao executa neste ciclo | FE-DASH-005, INT-FE-DASH-004, INT-FE-DASH-005 |

---

## 3. Plano por prioridade

### Prioridade 1 — Lint fix: selector prefix + warnings

Status: pendente
Issues cobertas: DASH-FE-001
Categoria: ESTRUTURAL

#### Objetivo

Fazer `npx nx lint dash` sair com 0 errors e 0 warnings.

#### Problemas identificados

1. `entrada.component.ts`: `selector: 'dash-entrada'` viola `@angular-eslint/component-selector` (prefix `lib`). Correcao: `selector: 'lib-entrada'`.
2. `home-status.component.ts`: `selector: 'dash-home-status'` → `selector: 'lib-home-status'`.
3. `notification-center.component.ts`: `selector: 'dash-notification-center'` → `selector: 'lib-notification-center'`.
4. `dash-notifications-mock.adapter.ts`: parametros `_waitSeconds` e `_cursor` no metodo `poll()` podem acionar `@typescript-eslint/no-unused-vars`. Verificar saida de lint e corrigir com prefixo underscore ou supressao inline se necessario.

#### Correcoes mandatorias

- `frontend/libs/dash/src/lib/feature-entrada/entrada.component.ts`:
  - Mudar `selector: 'dash-entrada'` → `selector: 'lib-entrada'`
- `frontend/libs/dash/src/lib/feature-home/home-status.component.ts`:
  - Mudar `selector: 'dash-home-status'` → `selector: 'lib-home-status'`
- `frontend/libs/dash/src/lib/feature-notifications/notification-center.component.ts`:
  - Mudar `selector: 'dash-notification-center'` → `selector: 'lib-notification-center'`
- `frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.ts`:
  - Verificar warnings para `_waitSeconds`, `_cursor`. Se o ESLint flagiar, renomear para `_waitSeconds` e `_cursor` (ja com underscore) ou adicionar comentario `// eslint-disable-next-line @typescript-eslint/no-unused-vars` apenas se necessario.

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/feature-entrada/entrada.component.ts
frontend/libs/dash/src/lib/feature-home/home-status.component.ts
frontend/libs/dash/src/lib/feature-notifications/notification-center.component.ts
frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.ts
```

#### Contrato de tipo

Sem alteracao de contratos — apenas renomeacao de selector (string) e possivel ajuste de warnings. Componentes nao mudam comportamento.

#### Sequencia de execucao

1. Editar `entrada.component.ts`: mudar valor de `selector`
2. Editar `home-status.component.ts`: mudar valor de `selector`
3. Editar `notification-center.component.ts`: mudar valor de `selector`
4. Rodar `cd frontend && npx nx lint dash` — verificar se ha warnings restantes no mock adapter
5. Se warnings: corrigir `dash-notifications-mock.adapter.ts`
6. Confirmar: `cd frontend && npx nx lint dash` → 0 errors, 0 warnings

#### Generators Nx obrigatorios

Nenhum.

#### Politica de testes

Nenhum novo teste nesta prioridade. Lint verifica estaticamente.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. P1 e puramente lint — sem alteracoes de runtime.

#### Criterios de aceite

```bash
cd frontend && npx nx lint dash
# Exit code: 0
# Output: nenhum erro, nenhum warning
```

#### Riscos

Baixo. Alteracoes cirurgicas de string de selector. Nenhum comportamento muda. Componentes sao standalone e os selectors so sao usados em templates de outros componentes (ou em testes); verificar se algum template pai referencia `dash-home-status` etc. — se sim, atualizar simultaneamente.

#### Instrucao para implementador

Abrir os tres arquivos de componente, alterar o valor do campo `selector` em cada um. Rodar `npx nx lint dash` para confirmar 0 errors. Se houver warnings no mock adapter, corrigir tambem. Nao alterar logica, template, ou contratos.

---

### Prioridade 2 — Unit tests scaffold + coverage baseline

Status: pendente
Issues cobertas: DASH-FE-003
Categoria: VALIDACAO

#### Objetivo

Criar arquivos `.spec.ts` cobrindo os contratos comportamentais das facades, guards e mock adapters do modulo DASH. Meta: >= 20 testes passando.

#### Problema

0 arquivos `.spec.ts` existem. `passWithNoTests: true` no `jest.config.js` silencia a ausencia. O modulo nao tem nenhuma evidencia verificavel de corretude.

#### Resultado esperado

`cd frontend && npx nx test dash` retorna exit 0 com >= 20 testes PASS.

#### Correcoes mandatorias

Criar os seguintes arquivos (nenhum existe):

1. `frontend/libs/dash/src/lib/dash.facade.spec.ts` — testa DashHomeFacade
2. `frontend/libs/dash/src/lib/notifications.facade.spec.ts` — testa NotificationsFacade
3. `frontend/libs/dash/src/lib/access/dash.guard.spec.ts` — testa guards (Wave-0 behavior)
4. `frontend/libs/dash/src/lib/api/dash-summary-mock.adapter.spec.ts` — contrato do mock summary
5. `frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.spec.ts` — contrato do mock notifications

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/dash.facade.spec.ts
frontend/libs/dash/src/lib/notifications.facade.spec.ts
frontend/libs/dash/src/lib/access/dash.guard.spec.ts
frontend/libs/dash/src/lib/api/dash-summary-mock.adapter.spec.ts
frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.spec.ts
```

#### Contrato de tipo dos testes

**dash.facade.spec.ts — cenarios obrigatorios:**
- `load()` → status = 'loading' depois = 'loaded'; `patient()` nao-null; `caregiverName()` nao-vazio
- `load()` com port retornando `patients: []` → status = 'empty'
- `load()` com port lancando erro → status = 'error'; `error()` com `code` e `message`
- `retry()` → re-executa load (equivalente a chamar load novamente)
- `loading` computed = true durante 'loading', false nos demais estados
- `hasDegradedSections()` = true quando `degradedSections.length > 0`

**notifications.facade.spec.ts — cenarios obrigatorios:**
- `load()` → status = 'loading' depois = 'loaded'; `notifications()` nao-vazia; `count()` = numero de items
- `load()` com lista vazia → status = 'empty'
- `load()` com erro → status = 'error'; `error()` nao-null
- `acknowledge(id)` → remove notificacao da lista apos confirmacao (nao-otimistico)
- `acknowledge(id)` tornando lista vazia → status = 'empty'
- `acknowledge(id)` com erro → nao-fatal; notificacao permanece na lista; sem crash
- `retry()` → chama load()
- `count` = `notifications().length`

**dash.guard.spec.ts — cenarios obrigatorios:**
- `authGuard`: retorna `true` quando `DASH_AUTH_SESSION` = null (Wave-0 bypass)
- `authGuard`: retorna `UrlTree` para `/entrada` quando `session.token === null`
- `dashGuard`: retorna `true` quando `DASH_ACCESS_DECISION` = null (Wave-0 bypass)
- `roleGuard`: retorna `true` quando `DASH_ACCESS_DECISION` = null (Wave-0 bypass)

**dash-summary-mock.adapter.spec.ts — cenarios obrigatorios:**
- `getSummary()` retorna Observable com `DashSummaryResponse` (tenantId, caregiver, patients[0])

**dash-notifications-mock.adapter.spec.ts — cenarios obrigatorios:**
- `listUnacknowledged()` retorna Observable com `notifications` nao-vazio
- `acknowledge(id)` retorna Observable<void>; remove item internamente; segunda listagem nao inclui o item

#### Sequencia de execucao

1. Criar `dash.facade.spec.ts` com mocks do `DASH_SUMMARY_PORT` via TestBed providers (`useValue: { getSummary: jest.fn(() => of(mockSummary)) }`)
2. Criar `notifications.facade.spec.ts` com mocks do `DASH_NOTIFICATIONS_PORT` via TestBed providers
3. Criar `dash.guard.spec.ts` com mocks de `DASH_AUTH_SESSION`, `DASH_ACCESS_DECISION` e `Router`
4. Criar `dash-summary-mock.adapter.spec.ts` com TestBed basico
5. Criar `dash-notifications-mock.adapter.spec.ts` com TestBed basico
6. Rodar `cd frontend && npx nx test dash` — confirmar >= 20 PASS

#### Generators Nx obrigatorios

Nenhum. Criar arquivos `.spec.ts` diretamente.

#### Politica de testes

Unit obrigatorio. Isolamento com mocks de ports via TestBed. Nao usar `HttpClientTestingModule` (adapters HTTP sao stubs nao testados neste ciclo). Usar `of()` do RxJS para wrapping de valores nos mocks. Facades usam `lastValueFrom` + `takeUntilDestroyed` — garantir que mocks retornam `Observable<T>`.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. Testes unitarios usam mock adapters que nao fazem HTTP.

#### Criterios de aceite

```bash
cd frontend && npx nx test dash
# Exit code: 0
# Tests: >= 20 PASS, 0 FAIL
```

#### Riscos

- `DashHomeFacade` e `NotificationsFacade` usam `takeUntilDestroyed(this.destroyRef)` que requer `DestroyRef` injetado. Angular 18 injeta automaticamente em `TestBed` — sem necessidade de prover manualmente.
- Guards usam `inject(Router)` — prover `Router` via `RouterTestingModule` ou mock no TestBed.
- `DashNotificationsMockAdapter.acknowledge()` muta estado interno (`this.pending`) — testar apos acknowledge que segunda listagem nao inclui o item.

#### Instrucao para implementador

Criar os 5 arquivos spec listados. Usar `TestBed.configureTestingModule` com `providers` que substituem os tokens de port por valores mock. Para facades, usar `jest.fn()` retornando `of(mockData)`. Para guards, usar `useValue` para os tokens de auth. Rodar `npx nx test dash` ao final. Ver referencia em `frontend/libs/hist/src/lib/hist.facade.spec.ts` para padrao de spec de facade.

---

### Prioridade 3 — DashStreamClient: SSE + long-poll fallback

Status: pendente
Issues cobertas: DASH-FE-002 (→ arch FE-DASH-008)
Categoria: FUNCIONAL

#### Objetivo

Criar o `DashStreamClient` em `frontend/libs/dash/src/lib/api/` que gerencia conexao SSE com heartbeat, reconexao por backoff exponencial e fallback para long-poll via `DashNotificationsPort.poll()`.

#### Problema

`DashStreamClient` nao existe. A feature-flag `dash.sseStream` esta declarada no descriptor mas sem implementacao. O `NotificationsFacade` nao tem acesso a stream real-time — apenas polling one-shot via `load()`.

#### Resultado esperado

`DashStreamClient` criado; `NotificationsFacade` integra o stream via injecao; tests de reconexao e fallback passando; `npx nx test dash` PASS.

#### Contrato de tipo (interfaces, signals, facades — nomes exatos)

```typescript
// frontend/libs/dash/src/lib/api/dash-stream-client.ts

export type StreamStatus = 'disconnected' | 'connecting' | 'connected' | 'fallback';
export interface StreamError { code: string; message: string; }

@Injectable({ providedIn: 'root' })
export class DashStreamClient {
  // Writable internos
  readonly #status      = signal<StreamStatus>('disconnected');
  readonly #error       = signal<StreamError | null>(null);
  readonly #fallbackActive = signal<boolean>(false);

  // Publicos readonly
  readonly status       = this.#status.asReadonly();
  readonly isConnected  = computed(() => this.#status() === 'connected');
  readonly fallbackActive = this.#fallbackActive.asReadonly();
  readonly error        = this.#error.asReadonly();

  // Subject de eventos (stream interno; NotificationsFacade subscreve)
  // Interno — nao exposto diretamente como signal; facade converte
  readonly events$ = new Subject<NotificationEnvelope>();

  open(url: string): void;     // Abre SSE; inicia reconexao automatica
  close(): void;               // Fecha SSE e cancela reconexao; limpa fallback
}
```

**Decisao de design (planner escolhe):** `DashStreamClient` expoe `events$: Subject<NotificationEnvelope>` internamente e `NotificationsFacade` subscreve via `takeUntilDestroyed`. A facade acumula items no signal `#notifications`. O stream e aberto em `ngOnInit` do `NotificationCenterComponent` e fechado no `ngOnDestroy` do mesmo (via `DashStreamClient.close()` ou via `destroyRef`).

**Fallback:** se `EventSource` falhar (onerror apos 3 tentativas), `DashStreamClient` ativa modo long-poll chamando `DASH_NOTIFICATIONS_PORT.poll()` a cada `pollIntervalMs` (default 30000ms). `fallbackActive` signal = true.

**Auth SSE:** `EventSource` nao aceita headers. Decisao de Wave-0: abrir URL sem auth header (compativel com mock); em producao, coordenar com BE sobre mecanismo (cookie ou query-param JWT — documentar com `TODO[DASH-005-AUTH]`).

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/api/dash-stream-client.ts         (CRIAR)
frontend/libs/dash/src/lib/api/dash-stream-client.spec.ts    (CRIAR)
frontend/libs/dash/src/lib/notifications.facade.ts           (ATUALIZAR — injetar DashStreamClient)
```

#### Sequencia de execucao

1. Criar `dash-stream-client.ts` com `DashStreamClient` service
2. Implementar `open()`: cria `EventSource`; seta status = 'connecting'; em `onopen`: status = 'connected'; em `onmessage`: parseia JSON e emite em `events$`; em `onerror`: incrementa tentativas; se >= 3 → ativar fallback; senao → backoff exponencial e reconectar
3. Implementar backoff exponencial: `2s * 2^attempt` com max `30s`
4. Implementar fallback: chama `DASH_NOTIFICATIONS_PORT.poll(waitSeconds, cursor)` em loop; status = 'fallback'; `fallbackActive.set(true)`
5. Implementar `close()`: fecha `EventSource`; cancela fallback; `#status.set('disconnected')`
6. Atualizar `notifications.facade.ts`: injetar `DashStreamClient`; em `load()` apos catch-up HTTP, chamar `this.streamClient.open('/panel/stream')`; subscrever `streamClient.events$` via `takeUntilDestroyed` para acumular notifications
7. Criar `dash-stream-client.spec.ts`: cenarios de reconexao, fallback, close
8. Rodar `cd frontend && npx nx test dash`

#### Generators Nx obrigatorios

Nenhum. Criar arquivos diretamente em `api/`.

#### Politica de testes

Unit obrigatorio. Mock `EventSource` via jest.fn() (JSDOM nao tem EventSource real — usar `global.EventSource = jest.fn(...)`). Testar reconexao, fallback, close. Nao usar `HttpClientTestingModule`. Nao testar SSE real em unit test.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao para SSE (EventSource nao usa interceptors). Para long-poll fallback: o port `DASH_NOTIFICATIONS_PORT.poll()` ja usa `HttpClient` via adapter HTTP (em producao) — o interceptor `X-Empresa-Id` sera configurado em P7.

#### Criterios de aceite

```bash
cd frontend && npx nx test dash
# Exit code: 0
# Testes de stream (reconexao, fallback, close) PASS
cd frontend && npx nx build neuroassist-ai-web
# Exit code: 0
```

#### Riscos

- **JSDOM + EventSource:** JSDOM nao implementa EventSource — mock obrigatorio em jest. Usar `jest.spyOn(global, 'EventSource')` ou assign diretamente `global.EventSource = jest.fn(...)`.
- **Auth SSE:** decisao de producao (cookie vs query-param) nao definida com BE. Em Wave-0, URL sem auth e suficiente para mock. Documentar TODO.
- **Ciclo de vida:** `DashStreamClient` e `providedIn: 'root'` — so ha uma instancia. Se `NotificationCenterComponent` for destroy/recreate, o stream deve ser gerenciado corretamente. Alternativa: `close()` no `ngOnDestroy` do component.

#### Instrucao para implementador

Criar `DashStreamClient` como service `providedIn: 'root'` em `api/`. Implementar `open/close` com mock de `EventSource` em tests. Atualizar `NotificationsFacade` para injetar e chamar o client. Garantir que `close()` e chamado quando o component e destruido. Rodar `npx nx test dash`.

---

### Prioridade 4 — Injection tokens: EVNT activeEvent + EDGE deviceBadge

Status: pendente
Issues cobertas: INT-FE-DASH-001 (→ arch INT-FE-DASH-001 + INT-FE-DASH-002)
Categoria: WIRING

#### Objetivo

Definir `DASH_ACTIVE_EVENT_TOKEN` e `DASH_DEVICE_BADGE_TOKEN` em `access/dash-access-tokens.ts` com safe defaults `signal(null)`. Wire `DASH_ACTIVE_EVENT_TOKEN` no `DashHomeFacade` para computar `calm`. Wire providers em `app.config.ts`.

#### Problema

Os tokens nao existem em `access/dash-access-tokens.ts`. `DashHomeFacade` tem `TODO[INT-FE-DASH-001]` e `TODO[INT-FE-DASH-002]` mas nao injeta nenhum signal externo. O `calm` signal (invariante de seguranca) nao existe ainda — o HomeStatusComponent usa `estaBem` computado localmente a partir de `unacknowledgedCount`, o que nao e equivalente a honestidade de `activeEvent`.

#### Resultado esperado

Tokens criados; `DashHomeFacade.calm` computed correto (derivado de `activeEvent`); build e lint OK; testes de integracao de token passando.

#### Contrato de tipo (nomes exatos, campos, tipos)

```typescript
// Adicionar em frontend/libs/dash/src/lib/access/dash-access-tokens.ts

// Tipos proprios (DASH nao importa scope:evnt nem scope:edge)
export interface ActiveEventVm {
  readonly eventId: string;
  readonly urgencia: 'emergencia' | 'rotina';
  readonly patientId: string;
}

export interface DeviceBadgeVm {
  readonly status: 'online' | 'offline' | 'unknown';
  readonly lastSeenAt: string | null;
  readonly degraded: boolean;
}

export const DASH_ACTIVE_EVENT_TOKEN = new InjectionToken<Signal<ActiveEventVm | null>>(
  'DashActiveEventToken',
  { providedIn: 'root', factory: () => signal(null) },
);

export const DASH_DEVICE_BADGE_TOKEN = new InjectionToken<Signal<DeviceBadgeVm | null>>(
  'DashDeviceBadgeToken',
  { providedIn: 'root', factory: () => signal(null) },
);
```

**DashHomeFacade — adicionar:**
```typescript
// Em dash.facade.ts, junto aos outros injects:
private readonly activeEvent = inject(DASH_ACTIVE_EVENT_TOKEN);

// Computed calm — derivado do EVNT signal, nunca do aggregate local
readonly calm = computed<boolean>(() => this.activeEvent() === null);
```

**Instrucao:** `calm === true` quando nao ha `activeEvent` ativo (null = cuidado normal). `calm === false` quando ha alarme ativo em EVNT. O `HomeStatusComponent.estaBem` deve migrar para usar `facade.calm()` em vez de `unacknowledgedCount === 0` para garantir honestidade.

**app.config.ts — adicionar providers:**
```typescript
// Safe defaults ate EVNT/EDGE FE exportarem signals reais
{ provide: DASH_ACTIVE_EVENT_TOKEN, useValue: signal(null) },
{ provide: DASH_DEVICE_BADGE_TOKEN, useValue: signal(null) },
```

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/access/dash-access-tokens.ts          (ATUALIZAR — adicionar tipos + tokens)
frontend/libs/dash/src/lib/dash.facade.ts                        (ATUALIZAR — injetar DASH_ACTIVE_EVENT_TOKEN + computed calm)
frontend/libs/dash/src/lib/feature-home/home-status.component.ts (ATUALIZAR — usar facade.calm() em estaBem)
frontend/apps/neuroassist-ai-web/src/app/app.config.ts           (ATUALIZAR — adicionar providers dos tokens)
frontend/libs/dash/src/index.ts                                   (ATUALIZAR — exportar ActiveEventVm, DeviceBadgeVm, tokens)
```

#### Sequencia de execucao

1. Atualizar `dash-access-tokens.ts`: adicionar `ActiveEventVm`, `DeviceBadgeVm`, `DASH_ACTIVE_EVENT_TOKEN`, `DASH_DEVICE_BADGE_TOKEN`
2. Atualizar `dash.facade.ts`: injetar `DASH_ACTIVE_EVENT_TOKEN`; adicionar `readonly calm = computed(() => this.activeEvent() === null)`
3. Atualizar `home-status.component.ts`: substituir calculo local de `estaBem` por `facade.calm()`
4. Atualizar `index.ts`: exportar novos tipos e tokens
5. Atualizar `app.config.ts`: adicionar providers com `signal(null)` defaults
6. Atualizar testes em `dash.facade.spec.ts` (P2): adicionar cenarios para `calm`
7. Rodar `cd frontend && npx nx lint dash && npx nx test dash && npx nx build neuroassist-ai-web`

#### Generators Nx obrigatorios

Nenhum.

#### Politica de testes

Unit obrigatorio. Adicionar cenarios de `calm` nos testes existentes de `DashHomeFacade`:
- `calm()` = true quando `DASH_ACTIVE_EVENT_TOKEN` = `signal(null)`
- `calm()` = false quando `DASH_ACTIVE_EVENT_TOKEN` = `signal({ eventId, urgencia: 'emergencia', ... })`

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. Tokens sao DI puro — sem HTTP.

#### Criterios de aceite

```bash
cd frontend && npx nx lint dash  # 0 errors, 0 warnings
cd frontend && npx nx test dash   # calm tests PASS
cd frontend && npx nx build neuroassist-ai-web  # 0 erros
```

#### Riscos

- `HomeStatusComponent` usa `estaBem` como computed local — verificar se a migracao para `facade.calm()` nao quebra o template (o template referencia `estaBem()` — a migracao substitui o calculo, nao o nome do computed local).
- `app.config.ts` pode nao existir no path esperado — verificar path exato antes de editar.

#### Instrucao para implementador

Adicionar os dois tokens ao arquivo de tokens existente. Injetar `DASH_ACTIVE_EVENT_TOKEN` na facade e criar `calm` computed. Atualizar `estaBem` no component para usar `facade.calm()`. Exportar novos tipos do barrel. Prover defaults em `app.config.ts`. Rodar lint+test+build.

---

### Prioridade 5 — Shell integration + bootstrap lifecycle

Status: pendente
Issues cobertas: FE-DASH-018, FE-DASH-019, FE-DASH-020
Categoria: WIRING

#### Objetivo

Criar `DashShellIntegrationService` e `dash.bootstrap.ts` para registrar o modulo DASH no shell chrome, declarar a regiao `dash.global-interrupt`, fazer SSE abrir no bootstrap e fechar no destroy.

#### Problema

O diretorio `frontend/libs/dash/src/lib/integration/` existe mas esta vazio. Sem o service de integracao, o modulo nao declara a regiao de outlet para alarmes EVNT, nao registra navegacao no shell e nao tem ciclo init/destroy gerenciado.

#### Resultado esperado

`DashShellIntegrationService` criado; `dash.bootstrap.ts` exportado; `DashModule` (ou `provideDash()`) chama bootstrap na inicializacao; destroy fecha SSE e libera outlet.

#### Contrato de tipo (nomes exatos)

```typescript
// frontend/libs/dash/src/lib/integration/dash-shell-integration.service.ts

@Injectable({ providedIn: 'root' })
export class DashShellIntegrationService {
  registerWithShell(): void;         // Registra rotas e menu no shell descriptor
  bindActiveEvent(): void;           // Assinatura: quando DASH_ACTIVE_EVENT_TOKEN emitir valor, notificar observers
  bindDeviceBadge(): void;           // Idem para DASH_DEVICE_BADGE_TOKEN
  releaseOutletRegion(): void;       // Libera regiao 'dash.global-interrupt' no ShellOutletRegistry
}

// frontend/libs/dash/src/lib/dash.bootstrap.ts
export function dashInit(injector: Injector): void;    // SSE open + summary prime
export function dashDestroy(injector: Injector): void; // SSE close + region release
```

**Decisao de design (planner escolhe):** `DashShellIntegrationService` usa `inject()` para obter `DashStreamClient` e os tokens. `dashInit` e `dashDestroy` sao funcoes (nao classes) exportadas do barrel para uso em `APP_INITIALIZER` ou `provideAppInitializer` em `app.config.ts`. A regiao `dash.global-interrupt` e declarada com comentario `// TODO[INT-FE-DASH-003]: integrar ShellOutletRegistry quando estabilizado` — stub safe para este ciclo.

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/integration/dash-shell-integration.service.ts  (CRIAR)
frontend/libs/dash/src/lib/dash.bootstrap.ts                               (CRIAR)
frontend/libs/dash/src/lib/dash-nav-config.ts                              (VERIFICAR — ja existe; atualizar se necessario)
frontend/libs/dash/src/index.ts                                             (ATUALIZAR — exportar DashShellIntegrationService, dashInit, dashDestroy)
```

#### Sequencia de execucao

1. Criar `integration/dash-shell-integration.service.ts` com stub dos 4 metodos
2. Criar `dash.bootstrap.ts` com `dashInit()` e `dashDestroy()`
3. `dashInit`: chama `DashStreamClient.open('/panel/stream')`; chama `DashHomeFacade.load()`
4. `dashDestroy`: chama `DashStreamClient.close()`; chama `releaseOutletRegion()` (stub)
5. Criar breadcrumb navigation se necessario (dash-nav-config.ts ja existe; verificar se breadcrumb esta coberto)
6. Exportar do barrel `index.ts`
7. Rodar `cd frontend && npx nx lint dash && npx nx build neuroassist-ai-web`

#### Generators Nx obrigatorios

Nenhum.

#### Politica de testes

Unit obrigatorio basico: verificar que `dashInit` chama `DashStreamClient.open()` e `DashHomeFacade.load()`. Usar mocks de ambos via TestBed. E2E cobre o flow completo em P6.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. Bootstrap e shell integration sao DI puro — sem HTTP diretamente.

#### Criterios de aceite

```bash
cd frontend && npx nx lint dash  # 0 errors
cd frontend && npx nx build neuroassist-ai-web  # 0 erros
# Verificar que app monta em / com DASH; /entrada renderiza sem chrome
```

#### Riscos

- `ShellOutletRegistry` (`@neuroassist/shared/ui-components`) pode nao estar estabilizado. Usar stub/comentario em vez de referencia real — documentar `TODO[INT-FE-DASH-003]`.
- `dashInit` e `dashDestroy` em `APP_INITIALIZER` requer integracao em `app.config.ts` (scope:web) — verificar path.

#### Instrucao para implementador

Criar `DashShellIntegrationService` com stub dos 4 metodos documentados. Criar `dash.bootstrap.ts` com `dashInit/dashDestroy`. Wiring em `app.config.ts` e opcional neste ciclo (pode ser documentado como TODO). Exportar do barrel. Rodar lint+build.

---

### Prioridade 6 — E2E: flows principais

Status: pendente
Issues cobertas: FE-DASH-024, FE-DASH-025, FE-DASH-026, FE-DASH-028, FE-DASH-030
Categoria: VALIDACAO

#### Objetivo

Implementar testes Playwright para os flows principais do modulo DASH: entrada page, home status page, notifications center, bootstrap nav, e fallback/error states.

#### Problema

Nenhum spec Playwright implementado para DASH. O arch backlog FE-DASH-024..030 documenta os cenarios; specs nao foram criados ainda.

#### Resultado esperado

`cd frontend && npx nx e2e dash-e2e` retorna exit 0 com >= 10 cenarios PASS (subconjunto de FE-DASH-024..030).

#### Arquivos-alvo

```
frontend/apps/dash-e2e/src/entrada.spec.ts          (CRIAR)
frontend/apps/dash-e2e/src/home-status.spec.ts      (CRIAR)
frontend/apps/dash-e2e/src/notifications.spec.ts    (CRIAR)
frontend/apps/dash-e2e/src/bootstrap.spec.ts        (CRIAR)
frontend/apps/dash-e2e/playwright.config.ts         (VERIFICAR — pode ja existir)
```

#### Cenarios por spec (selectors e assertions obrigatorios)

**entrada.spec.ts (FE-DASH-025 — G3):**
- "Rota /entrada renderiza sem shell chrome": `goto('/entrada')` → nao tem `data-shell-chrome="true"`; logo visivel; CTA "Entrar no painel" visivel; `getByRole('link', { name: 'Entrar no painel' })` presente
- "CTA da entrada navega para /": clicar CTA → URL = `/` (ou shell home)

**home-status.spec.ts (FE-DASH-024 — G1/G2):**
- "Home page carrega com status do paciente": `goto('/')` → `getByRole('heading', { name: /Olá/ })` visivel
- "StatusCard mostra paciente principal": patient name visivel no heading
- "Nav-item Onboarding: comportamento Wave-0": link aparece (Wave-0 bypass — documentar como Wave-0)
- "Home redirect da /entrada": Wave-0 → `/` acessivel diretamente sem redirect

**notifications.spec.ts (FE-DASH-026 — G5):**
- "Centro de notificacoes lista notificacoes pendentes": `goto('/notificacoes')` → pelo menos uma notificacao visivel
- "Acknowledge remove notificacao da lista": clicar "Revisei" → notificacao desaparece
- "Lista vazia mostra estado tranquilizador": apos acknowledge de todas → "Tudo revisado" visivel

**bootstrap.spec.ts (FE-DASH-028):**
- "Nav registrado no shell apos bootstrap": header da shell tem link para home e notificacoes
- "Destroy fecha SSE sem crash": Wave-0 apenas verifica que app nao crasha ao navegar entre rotas

**error-fallback.spec.ts (FE-DASH-030 — G8):**
- "503 retry: home mostra botao tentar novamente": simular erro via `page.route()` → botao "Tentar novamente" visivel
- "SSE fallback nao quebra UI": fallback ativo (mock) → notifications ainda aparecem via polling

#### Sequencia de execucao

1. Verificar que `npx nx serve neuroassist-ai-web` sobe em localhost:4200 com rota `/` e `/notificacoes` wired e `provideDash({ adapter: 'mock' })` ativo
2. Verificar/criar `playwright.config.ts` em `apps/dash-e2e/`
3. Criar specs na ordem: `entrada.spec.ts`, `home-status.spec.ts`, `notifications.spec.ts`, `bootstrap.spec.ts`
4. Para estados de erro: usar `page.route('/panel/**', route => route.fulfill({ status: 503 }))` do Playwright
5. Rodar `cd frontend && npx nx e2e dash-e2e` — confirmar >= 10 PASS

#### Generators Nx obrigatorios

Se `dash-e2e` app nao existir em `frontend/apps/`:
```bash
cd frontend && npx nx g @nx/playwright:configuration --project=dash-e2e
```

#### Politica de testes

E2E Playwright com `baseURL: http://localhost:4200`. Mock adapter ativo (nao requer backend). Usar seletores semanticos (`getByRole`, `getByText`, `getByLabel`). Cenarios Wave-0: documentar comportamento esperado em modo bypass com comentario `// Wave-0: ...`.

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

Nao. E2E usa mock adapter; nao faz chamadas reais com X-Empresa-Id.

#### Criterios de aceite

```bash
cd frontend && npx nx e2e dash-e2e
# Exit code: 0
# Tests: >= 10 PASS, 0 fail
```

#### Riscos

- **RISCO P6-01:** Rotas `/ ` e `/notificacoes` devem estar registradas em `app.routes.ts` do shell. Verificar antes de implementar specs.
- **RISCO P6-02:** `dash-e2e` app pode nao existir ainda. Verificar `frontend/apps/` antes de criar specs.
- **RISCO P6-03:** Mock adapter retorna dados deterministicos — cenarios de estado vazio/erro requerem `page.route()` para interceptar e substituir respostas.

#### Instrucao para implementador

Verificar primeiro que o app serve em localhost:4200 com rotas DASH wired. Verificar existencia do app `dash-e2e` — criar com generator se ausente. Criar specs por feature. Para estados de erro/vazio, usar `page.route()` do Playwright. Rodar `npx nx e2e dash-e2e` ao final.

---

### Prioridade 7 — HTTP integration (DEFERRED)

Status: BLOQUEADO
Issues cobertas: FE-DASH-005, INT-FE-DASH-004, INT-FE-DASH-005, INT-FE-DASH-003
Categoria: WIRING

#### Objetivo

Ativar os adapters HTTP (`DashSummaryHttpAdapter`, `DashNotificationsHttpAdapter`) e write-throughs (calibracao via PAC, confirmacao via EVNT) quando dash-service e os servicos BE necessarios estiverem deployados.

#### Condicao de desbloqueio

- `dash-service` (porta 8090) deployado com rotas de Gateway expostas
- Gateway route `/panel/**` configurado
- `pac-service` endpoint de calibracao disponivel (`PUT /panel/.../calibration`)
- `evnt-service` endpoint de confirmacao disponivel (`POST /panel/events/.../confirm`)
- `ShellOutletRegistry` em `@neuroassist/shared/ui-components` estabilizado (INT-FE-DASH-003)

#### Acoes (nao executar agora)

1. Substituir `provideDash({ adapter: 'mock' })` por `provideDash({ adapter: 'http' })` em producao (`app.config.ts`)
2. Implementar `dashHttpInterceptorFn` que adiciona `X-Empresa-Id` via `@neuroassist/shared/access-http-base` — mesmo padrao de `acsHttpInterceptorFn`
3. Completar `DashSummaryHttpAdapter`: implementar `getSummary()` via `HttpClient`; URL base `${ACCESS_HTTP_BASE}/panel/summary`
4. Completar `DashNotificationsHttpAdapter`: implementar `listUnacknowledged()` e `acknowledge()` via `HttpClient`
5. Implementar write-throughs: calibracao (INT-FE-DASH-004) e confirmacao de evento (INT-FE-DASH-005)
6. Integrar `ShellOutletRegistry` (INT-FE-DASH-003) quando estabilizado
7. Configurar SSE auth (cookie ou query-param JWT) apos decisao com BE

#### Multi-empresa: @neuroassist/shared/access-http-base necessario?

SIM — obrigatorio em P7. Usar `@neuroassist/shared/access-http-base` como prefixo de URL e `dashHttpInterceptorFn` como interceptor que injeta `X-Empresa-Id`. Declarar `isMock: false` apos ativacao.

#### Criterios de aceite (quando desbloqueado)

```bash
cd frontend && npx nx lint dash && npx nx test dash && npx nx build neuroassist-ai-web
# Todos exit 0
# Smoke test manual: GET /panel/summary retorna 200; acknowledge funciona
```

#### Riscos

- Contrato da API dash-service pode diferir dos types em `dash.types.ts` — verificar com BE team antes de implementar
- SSE auth mecanismo nao decidido ainda — `TODO[DASH-005-AUTH]` pendente
- `ShellOutletRegistry` pode mudar interface antes de estabilizar

#### Instrucao para implementador

Nao executar ate que `dash-service` + GWY + servicos dependentes estejam deployados. Quando desbloqueados, seguir o padrao `acsHttpInterceptorFn` para consistencia de multi-empresa. Coordenar auth SSE com time BE antes de P7.

---

## 4. Dependencias externas

| Dependencia | Status | Issues | Bloqueia |
|---|---|---|---|
| dash-service (backend, porta 8090) | NAO DEPLOYADO | FE-DASH-005, INT-FE-DASH-004/005 | P7 |
| Gateway route /panel/** | NAO CONFIGURADO | FE-DASH-005 | P7 |
| EVNT FE export de activeEvent signal | NAO SCAFFOLDED | INT-FE-DASH-001 | P4 (mitigado com safe default) |
| EDGE FE export de deviceBadge signal | NAO SCAFFOLDED | INT-FE-DASH-002 | P4 (mitigado com safe default) |
| ShellOutletRegistry (@neuroassist/shared/ui-components) | NAO ESTABILIZADO | INT-FE-DASH-003 | P5 (stub em P5) |
| pac-service (calibracao) | NAO CONFIRMADO | INT-FE-DASH-004 | P7 |
| evnt-service (confirmacao) | NAO CONFIRMADO | INT-FE-DASH-005 | P7 |
| neuroassist-ai-web (app shell) | DISPONIVEL | — | P6 (rota /  e /notificacoes) |
| SSE auth mecanismo (BE decisao) | PENDENTE | DASH-FE-002 | P3 (mitigado — Wave-0 sem auth) |

**P1, P2, P3, P4, P5, P6 sao totalmente independentes de backend.** Apenas P7 requer dash-service.

---

## 5. Restricoes arquiteturais

- **RULE-NX-01 (scope boundary):** `libs/dash` nao pode importar de `scope:evnt`, `scope:edge`, `scope:hist` — apenas via InjectionToken em `scope:web`
- **RULE-NX-02 (type boundary):** `type:feature` pode depender de `type:data-access`, `type:ui`, `type:util`; nao o inverso
- **Selector prefix:** `lib-*` obrigatorio (`.eslintrc.json` da lib) — corrigido em P1
- **OnPush obrigatorio:** `ChangeDetectionStrategy.OnPush` em todos os componentes — ja aplicado
- **Signals readonly:** `.asReadonly()` em todos os sinais publicos das facades — ja aplicado
- **Sem HttpClient direto em componentes:** injetado apenas em adapters/services — ja respeitado
- **Non-optimistic acknowledge:** facade evicta da lista somente apos confirmacao do port — ja implementado
- **calm derivado de activeEvent, nunca de aggregate local:** invariante de seguranca (SPEC-01 §3) — implementado em P4

---

## 6. Referencias

- `docs/architecture/frontend/modules/dash/frontend/module-state.md` — BASELINE 2026-08-19
- `docs/architecture/frontend/modules/dash/frontend/plans/plan-FE-DASH.md` — plano original do Architect (9 ondas P1-P9, 16d estimado)
- `docs/architecture/frontend/standards/nx-boundaries.md` — regras de boundary e prefix
- `docs/architecture/shared/multi-empresa.md` — padrao X-Empresa-Id (relevante para P7)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/` — SPECs DASH (SPEC-01..08)
- `docs/architecture/frontend/modules/dash/frontend/adr/` — ADRs DASH (ADR-005, ADR-006)
- `docs/architecture/frontend/issues/DASH-FE-001.md` — lint errors (baseline)
- `docs/architecture/frontend/issues/DASH-FE-002.md` — DashStreamClient (baseline)
- `docs/architecture/frontend/issues/DASH-FE-003.md` — unit tests (criado neste ciclo)
- `docs/architecture/frontend/issues/INT-FE-DASH-001.md` — injection tokens (baseline)
- `docs/architecture/frontend/modules/dash/frontend/issues/FE-DASH-018.md` — DashShellIntegrationService (arch backlog)
- `docs/architecture/frontend/modules/dash/frontend/issues/FE-DASH-024..030.md` — e2e arch backlog
- `frontend/libs/dash/src/lib/dash.facade.ts` — DashHomeFacade (SPEC-03 §3/§4)
- `frontend/libs/dash/src/lib/notifications.facade.ts` — NotificationsFacade (SPEC-05)
- `frontend/libs/hist/src/lib/hist.facade.spec.ts` — padrao de spec de facade (referencia)
- `frontend/apps/neuroassist-ai-web/src/app/app.config.ts` — providers de app (para P4 + P7)
