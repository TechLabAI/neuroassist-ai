# Plano FE — EVNT (neuroassist-ai-web)

> Atualizado por: `/fe-planner` (pipeline B, reabertura P6-P9) | 2026-08-16
> Score atual: **90/100 — Maduro** | Issues MVP DONE: 37/37 (100%); Gates: lint PASS, build PASS, test 34/34 PASS
> Tipo de planejamento: **INCREMENTAL** (P1-P5 DONE; P6-P9 agora ativos — BE pronto desde commit 4fa3366)
> Plano anterior: `2026-08-13` — P1-P5 executados com sucesso; P6-P9 estavam deferred; agora ativados.

---

## Status das prioridades

- **P1** — Nx Lint Fix + TypeScript Build Repair — **DONE** (FE-EVNT-001, FE-EVNT-003)
- **P2** — libs/evnt/ui: UrgencyBadge + EventTimestamp — **DONE** (FE-EVNT-018)
- **P3** — Feature Routine Leaf: RoutineNotice + EventList + EventFeedPage — **DONE** (FE-EVNT-013, 014, 015)
- **P4** — Feature Routine Smart: EventDetailPage + FeedbackVerdictControl — **DONE** (FE-EVNT-016, 017)
- **P5** — Foundation Completion: EVNT_NAV_CONFIG + Unit Tests — **DONE** (FE-EVNT-004, 006 + specs P5)
- **P6** — Integração real com backend + Guards + HTTP wiring — **pendente** (INT-FE-EVNT-001..007, FE-EVNT-019..021, FE-EVNT-023)
- **P7** — Polish/UX: loading states, degraded banner — **pendente** (FE-EVNT-022, FE-EVNT-024)
- **P8** — Observability: EvntTelemetryService zero-PII — **pendente** (FE-EVNT-025)
- **P9** — e2e Playwright (OBRIGATORIO — NAO PODE SER DEFERRED) — **pendente** (FE-EVNT-026..030)

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

EVNT é a superfície de alarme e notificação do NeuroAssist AI. Quando um gesto de emergência é detectado, toda a tela vira um alarme full-bleed vermelho (`AlarmOverlayComponent`) que só fecha com confirmação explícita. Comandos de rotina chegam como notificações discretas (`RoutineNoticeComponent`). O AlarmOverlay é um shell outlet global (ADR-005), não uma rota. O canal live é SSE com fallback automático para polling.

### Estado atual observado (2026-08-16)

P1-P5 completamente entregues: 37/37 issues MVP DONE, gates ALL GREEN (lint 0 warnings, build 710ms, test 34/34 PASS, 11 suites). O backend `evnt-service` ficou completo no commit `4fa3366` (P9 hardening — circuit breakers, WireMock contracts, métricas). Os endpoints disponíveis são: `GET /events/active`, `GET /events/stream` (SSE), `POST /events/{id}/confirmation`, `GET /events`, `GET /events/{id}`.

### Continuidade da última rodada

P5 entregou: `evnt.nav-config.ts`, `evnt-error-mapper.ts`, X-Empresa-Id injection em `EvntClientHttpAdapter`, tokens DI Wave-0 (`EVNT_EMPRESA_CONTEXT`, `EVNT_AUTH_SESSION`, `EVNT_ACCESS_DECISION`), specs de facade e mock adapter. O `openStream()` ainda é stub que degrada para polling. Os guards têm Wave-0 bypass via mock adapter check.

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|---|---|---|
| Estrutural | OK | P1-P5 DONE; lib buildable; lint/build PASS |
| Funcional (MVP) | OK | 37/37 DONE; componentes, facade, rotas todos entregues |
| Testes unit | OK | 34/34 PASS (11 suites) |
| Integração BE real | ABERTA | SSE stub; adapter default ainda 'mock'; guards Wave-0 |
| Polish/UX | ABERTA | Loading states e degraded banner sem componente real |
| Observabilidade | ABERTA | Sem telemetria estruturada |
| e2e | ABERTA — OBRIGATORIO | Nenhum spec e2e; evnt-e2e app existe com playwright.config.ts |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

PARCIAL. `EvntClientHttpAdapter` já injeta `EVNT_EMPRESA_CONTEXT` (Wave-0, retorna null) e adiciona `X-Empresa-Id` condicionalmente via `empresaHeaders`. P6 substitui esta abordagem por `evntHttpInterceptorFn` (padrão ACS — commit 3e4328d), tornando o interceptor reutilizável e desacoplado do adapter.

---

## 1. Principais achados

### Pontos fortes (P1-P5 legados)

- `EventDeliveryFacade` signals-first, CONCUR-01 guard, non-optimistic confirm, polling fallback
- `EvntClientPort` completo (6 métodos) + adapters (mock + http) com error mapper
- 5 feature components (AlarmOverlay, RoutineNotice, EventList, EventDetail, FeedbackVerdictControl) standalone, OnPush, a11y
- Tokens DI Wave-0 prontos para promoção (EVNT_EMPRESA_CONTEXT, EVNT_AUTH_SESSION, EVNT_ACCESS_DECISION)
- `evntGuard` com GUARD-01 + GUARD-02 implementados (Wave-0 bypass via mock check)

### Gaps P6-P9

1. `openStream()` em `EvntClientHttpAdapter` retorna erro imediatamente — SSE nunca funciona em produção
2. `evnt.providers.ts` default ainda é `'mock'` — produção não conecta ao BE
3. Não há `evntHttpInterceptorFn` separado (padrão ACS) — X-Empresa-Id é per-call no adapter
4. `FeatureFlagService` hardcoded `'on'` — sem leitura runtime das flags liveChannel/pollIntervalMs/routineNotices
5. `AccessDecisionService` Wave-0 null — guards passam apenas pelo mock bypass
6. `AppError.type` pode não existir em `@neuroassist/shared/error-model` — impede distinção 403/tenant-mismatch vs 403/forbidden (INT-FE-EVNT-006 full)
7. Loading states e DegradedModeBanner inline — sem componente real (P7)
8. Zero telemetria estruturada (P8)
9. Zero specs e2e (P9) — `evnt-e2e` app existe com playwright.config.ts mas `src/` vazio

### Restrição crítica do usuário

**P9 (e2e) OBRIGATORIO** — não pode ser marcado como deferred novamente. A suíte Playwright completa (5 spec files em `frontend/apps/evnt-e2e/src/`) deve ser implementada e passar. O `evnt-e2e` app já existe com `playwright.config.ts` configurado.

### Aderência às regras Nx

- Boundaries lint PASS (corrigido em P1): `scope:evnt → scope:evnt | scope:shared`
- `enforceBuildableLibDependency: false` (workspace-wide, necessário pois shared libs são non-buildable Wave-0)
- Nenhuma sub-lib física nova necessária em P6-P9 — estrutura monolítica mantida

---

## 2. Estratégia geral

### Objetivo macro

Conectar o módulo EVNT ao backend real (P6), polir UX (P7), adicionar telemetria zero-PII (P8) e entregar cobertura e2e Playwright completa e obrigatória (P9). Ao final de P6, `provideEvnt({ adapter: 'http' })` está funcional em produção com SSE real, guards reais e X-Empresa-Id via interceptor.

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P6 | Backend Integration + HTTP wiring | BE pronto; SSE stub é gap funcional crítico; guards Wave-0 bloqueiam segurança | INT-FE-EVNT-001..007, FE-EVNT-019, FE-EVNT-020, FE-EVNT-021, FE-EVNT-023 |
| P7 | Polish/UX | Loading states e degraded banner são experiência mínima produção-grade | FE-EVNT-022, FE-EVNT-024 |
| P8 | Observability | Telemetria crítica para plataforma assistiva (rastreabilidade alarmes) | FE-EVNT-025 |
| P9 | e2e Playwright (OBRIGATORIO) | Instrução explícita do usuário: nenhum teste deferred; evnt-e2e app pronto | FE-EVNT-026, FE-EVNT-027, FE-EVNT-028, FE-EVNT-029, FE-EVNT-030 |

---

## 3. Plano por prioridade

---

### Prioridade P6 — Backend Integration + HTTP Wiring

Status: pendente
Issues cobertas: INT-FE-EVNT-001, INT-FE-EVNT-002, INT-FE-EVNT-003, INT-FE-EVNT-004, INT-FE-EVNT-005, INT-FE-EVNT-006, INT-FE-EVNT-007, FE-EVNT-019, FE-EVNT-020, FE-EVNT-021, FE-EVNT-023
Categoria: WIRING

#### Objetivo

Conectar `EvntClientHttpAdapter` ao backend real: implementar SSE via `fetch()` com headers customizados, criar `evntHttpInterceptorFn` (padrão ACS) para X-Empresa-Id, completar error mapper com `type` discriminante para 403 diferenciados, integrar `AccessDecisionService` e `FeatureFlagService` reais, e tornar `provideEvnt({ adapter: 'http' })` funcional em produção.

#### Problemas identificados

1. **INT-FE-EVNT-002 (SSE)**: `openStream()` retorna erro imediatamente → façade sempre degrada para polling. `EventSource` nativo não suporta headers customizados (Authorization + X-Empresa-Id). Solução: `fetch()` API com `ReadableStream` + parser SSE manual.
2. **INT-FE-EVNT-006 (error type)**: `AppError` em `@neuroassist/shared/error-model` pode não ter `type?: string`. Sem esse campo, 403/tenant-mismatch e 403/forbidden são indistinguíveis (ambos chegam como `HttpErrorResponse` com status 403, corpo diferente em `type`).
3. **INT-FE-EVNT-004/005 (Wave-0 guards e flags)**: Guard bypassa via mock check; flags hardcoded `'on'`. Precisa de injeção real com fallback seguro.
4. **INT-FE-EVNT-001 (Shell outlet)**: `EvntShellIntegrationService` existe mas ShellOutletRegistry em `@neuroassist/shared/ui-components` precisa ser validado — interface `registerGlobalOutlet()` precisa aceitar o contrato EVNT.
5. **Interceptor pattern**: Adapter injeta `empresaHeaders` per-call. Padrão ACS usa `HttpInterceptorFn` registrado em `withInterceptors([])` — mais limpo e consistente com o padrão do workspace.

#### Correções mandatórias

**Sub-tarefa P6-A: evntHttpInterceptorFn (seguindo padrão ACS)**

1. Criar `frontend/libs/evnt/src/lib/api/evnt-http.interceptor.ts` com `evntHttpInterceptorFn: HttpInterceptorFn`.
   - Injeta `EVNT_EMPRESA_CONTEXT` (opcional).
   - Se `context?.id` não-null: clona request com `setHeaders: { 'X-Empresa-Id': id }`.
   - Se null: passa sem modificar (Wave-0, pre-provisioning).
   - Criar `frontend/libs/evnt/src/lib/api/evnt-http.interceptor.spec.ts` (2 testes: com context → header adicionado; sem context → request intacto).
2. Em `evnt.providers.ts`: adicionar `withInterceptors([evntHttpInterceptorFn])` ao `makeEnvironmentProviders`.
3. Em `EvntClientHttpAdapter`: remover `private get empresaHeaders` e remover `headers: this.empresaHeaders` de todos os métodos — o interceptor assume essa responsabilidade.

**Sub-tarefa P6-B: SSE real via fetch() (INT-FE-EVNT-002)**

4. Substituir `openStream()` em `evnt-client-http.adapter.ts` com implementação `fetch()`-based:
   - URL: `${this.base}/stream`
   - Headers: `{ 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache' }` (Authorization é adicionado pelo interceptor de auth; X-Empresa-Id pelo `evntHttpInterceptorFn`).
   - Nota: `fetch()` não passa por `HttpClient` interceptors. Para X-Empresa-Id em SSE: injetar `EVNT_EMPRESA_CONTEXT` diretamente e montar o header manualmente no `fetch()` options.
   - Parser SSE manual: ler `ReadableStream<Uint8Array>`, decodificar com `TextDecoder`, acumular buffer, extrair linhas `data:`, emitir `SseFrame` via RxJS `Observable`.
   - Reconexão: re-chamar fetch com `Last-Event-ID` header quando stream fechar (not via `error`).
   - `takeUntilDestroyed()` não se aplica em adapter — o caller (façade) gerencia o ciclo de vida via `unsubscribe()`.
5. Criar `frontend/libs/evnt/src/lib/api/evnt-sse-parser.ts`: função pura `parseSseLine(buffer: string): SseFrame | null`. Sem DI. Testável isoladamente.
6. Criar `frontend/libs/evnt/src/lib/api/evnt-sse-parser.spec.ts` (3 testes: linha `data:` → SseFrame; linha `id:` → lastEventId; linha vazia → null).

**Sub-tarefa P6-C: Error mapper type discriminante (INT-FE-EVNT-006 completo)**

7. Verificar `frontend/libs/shared/error-model/src/index.ts` — confirmar se `AppError` tem `type?: string`.
   - Se não: adicionar `type?: string` ao `AppError` interface.
8. Em `evnt-error-mapper.ts`: garantir que `pd.type` é copiado para `AppError.type` (não apenas para `code`). Resultado: `{ code: pd.type ?? ..., type: pd.type, message: pd.detail ?? pd.title ?? err.message }`.
9. Atualizar `evnt-error-mapper.spec.ts`: adicionar 2 testes — 403 com `type: '/errors/tenant-mismatch'` → `AppError.type === '/errors/tenant-mismatch'`; 403 com `type: '/errors/forbidden'` → `AppError.type === '/errors/forbidden'`.

**Sub-tarefa P6-D: FeatureFlagService real (INT-FE-EVNT-005)**

10. Verificar se `@neuroassist/shared/observability` ou outro shared lib exporta `FeatureFlagService`. Se não existir: criar `frontend/libs/evnt/src/lib/api/evnt-feature-flags.service.ts` com implementação local usando `EVNT_MODULE_DESCRIPTOR.featureFlags` como defaults e `InjectionToken<EvntFeatureFlags>` para override em testes.
11. Em `EventDeliveryFacade.startLiveChannel()`: ler `liveChannel` da flag service antes de abrir SSE. Se `liveChannel === 'off'`: iniciar apenas polling (não tentar SSE). `pollIntervalMs` também lido da flag service.

**Sub-tarefa P6-E: AccessDecisionService + Guards reais (INT-FE-EVNT-004, FE-EVNT-019, FE-EVNT-020)**

12. Em `evntGuard`: remover dependência de `EvntClientMockAdapter` para mock bypass. Substituir por token `EVNT_E2E_BYPASS` (`InjectionToken<boolean>`, default false). Em testes/mock mode: setar `EVNT_E2E_BYPASS = true` em `provideEvnt()`.
13. Adicionar `EVNT_AUTH_SESSION` provider em `provideEvnt()` que tenta `useExisting: AuthSession` (de `@neuroassist/shared/auth-session` se existir), com fallback null.
14. Adicionar provider de `EVNT_ACCESS_DECISION` analogo.
15. Verificar `FE-EVNT-020`: `evntGuard` deve redirecionar para `/` (não para login) quando permissão ausente mas sessão válida (GUARD-02 gap).

**Sub-tarefa P6-F: ShellOutletRegistry + EvntShellIntegrationService (INT-FE-EVNT-001, FE-EVNT-021)**

16. Ler interface de `ShellOutletRegistry` em `frontend/libs/shared/ui-components/src/`. Confirmar assinatura de `registerGlobalOutlet()`.
17. Validar `evnt-shell-integration.service.ts`: `registerAlarmOutlet()` chama `registry.registerGlobalOutlet({ component: () => Promise<AlarmOverlayComponent>, visible: facade.alarmVm, zLayer: 'interrupt' })`. Se interface divergir, ajustar o serviço (não o registry).
18. Criar `frontend/libs/evnt/src/lib/integration/evnt-shell-integration.service.spec.ts` (1 teste: `registerAlarmOutlet()` chama `registry.registerGlobalOutlet` com os parâmetros corretos).

**Sub-tarefa P6-G: Error handler components (INT-FE-EVNT-003, INT-FE-EVNT-007, FE-EVNT-023)**

19. Em `EventDetailPageComponent`: adicionar handling diferenciado para `AppError.type`:
    - `/errors/event-not-found` → `router.navigate(['/eventos'])` + toast "Evento não encontrado."
    - `/errors/tenant-mismatch` → `router.navigate(['/eventos'])` sem toast (não revelar existência)
    - `/errors/forbidden` → mensagem inline "Você não é responsável por este paciente." (sem redirect)
20. Confirmar `EvntClientHttpAdapter.confirm()` usa path `/api/v1/events/{eventoId}/confirmation` e body `{ verdict }`. Confirmar mapeamento de 409 (feedback-terminal) para `AppError { code: '/errors/feedback-terminal', ... }`.
21. Validar `EvntClientHttpAdapter.query(params)` serializa `pacienteId`, `from`, `to` para query string (INT-FE-EVNT-007).

**Sub-tarefa P6-H: Switch adapter padrão para produção**

22. Em `evnt.providers.ts`: adicionar export `provideEvntForProduction()` que chama `provideEvnt({ adapter: 'http' })` com `withInterceptors([evntHttpInterceptorFn])`. Documentar no JSDoc que `app.config.ts` deve usar `provideEvntForProduction()` em builds de produção e `provideEvnt({ adapter: 'mock' })` em testes.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/api/evnt-http.interceptor.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-http.interceptor.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts` (editar — remover empresaHeaders; substituir openStream())
- `frontend/libs/evnt/src/lib/api/evnt-sse-parser.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-sse-parser.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-error-mapper.ts` (editar — adicionar type ao AppError output)
- `frontend/libs/evnt/src/lib/api/evnt-error-mapper.spec.ts` (editar — 2 testes novos)
- `frontend/libs/evnt/src/lib/api/evnt-feature-flags.service.ts` (criar se shared lib não existir)
- `frontend/libs/evnt/src/lib/access/evnt.guard.ts` (editar — remover mock adapter check; adicionar EVNT_E2E_BYPASS)
- `frontend/libs/evnt/src/lib/integration/evnt-shell-integration.service.ts` (editar se divergir da interface)
- `frontend/libs/evnt/src/lib/integration/evnt-shell-integration.service.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/evnt.providers.ts` (editar — withInterceptors; provideEvntForProduction())
- `frontend/libs/evnt/src/lib/evnt.facade.ts` (editar — liveChannel flag check antes de SSE)
- `frontend/libs/evnt/src/lib/feature-routine/event-detail-page/event-detail-page.component.ts` (editar — error type handling)
- `frontend/libs/evnt/src/index.ts` (editar — export novos artefatos)

#### Contrato de tipo

```typescript
// evnt-http.interceptor.ts
export const evntHttpInterceptorFn: HttpInterceptorFn = (req, next) => {
  const ctx = inject(EVNT_EMPRESA_CONTEXT, { optional: true });
  const id = ctx?.id ?? null;
  if (!id) return next(req);
  return next(req.clone({ setHeaders: { 'X-Empresa-Id': id } }));
};

// evnt-sse-parser.ts
export interface SseBuffer { dataLines: string[]; lastId: string | null; }
export function parseSseLine(line: string, buffer: SseBuffer): SseFrame | null {
  // 'data: {...}' → accumulate; empty line → emit SseFrame; 'id: N' → set lastId
}

// openStream() strategy (evnt-client-http.adapter.ts):
openStream(): Observable<SseFrame> {
  return new Observable<SseFrame>((subscriber) => {
    const controller = new AbortController();
    const empresaId = inject(EVNT_EMPRESA_CONTEXT, { optional: true })?.id ?? null;
    const headers: Record<string, string> = { 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache' };
    if (empresaId) headers['X-Empresa-Id'] = empresaId;
    // Note: EVNT_EMPRESA_CONTEXT injected in constructor (not inside Observable callback)
    void fetch(`${this.base}/stream`, { headers, signal: controller.signal })
      .then(res => { /* read ReadableStream, parse SSE, call subscriber.next(frame) */ })
      .catch(err => { if (!controller.signal.aborted) subscriber.error(err); });
    return () => controller.abort();
  });
}

// evnt.providers.ts addition:
export function provideEvntForProduction(): EnvironmentProviders {
  return provideEvnt({ adapter: 'http' });
}
```

#### Sequência de execução

1. P6-A: criar `evntHttpInterceptorFn` + spec; editar `provideEvnt()` + `EvntClientHttpAdapter` (remover empresaHeaders)
2. P6-B: criar `evnt-sse-parser.ts` + spec; substituir `openStream()` com implementação fetch-based
3. P6-C: verificar `AppError.type`; atualizar error mapper + spec
4. P6-D: verificar/criar FeatureFlagService; integrar no facade
5. P6-E: refatorar guard (remover mock dependency; adicionar EVNT_E2E_BYPASS)
6. P6-F: validar ShellOutletRegistry; ajustar shell integration service + criar spec
7. P6-G: error type handling em EventDetailPageComponent; validar confirm() path e query() params
8. P6-H: adicionar `provideEvntForProduction()` em providers
9. Rodar gates: `npx nx lint @neuroassist/evnt && npx nx test @neuroassist/evnt && npx nx build @neuroassist/evnt`

#### Generators Nx obrigatórios

Nenhum. Todos os arquivos dentro da lib monolítica existente.

#### Política de testes

Unit obrigatório para: `evntHttpInterceptorFn` (2 specs), `evnt-sse-parser` (3 specs), error mapper update (2 specs novos), shell integration service (1 spec). Guard refactor: atualizar spec existente se houver.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Sim. `evntHttpInterceptorFn` injeta `EVNT_EMPRESA_CONTEXT` (Wave-0 null). Para SSE (`fetch()`): lê `EVNT_EMPRESA_CONTEXT` diretamente no construtor do adapter (não via interceptor, pois fetch não passa por HttpClient). Wiring completo aguarda promoção do token.

#### Critérios de aceite

```bash
cd frontend
npx nx lint @neuroassist/evnt --max-warnings=0      # zero warnings
npx nx test @neuroassist/evnt --max-warnings=0      # PASS (todos specs incluindo novos P6)
npx nx build @neuroassist/evnt                       # exit code 0
grep "evntHttpInterceptorFn" frontend/libs/evnt/src/lib/evnt.providers.ts  # match
grep "fetch(" frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts   # match (SSE real)
grep -v "empresaHeaders" frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts  # zero matches
```

#### Riscos

- `fetch()` dentro de `Observable` (P6-B): o adapter não pode injetar via `inject()` dentro do callback do Observable. Resolver: capturar `EVNT_EMPRESA_CONTEXT` no construtor e usar como campo privado. O `fetch()` callback usa o valor capturado.
- `AppError.type` em shared lib (P6-C): se `@neuroassist/shared/error-model` não aceitar edição (non-buildable), declarar `EvntAppError extends AppError { type?: string }` local em `evnt.types.ts`.
- `FeatureFlagService` não existe em shared (P6-D): criar local em `evnt-feature-flags.service.ts` com interface `EvntFeatureFlagsService { getBoolean(key: string, default: boolean): boolean; get<T>(key: string, default: T): T }`.
- SSE reconexão: `fetch()` não reconecta automaticamente como `EventSource`. A façade já tem lógica de polling fallback quando SSE fecha — garantir que o erro de `fetch()` termina o Observable (não silencia) para que a façade ative o polling.

#### Instrução para implementador

Executar sub-tarefas em ordem (P6-A → P6-B → ... → P6-H). Rodar gates ao final de P6-A e P6-B para detectar erros cedo. Não avançar para P7 com gates quebrando. O mock bypass em `evntGuard` via `EvntClientMockAdapter` class-check deve ser substituído por `EVNT_E2E_BYPASS` token antes de P6-E — não deixar dependência de instância concreta no guard.

---

### Prioridade P7 — Polish/UX: Loading States + Degraded Mode Banner

Status: pendente
Issues cobertas: FE-EVNT-022, FE-EVNT-024
Categoria: FUNCIONAL

#### Objetivo

Adicionar skeleton/spinner de loading ao `EventFeedPageComponent` e criar `EvntDegradedModeBannerComponent` real (substituir template inline). Implementar silenciamento de notificações de rotina quando `routineNotices=off`.

#### Problemas identificados

1. **FE-EVNT-022**: `EventFeedPageComponent` não tem skeleton enquanto `loading()===true`. Estado vazio sem mensagem explícita de "Sem notificações pendentes". Breadcrumb `[Eventos]` deve estar sempre visível.
2. **FE-EVNT-024**: O banner de modo degradado é template inline (`@if (deliveryMode() === 'degraded') { <div>Modo degradado</div> }`). Precisa de componente real com mensagem "Modo degradado — verificando a cada {n}s". A flag `routineNotices=off` deve suprimir silenciosamente as notificações (ERR-03).

#### Correções mandatórias

1. Em `EventFeedPageComponent`:
   - Adicionar `@if (loading()) { <evnt-feed-skeleton /> } @else { ... conteúdo real ... }`.
   - Estado vazio: `@if (!loading() && routineQueue().length === 0 && recentEvents().length === 0) { <p class="empty-state">Sem notificações pendentes</p> }`.
   - Breadcrumb `<h1 class="page-title">Eventos</h1>` FORA do bloco `@if (loading())` — sempre visível.
2. Criar `frontend/libs/evnt/src/lib/ui/feed-skeleton/evnt-feed-skeleton.component.ts` — standalone, OnPush, sem DI. Skeleton simples (3 `<div class="skeleton-row">` animados com CSS).
3. Criar `frontend/libs/evnt/src/lib/ui/degraded-banner/evnt-degraded-mode-banner.component.ts` — standalone, OnPush, `input: pollIntervalSec: number`. Renderiza "Modo degradado — verificando a cada {n}s".
4. Em `EventFeedPageComponent`: substituir `@if (deliveryMode() === 'degraded') { inline }` por `<evnt-degraded-mode-banner [pollIntervalSec]="pollIntervalSec()" />`. Adicionar `computed pollIntervalSec = computed(() => flagService.get('pollIntervalMs', 15000) / 1000)`.
5. Validar `ERR-03` (FE-EVNT-024): falha de entrega de rotina → retry silencioso (sem toast). Confirmar que `EventDeliveryFacade.loadHistory()` não emite para `_activeEvent` em caso de erro de rotina.
6. Criar specs para `EvntFeedSkeletonComponent` (1 teste: renderiza) e `EvntDegradedModeBannerComponent` (1 teste: exibe pollIntervalSec).

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/ui/feed-skeleton/evnt-feed-skeleton.component.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/feed-skeleton/evnt-feed-skeleton.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/degraded-banner/evnt-degraded-mode-banner.component.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/degraded-banner/evnt-degraded-mode-banner.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-feed-page.component.ts` (editar — skeleton, empty state, breadcrumb, banner component)
- `frontend/libs/evnt/src/index.ts` (editar — novos exports)

#### Contrato de tipo

```typescript
// evnt-feed-skeleton.component.ts
@Component({ selector: 'evnt-feed-skeleton', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class EvntFeedSkeletonComponent { } // template: 3 div.skeleton-row com animation

// evnt-degraded-mode-banner.component.ts
@Component({ selector: 'evnt-degraded-mode-banner', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class EvntDegradedModeBannerComponent {
  readonly pollIntervalSec = input.required<number>();
  // template: <aside role="status">Modo degradado — verificando a cada {{pollIntervalSec()}}s</aside>
}
```

#### Sequência de execução

1. Criar `EvntFeedSkeletonComponent` + spec
2. Criar `EvntDegradedModeBannerComponent` + spec
3. Editar `EventFeedPageComponent`: integrar skeleton, empty state, breadcrumb, banner component
4. Rodar gates

#### Generators Nx obrigatórios

Nenhum. Sub-diretórios dentro de `ui/`.

#### Política de testes

Unit: 1 spec por novo componente + editar spec de `EventFeedPageComponent` para cobrir skeleton visível (loading=true) e empty state.

#### Multi-empresa: necessário?

Não em P7. Componentes presentacionais.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # PASS (incluindo specs P7)
npx nx lint @neuroassist/evnt --max-warnings=0
npx nx build @neuroassist/evnt
```

#### Riscos

- CSS skeleton animation: usar `@keyframes pulse` inline no componente (sem dependência de `@neuroassist/shared/ui-tokens`). Suficiente para MVP.
- `RoutineFeedVm.loading` signal: confirmar que `EventDeliveryFacade` expõe `loading` como signal readonly. Se não, adicionar à façade antes de referenciar em `EventFeedPageComponent`.

#### Instrução para implementador

Criar os dois componentes de UI primeiro (zero dependências). Depois editar `EventFeedPageComponent`. Rodar gates ao final.

---

### Prioridade P8 — Observability: EvntTelemetryService zero-PII

Status: pendente
Issues cobertas: FE-EVNT-025
Categoria: FUNCIONAL

#### Objetivo

Criar `EvntTelemetryService` seguindo exatamente o padrão de `AcsTelemetryService` (commit 3e4328d, `frontend/libs/acs/src/lib/telemetry/acs-telemetry.service.ts`). Emitir eventos nos momentos-chave do fluxo EVNT. Zero PII: apenas urgência e IDs opacos.

#### Problemas identificados

- Nenhum evento de telemetria emitido no módulo EVNT. A plataforma assistiva requer rastreabilidade de alarmes para auditoria e melhoria (FE-EVNT-025).

#### Correções mandatórias

1. Criar `frontend/libs/evnt/src/lib/telemetry/evnt-telemetry.service.ts` — `@Injectable({ providedIn: 'root' })`, injeta `ObservabilityService` de `@neuroassist/shared/observability`.
2. Métodos a implementar:
   - `trackAlarmReceived(urgencia: 'emergencia' | 'rotina')` → emit `evnt.alarm.received`
   - `trackConfirmStart(eventoId: string)` → emit `evnt.alarm.confirm.submitted` (ID opaco, não PII)
   - `trackConfirmSuccess(eventoId: string)` → emit `evnt.alarm.confirm.success`
   - `trackConfirmConflict(eventoId: string)` → emit `evnt.alarm.confirm.conflict` (409)
   - `trackConfirmError()` → emit `evnt.alarm.confirm.error`
   - `trackDegradedActivated(reason: 'sse_error' | 'sse_timeout')` → emit `evnt.delivery.degraded.activated`
   - `trackSseError()` → emit `evnt.stream.sse.error`
3. Em `EventDeliveryFacade`: injetar `EvntTelemetryService` e chamar nos pontos corretos:
   - `startLiveChannel()` → `trackSseError()` quando SSE falha e facade ativa polling
   - `confirm()` → `trackConfirmStart()` antes do request; `trackConfirmSuccess()` no success; `trackConfirmConflict()` no 409; `trackConfirmError()` nos outros erros
   - `hydrate()` (ou `loadActive()`) → `trackAlarmReceived()` quando `activeEvent` não-null
   - `_deliveryMode.set('degraded')` → `trackDegradedActivated(reason)`
4. Criar `frontend/libs/evnt/src/lib/telemetry/evnt-telemetry.service.spec.ts` (3 testes: `trackConfirmSuccess()` chama `obs.emit({ event: 'evnt.alarm.confirm.success', ... })`; `trackAlarmReceived('emergencia')` chama emit com urgencia; `trackDegradedActivated('sse_error')` emite reason).

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/telemetry/evnt-telemetry.service.ts` (criar)
- `frontend/libs/evnt/src/lib/telemetry/evnt-telemetry.service.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/evnt.facade.ts` (editar — injetar EvntTelemetryService; chamar nos 7 pontos acima)
- `frontend/libs/evnt/src/index.ts` (editar — export EvntTelemetryService)

#### Contrato de tipo

```typescript
// evnt-telemetry.service.ts
@Injectable({ providedIn: 'root' })
export class EvntTelemetryService {
  private readonly obs = inject(ObservabilityService);

  trackAlarmReceived(urgencia: 'emergencia' | 'rotina'): void {
    this.obs.emit({ event: 'evnt.alarm.received', urgencia });
  }
  trackConfirmStart(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.submitted', eventoId }); // opaque ID — not PII
  }
  trackConfirmSuccess(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.success', eventoId });
  }
  trackConfirmConflict(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.conflict', eventoId });
  }
  trackConfirmError(): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.error' });
  }
  trackDegradedActivated(reason: 'sse_error' | 'sse_timeout'): void {
    this.obs.emit({ event: 'evnt.delivery.degraded.activated', reason });
  }
  trackSseError(): void {
    this.obs.emit({ event: 'evnt.stream.sse.error' });
  }
}
```

#### Sequência de execução

1. Criar `EvntTelemetryService` + spec
2. Editar `EventDeliveryFacade`: adicionar `private readonly telemetry = inject(EvntTelemetryService)` e calls nos 7 pontos
3. Rodar gates

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit: 3 specs mínimos para `EvntTelemetryService`. Spec de `EventDeliveryFacade` (existente) não precisa cobrir telemetry calls — a facade spec usa mock port, telemetry service seria spy.

#### Multi-empresa: necessário?

Não. Telemetria é zero-PII, sem contexto de empresa.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # PASS
npx nx lint @neuroassist/evnt --max-warnings=0
npx nx build @neuroassist/evnt
grep "EvntTelemetryService" frontend/libs/evnt/src/lib/evnt.facade.ts  # match
```

#### Riscos

- `ObservabilityService` em `@neuroassist/shared/observability`: se for stub Wave-0 sem método `emit()`, implementar local `EvntObservabilityStub` e injetar via token. Não bloquear P8 por stub incompleto.

#### Instrução para implementador

Criar o service primeiro (trivial, segue ACS exatamente). Depois editar a facade. Spec de facade não precisa testar chamadas de telemetria — focar nos 3 specs próprios do service.

---

### Prioridade P9 — e2e Playwright (OBRIGATORIO — NAO PODE SER DEFERRED)

Status: pendente
Issues cobertas: FE-EVNT-026, FE-EVNT-027, FE-EVNT-028, FE-EVNT-029, FE-EVNT-030
Categoria: VALIDACAO

**RESTRICAO CRITICA:** Esta prioridade NÃO PODE ser marcada como deferred. É instrução explícita do usuário. Os 5 spec files devem existir, os cenários devem estar implementados como testes Playwright reais (`test()`, não `test.skip()`), e devem passar com `npx nx e2e evnt-e2e`.

#### Objetivo

Implementar os 5 spec files Playwright em `frontend/apps/evnt-e2e/src/` cobrindo os fluxos críticos: alarme de emergência, confirmação, concorrência, rotina e modo degradado. Usar `page.route()` para interceptar requests HTTP e simular o backend — não depender do BE real em execução para os testes passarem.

#### Problemas identificados

- `frontend/apps/evnt-e2e/src/` está vazio. A app `evnt-e2e` existe com `playwright.config.ts` mas zero specs.
- Cinco issues abertas (FE-EVNT-026..030) definem exatamente os cenários a cobrir.

#### Correções mandatórias (5 spec files)

**FE-EVNT-026 — alarme-emergencia-preempcao.spec.ts**

Cenário 1: Alarme preempta a tela ativa.
- `page.route('**/api/v1/events/active', ...)` → retorna `ActiveEventsDto` com `activeEvent` de urgência 'emergencia'.
- Navegar para `/eventos`. Verificar que `[data-testid="alarm-overlay"]` é visível (full-bleed).
- Verificar `role="alertdialog"` presente.

Cenário 2: Recarregar durante alarme ativo re-exibe o alarme (hidratação).
- Mesmo route mock; recarregar página; verificar overlay visível.

**FE-EVNT-027 — confirmar-atendimento.spec.ts**

Cenário 1: Confirmação bem-sucedida limpa o alarme.
- Mock `GET /events/active` → alarme ativo; mock `POST /events/{id}/confirmation` → 200 OK com `ConfirmationResponseDto`.
- Clicar botão "Confirmar Atendimento"; verificar overlay desaparece.

Cenário 2: Falha de rede mantém o alarme + retry inline.
- Mock `POST /events/{id}/confirmation` → network error (abort request).
- Clicar confirmar; verificar overlay permanece; verificar mensagem de erro inline.

**FE-EVNT-028 — confirmacao-concorrente.spec.ts**

Cenário: 409 (feedback-terminal) tratado como sucesso-equivalente.
- Mock `POST /events/{id}/confirmation` → 409 com body `{ type: '/errors/feedback-terminal', confirmadoPor: 'Outra Cuidadora', confirmadoAt: '2026-08-16T10:00:00Z' }`.
- Clicar confirmar; verificar overlay limpo; verificar texto "Já confirmado por Outra Cuidadora" visível.

**FE-EVNT-029 — notificacao-rotina.spec.ts**

Cenário 1: Notificação discreta não full-bleed.
- Mock `GET /events/active` → `{ activeEvent: null, routineQueue: [{ urgencia: 'rotina', ... }] }`.
- Verificar que `[data-testid="routine-notice"]` visível e `[data-testid="alarm-overlay"]` ausente.

Cenário 2: Dismiss remove notificação (client-only).
- Clicar botão dismiss; verificar `[data-testid="routine-notice"]` desaparece sem reload.

**FE-EVNT-030 — entrega-degradada.spec.ts**

Cenário: SSE falha → modo degradado ativo sem perder alarme.
- Mock `GET /events/stream` → abort/error; mock `GET /events/active` → alarme ativo.
- Verificar `[data-testid="degraded-banner"]` visível; verificar alarme overlay ainda presente.
- Bonus (BRIDGE-01): verificar que polling fallback está ativo (2º call a `GET /events/active` depois de {pollInterval}ms).

#### Arquivos-alvo

- `frontend/apps/evnt-e2e/src/alarme-emergencia-preempcao.spec.ts` (criar)
- `frontend/apps/evnt-e2e/src/confirmar-atendimento.spec.ts` (criar)
- `frontend/apps/evnt-e2e/src/confirmacao-concorrente.spec.ts` (criar)
- `frontend/apps/evnt-e2e/src/notificacao-rotina.spec.ts` (criar)
- `frontend/apps/evnt-e2e/src/entrega-degradada.spec.ts` (criar)
- `frontend/apps/evnt-e2e/src/evnt-e2e-helpers.ts` (criar — helpers compartilhados: mocks, navegação, fixtures)

#### Contrato de tipo / helpers

```typescript
// evnt-e2e-helpers.ts
import { Page, Route } from '@playwright/test';

export const EVNT_MOCK_ALARM: ActiveEventsDto = {
  activeEvent: { eventoId: 'evt-mock-001', urgencia: 'emergencia', commandLabel: 'Água', recognizedAt: '2026-08-16T10:00:00Z', feedback: 'nao_avaliado' },
  routineQueue: [],
};

export const EVNT_MOCK_ACTIVE_NONE: ActiveEventsDto = {
  activeEvent: null, routineQueue: [],
};

export async function mockActiveEvents(page: Page, body: ActiveEventsDto): Promise<void> {
  await page.route('**/api/v1/events/active', (route: Route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  );
}

export async function mockConfirm(page: Page, status: number, body: unknown): Promise<void> {
  await page.route('**/api/v1/events/**/confirmation', (route: Route) =>
    status === 0 ? route.abort() : route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  );
}

export async function navigateToEvnt(page: Page): Promise<void> {
  await page.goto('/eventos');
  await page.waitForLoadState('networkidle');
}
```

#### Sequência de execução

1. Criar `evnt-e2e-helpers.ts` com fixtures e mocks compartilhados
2. Criar `alarme-emergencia-preempcao.spec.ts` (2 cenários) — mais simples, valida infraestrutura e2e
3. Criar `confirmar-atendimento.spec.ts` (2 cenários) — testa o caminho safety crítico
4. Criar `confirmacao-concorrente.spec.ts` (1 cenário) — 409 handling
5. Criar `notificacao-rotina.spec.ts` (2 cenários) — dismiss client-only
6. Criar `entrega-degradada.spec.ts` (1 cenário + bonus) — SSE fallback
7. Rodar `npx nx e2e evnt-e2e` e confirmar todos os testes PASS

**Pré-requisito**: P6 deve estar concluído antes de P9. Em especial, `provideEvnt({ adapter: 'mock' })` precisa funcionar corretamente em modo e2e (app serve em `localhost:4200` com mock adapter — não requer BE real). Os `page.route()` mocks interceptam o HTTP mesmo com mock adapter; para cenários que dependem de HTTP real (SSE fallback), o mock adapter e `page.route()` trabalham juntos.

**Nota sobre `data-testid`**: Os specs usam `[data-testid="alarm-overlay"]`, `[data-testid="routine-notice"]`, `[data-testid="degraded-banner"]`. Verificar se esses atributos existem nos componentes; se não existirem, o implementador adiciona-os nos templates durante a implementação de P9 (não é necessário um item separado).

#### Generators Nx obrigatórios

Nenhum. O app `evnt-e2e` já existe.

#### Política de testes

TODOS os cenários devem ser `test()`, não `test.skip()`. Blockers conhecidos (Wave-0 guards, BE não real) devem ser cobertos via `page.route()` e `page.addInitScript()` — não por skip. Cenários que exigem BE real e não podem ser simulados com `page.route()` devem ser marcados com comentário `// BLOCKER-BE-REAL` mas ainda implementados como `test()` que passa contra o mock.

#### Multi-empresa: necessário?

Não diretamente em P9. Os mocks do e2e não precisam enviar `X-Empresa-Id` — a lógica é validada em unit tests (P6 interceptor specs).

#### Critérios de aceite — OBRIGATORIOS

```bash
cd frontend
npx nx e2e evnt-e2e   # TODOS os 10 cenários (5 specs) PASS — exit code 0
# Verificar que nenhum test.skip() existe:
grep -r "test.skip\|xit\|xtest" frontend/apps/evnt-e2e/src/   # zero matches
```

#### Riscos

- App deve estar servindo em `localhost:4200` quando `npx nx e2e evnt-e2e` rodar. O `playwright.config.ts` tem `baseURL: process.env['APP_URL'] ?? 'http://localhost:4200'` — garantir que `nx serve neuroassist-ai-web` está rodando antes de `nx e2e evnt-e2e`, ou configurar `webServer` no playwright.config.ts.
- `data-testid` ausentes em componentes: adicionar durante P9 (o implementador edita os templates quando necessário).
- SSE em e2e (FE-EVNT-030): `page.route('**/events/stream')` com `route.abort()` simula falha de SSE; o facade deve ativar polling — mas timing pode ser flaky. Usar `page.waitForTimeout(pollIntervalMs + 500)` ou `page.waitForRequest('**/events/active')` para confirmar o 2º poll.
- `routineQueue` no mock adapter: verificar que o mock adapter tem um item de rotina na fila padrão, ou adicionar via `page.addInitScript` que seta flag para o mock adapter incluir rotina.

#### Instrução para implementador

Criar `evnt-e2e-helpers.ts` primeiro (base de todos os specs). Depois os 5 specs em ordem de complexidade crescente (alarme → confirmar → concorrente → rotina → degradado). Para cada spec: rodar isoladamente com `npx nx e2e evnt-e2e --grep "nome do spec"` antes de rodar a suite completa. Não usar `test.skip()` em nenhuma hipótese — resolver o bloqueador ou implementar o mock adequado.

---

## 4. Dependências externas (endpoints BE, contratos de tipo, issues dependentes)

| Dependência | Tipo | Bloqueio | Resolução |
|---|---|---|---|
| `GET /api/v1/events/active` | BE endpoint | P6 (INT-FE-EVNT-002) | BE pronto (commit 4fa3366); testar via `page.route()` em P9 |
| `GET /api/v1/events/stream` | BE SSE endpoint | P6 (INT-FE-EVNT-002 — SSE real) | BE pronto; implementar `fetch()`-based `openStream()` |
| `POST /api/v1/events/{id}/confirmation` | BE endpoint | P6 (INT-FE-EVNT-003) | BE pronto; validar path e 409 handling |
| `GET /api/v1/events` | BE endpoint | P6 (INT-FE-EVNT-007) | BE pronto; validar query params serialization |
| `GET /api/v1/events/{id}` | BE endpoint | P6 (INT-FE-EVNT-007) | BE pronto; validar mapper EventoDto → EventoVm |
| `ShellOutletRegistry` em `@neuroassist/shared/ui-components` | Shared lib | P6 (INT-FE-EVNT-001) | Verificar interface; stub já existe |
| `ObservabilityService` em `@neuroassist/shared/observability` | Shared lib | P8 (FE-EVNT-025) | Verificar `emit()` interface; stub local se necessário |
| `FeatureFlagService` em shared | Shared lib | P6 (INT-FE-EVNT-005) | Verificar; criar local se não existir |
| `AppShellComponent` — renderizar AlarmOutlet | Shell app | INT-FE-EVNT-001 | Validado pelo e2e (P9 — alarme visível em `/eventos`) |
| WireMock contracts (BE commit 4fa3366) | BE test infra | P9 (e2e) | Usar `page.route()` ao invés de WireMock externo |

---

## 5. Restrições arquiteturais (Nx boundaries)

- `scope:evnt` pode depender de `scope:evnt` e `scope:shared` APENAS. Proibido importar de `scope:pac`, `scope:gest`, `scope:acs`, etc.
- `enforceBuildableLibDependency: false` — workspace-wide (corrigido em P1); regra de scope continua ativa.
- Estrutura monolítica `libs/evnt/src/lib/`: P6-P9 adicionam arquivos em sub-diretórios existentes (`api/`, `access/`, `integration/`, `ui/`, `telemetry/`). Nenhum `project.json` novo.
- Componentes presentacionais (EvntFeedSkeleton, EvntDegradedModeBanner): zero DI.
- `ChangeDetectionStrategy.OnPush` obrigatório em todos os componentes.
- `standalone: true` obrigatório — sem NgModule.
- `takeUntilDestroyed()` em todas as subscriptions RxJS (facades e smart components).
- `fetch()` para SSE: não usa `HttpClient`. Gerenciar AbortController manualmente no destructor do Observable.

---

## 6. Referências

| Documento | Relevância |
|---|---|
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` | Rotas e guards |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` | Port, adapters, SSE degradação |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` | ProblemDetail, ERR-01..04 |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-contracts.md` | Feature files e2e (FE-EVNT-026..030) |
| `docs/architecture/frontend/modules/evnt/frontend/adr/ADR-005-global-alarm-outlet-and-live-delivery.md` | AlarmOutlet não-rota |
| `docs/architecture/frontend/modules/evnt/frontend/issues/INT-FE-EVNT-001..007.md` | Issues P6 completas |
| `docs/architecture/frontend/modules/evnt/frontend/issues/FE-EVNT-019..030.md` | Issues P6-P9 completas |
| `frontend/libs/acs/src/lib/api/acs-http.interceptor.ts` | Padrão ACS para evntHttpInterceptorFn |
| `frontend/libs/acs/src/lib/telemetry/acs-telemetry.service.ts` | Padrão ACS para EvntTelemetryService |
| `frontend/apps/acs-e2e/src/caregiver-roster.spec.ts` | Padrão de spec e2e (page.route, helpers, data-testid) |
| `frontend/apps/evnt-e2e/playwright.config.ts` | Config e2e EVNT (baseURL, testDir, projects) |
| `docs/architecture/shared/multi-empresa.md` | Contexto multi-tenant (referência — não modificar) |
| `frontend/libs/evnt/src/lib/evnt.providers.ts` | Factory de DI — ponto de wiring de interceptors |
| `frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts` | Adapter HTTP — openStream() a substituir |
