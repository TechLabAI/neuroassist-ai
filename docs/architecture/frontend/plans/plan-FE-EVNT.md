# Plano FE — EVNT (neuroassist-ai-web)

> Gerado por: `/fe-planner` (pipeline B, etapa B3) | 2026-08-13
> Baseline score: **39/100 — Inicial/Parcial** | Issues DONE: 0/37 (0%); PARCIAL: 13/37; MISSING: 24/37
> Tipo de planejamento: **REPLANEJAMENTO**
> Plano de referência anterior: `docs/architecture/frontend/modules/evnt/frontend/plans/plan-FE-EVNT.md` (wave map original — não executado)

---

## Status das prioridades

- **P1** — Nx Lint Fix + TypeScript Build Repair — pendente (BLOQUEADOR ESTRUTURAL)
- **P2** — libs/evnt/ui: UrgencyBadge + EventTimestamp — pendente (FE-EVNT-018)
- **P3** — Feature Routine Leaf: RoutineNotice + EventList + EventFeedPage — pendente (FE-EVNT-013, 014, 015)
- **P4** — Feature Routine Smart: EventDetailPage + FeedbackVerdictControl — pendente (FE-EVNT-016, 017)
- **P5** — Foundation Completion: ShellDescriptor + NAV_CONFIG + Unit Tests — pendente (FE-EVNT-004, 006 + testes P1-P4)
- **P6** — Multi-empresa + Guards + Error Handling — pendente (INT-FE-EVNT-004, 006; FE-EVNT-021..024)

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

EVNT é a superfície de alarme e notificação do NeuroAssist AI. Quando um gesto de emergência é detectado, toda a tela vira um alarme full-bleed vermelho (`AlarmOverlayComponent`) que só fecha com confirmação explícita. Comandos de rotina chegam como notificações discretas (`RoutineNoticeComponent`). O AlarmOverlay é um shell outlet global (ADR-005), não uma rota. O canal live é SSE com fallback automático para polling.

### Estado atual observado

O código core existe e é bem estruturado: `EventDeliveryFacade` (274 linhas, signals-first, CONCUR-01, non-optimistic confirm, polling fallback), `AlarmOverlayComponent` (standalone, OnPush, a11y), `EvntClientPort` completo com 6 métodos. Mas nenhum gate de qualidade passa: lint FAIL (boundaries), build FAIL (tsconfig), testes não rodados. Das 8 issues P3, 6 estão MISSING — são o núcleo do MVP shippable e a prioridade imediata.

### Continuidade da última rodada

Não há histórico de implementação anterior (baseline inaugural). O scaffold criou 13 arquivos TS em `frontend/libs/evnt/src/lib/`. A próxima execução começa do zero neste plano.

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|---|---|---|
| Estrutural | PARCIAL | Lint FAIL (boundaries), Build FAIL (tsconfig) — bloqueador imediato |
| Funcional | PARCIAL | Core (facade, alarm, routes) OK; feature-routine 1/4, ui 0/2 |
| Testes | CRÍTICO | Zero specs rodados; jest.config.js criado mas não executado |
| Integração | LOW | ShellOutletRegistry stub OK; AccessDecision e FeatureFlag não integrados |
| Observabilidade | LOW | Sem telemetria; multi-empresa sem interceptor |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

**NÃO.** `@neuroassist/shared/access-http-base` é stub Wave-0 (`export const ACCESS_HTTP_BASE = '/api'`). O gap é workspace-wide. Ação planejada em P6: adicionar ponto de injeção `X-Empresa-Id` em `EvntClientHttpAdapter` como comentário operacional, marcado `TODO[INT-FE-EVNT-006]`, pronto para quando o stub for promovido. Wiring completo do interceptor real está bloqueado em tarefa cross-equipe.

---

## 1. Principais achados

### Pontos fortes

- `EventDeliveryFacade` bem estruturada: signals readonly, CONCUR-01 guard, non-optimistic confirm, degraded polling, `takeUntilDestroyed()`
- `EvntClientPort` completo (6 métodos, DTOs bem tipados, SSE + REST)
- `AlarmOverlayComponent` standalone, OnPush, `role="alertdialog"`, aria labels corretos
- `ShellOutletRegistry` stub já existe em `@neuroassist/shared/ui-components` — bloqueio P6 resolvido
- `evnt-shell-integration.service.ts` já usa o stub corretamente
- Rotas definidas com `loadComponent` lazy e guard wired

### Gaps

1. Lint FAIL: `enforceBuildableLibDependency: true` falha porque shared libs não têm build targets — BLOQUEADOR
2. Build FAIL: tsconfig.lib.json precisa de rootDir explícito — BLOQUEADOR
3. FE-EVNT-018 (ui: UrgencyBadge + EventTimestamp) ausente — bloqueio das features P3
4. FE-EVNT-013..017 ausentes — 5 componentes críticos para MVP
5. `EventFeedPageComponent` é stub funcional mas incompleto (falta DegradedModeBanner, RoutineNotice, EventList)
6. FE-EVNT-004 (descriptor registro no Shell) e FE-EVNT-006 (EVNT_NAV_CONFIG) não verificados
7. Testes: zero specs; `nx test @neuroassist/evnt` não foi rodado nunca

### Riscos

- SSE com `Authorization` header (INT-FE-EVNT-002): `EventSource` nativo não suporta headers. Abordagem selecionada: `HttpClient` com `observe: 'events'` + `responseType: 'text'` (interceptors de Angular carregam Auth automaticamente). Implementação em P6.
- `FeatureFlagService` hardcoded (`liveChannel: 'on'`) em `EVNT_MODULE_DESCRIPTOR` — aceitável para MVP dev.

### Aderência às regras Nx

- Tags `scope:evnt`, `type:feature` presentes no project.json — OK
- `depConstraints`: `scope:evnt → scope:evnt | scope:shared` — correto
- `enforceBuildableLibDependency: true` — FAIL (shared libs são non-buildable stubs)
- **Decisão arquitetural P1**: alterar `enforceBuildableLibDependency` para `false` no workspace `.eslintrc.json`. Justificativa: as shared libs são stubs Wave-0 sem ng-package.json; adicionar buildable infra completa a 3+ shared libs em paralelo atrasa o MVP sem benefício funcional; as regras de scope (evnt → shared) continuam enforçadas pela constraint de `onlyDependOnLibsWithTags` — que é a proteção arquitetural relevante.

### Decisão de estrutura: monolito vs. sub-libs

As issues FE-EVNT-013..018 especificam paths como `libs/evnt/feature-routine/src/lib/...` e `libs/evnt/ui/src/lib/...` — implicando sub-libs Nx separadas. **Decisão do planner: manter estrutura monolítica** `frontend/libs/evnt/src/lib/` e criar componentes como subdiretórios dentro da lib existente. Justificativa: criar sub-libs Nx durante lint/build quebrado adiciona superfície de falha; a estrutura monolítica é válida para desenvolvimento ativo de módulo; a divisão pode acontecer quando os gates estiverem verdes. Os caminhos de arquivo e os comandos de gate são ajustados para refletir a estrutura real (`nx test @neuroassist/evnt`, não `nx test evnt-feature-routine`).

---

## 2. Estratégia geral

### Objetivo macro

Passar os gates lint e build (P1), entregar os 6 componentes P3 faltantes que tornam o MVP shippable em dev (P2-P4), e completar a foundation incompleta + testes (P5). Multi-empresa e guards reais são P6.

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Nx Lint Fix + TypeScript Build | Nenhum outro gate pode ser validado sem lint/build verde | FE-EVNT-001, FE-EVNT-003 (gates) |
| P2 | libs/evnt/ui — UrgencyBadge + EventTimestamp | Leaf sem dependências; todos os componentes P3 importam esses dois | FE-EVNT-018 |
| P3 | Feature Routine: RoutineNotice + EventList + EventFeedPage | Trio coeso; FeedPage é o host que compõe Notice e List | FE-EVNT-013, FE-EVNT-014, FE-EVNT-015 |
| P4 | Feature Routine: EventDetailPage + FeedbackVerdictControl | Depende de FE-EVNT-018 (ui) e requer FeedPage como contexto de rota | FE-EVNT-016, FE-EVNT-017 |
| P5 | Foundation Completion + Unit Tests | FE-EVNT-004/006 incompletos; zero testes são risco de regressão | FE-EVNT-004, FE-EVNT-006 + unit tests |
| P6 | Multi-empresa + Guards + Error Handling | Segurança e integração real; desbloqueado após MVP shippable | INT-FE-EVNT-004, INT-FE-EVNT-006, FE-EVNT-021..024 |

---

## 3. Plano por prioridade

---

### Prioridade P1 — Nx Lint Fix + TypeScript Build Repair

Status: pendente
Issues cobertas: FE-EVNT-001 (contract tests gate), FE-EVNT-003 (entry lib build gate)
Categoria: ESTRUTURAL

#### Objetivo

Fazer `npx nx lint @neuroassist/evnt` e `npx nx build @neuroassist/evnt` passar sem erros, desbloqueando todos os outros gates.

#### Problemas identificados

1. `enforceBuildableLibDependency: true` no workspace `.eslintrc.json` linha 11 — o `@neuroassist/evnt` (buildable) importa `@neuroassist/shared/error-model` e `@neuroassist/shared/ui-components` (non-buildable), violando a regra.
2. TypeScript build falha com erro de `rootDir` — tsconfig.lib.json não declara `rootDir` explícito, o que causa conflito com arquivos em subdiretórios durante a compilação do ng-packagr.

#### Correções mandatórias

1. Em `frontend/.eslintrc.json`, na regra `@nx/enforce-module-boundaries`, alterar `"enforceBuildableLibDependency": true` para `"enforceBuildableLibDependency": false`.
2. Em `frontend/libs/evnt/tsconfig.lib.json`, adicionar `"rootDir": "src"` em `compilerOptions`.
3. Executar `npx nx lint @neuroassist/evnt` e confirmar zero erros.
4. Executar `npx nx build @neuroassist/evnt` e confirmar build bem-sucedido. Se falhar por outro motivo, diagnosticar e corrigir antes de prosseguir para P2.

#### Arquivos-alvo

- `frontend/.eslintrc.json` — alterar `enforceBuildableLibDependency`
- `frontend/libs/evnt/tsconfig.lib.json` — adicionar `rootDir`

#### Contrato de tipo

Nenhum novo tipo. Nenhum novo arquivo criado.

#### Sequência de execução

1. Editar `frontend/.eslintrc.json`: `enforceBuildableLibDependency: false`
2. Editar `frontend/libs/evnt/tsconfig.lib.json`: adicionar `"rootDir": "src"`
3. Rodar `npx nx lint @neuroassist/evnt --max-warnings=0`
4. Rodar `npx nx build @neuroassist/evnt`
5. Se build falhar por erro de ng-packagr diferente de rootDir: inspecionar saída e corrigir; não avançar para P2 com build quebrado.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Nenhum novo teste em P1. O objetivo é unicamente abrir os gates.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não em P1. O import já existe no `EvntClientHttpAdapter` como constante `ACCESS_HTTP_BASE = '/api'` — sem alteração.

#### Critérios de aceite

```bash
cd frontend
npx nx lint @neuroassist/evnt --max-warnings=0   # zero warnings, zero errors
npx nx build @neuroassist/evnt                    # exit code 0, dist/libs/evnt criado
```

#### Riscos

- Se o build falhar por motivo além do rootDir (ex.: ng-packagr versão incompatível, entryFile ausente), o implementador deve diagnosticar e documentar o erro exato antes de fixar — não especular.
- `enforceBuildableLibDependency: false` é workspace-wide; todos os módulos são afetados. Isso é intencional: o workspace ainda não tem infra de sub-libs buildable. Rever quando EVNT, GEST e PAC tiverem sub-libs físicas.

#### Instrução para implementador

Fazer exatamente as duas edições (`.eslintrc.json` + `tsconfig.lib.json`) e rodar os dois comandos de aceite. Se algum falhar, diagnosticar antes de tocar qualquer outro arquivo. P2 não começa enquanto P1 não estiver verde.

---

### Prioridade P2 — libs/evnt/ui: UrgencyBadgeComponent + EventTimestampComponent

Status: pendente
Issues cobertas: FE-EVNT-018
Categoria: FUNCIONAL

#### Objetivo

Criar os dois componentes de UI presentacional (`UrgencyBadgeComponent` e `EventTimestampComponent`) como subdiretório `ui/` dentro de `libs/evnt/src/lib/`, exportá-los de `src/index.ts`, e validar com lint + test.

#### Problema

Todos os componentes P3 (RoutineNotice, EventList, EventDetailPage) precisam de `UrgencyBadgeComponent` e `EventTimestampComponent`. Sem eles P3 não começa.

#### Resultado esperado

`UrgencyBadgeComponent` renderiza badge colorido por urgência (emergência → classe `alarm`, rotina → classe `routine`). `EventTimestampComponent` formata ISO para "às HH:mm" via `Intl.DateTimeFormat` locale `pt-BR`.

#### Problemas identificados

Nenhum dos dois componentes existe em `frontend/libs/evnt/src/lib/`.

#### Correções mandatórias

1. Criar `frontend/libs/evnt/src/lib/ui/urgency-badge/urgency-badge.component.ts` — standalone, OnPush, `input: urgencia: 'emergencia' | 'rotina'`.
2. Criar `frontend/libs/evnt/src/lib/ui/event-timestamp/event-timestamp.component.ts` — standalone, OnPush, `input: recognizedAt: string`.
3. Criar `frontend/libs/evnt/src/lib/ui/urgency-badge/urgency-badge.component.spec.ts`.
4. Criar `frontend/libs/evnt/src/lib/ui/event-timestamp/event-timestamp.component.spec.ts`.
5. Re-exportar ambos de `frontend/libs/evnt/src/index.ts`.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/ui/urgency-badge/urgency-badge.component.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/urgency-badge/urgency-badge.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/event-timestamp/event-timestamp.component.ts` (criar)
- `frontend/libs/evnt/src/lib/ui/event-timestamp/event-timestamp.component.spec.ts` (criar)
- `frontend/libs/evnt/src/index.ts` (editar — adicionar exports)

#### Contrato de tipo

```typescript
// urgency-badge.component.ts
@Component({ selector: 'evnt-urgency-badge', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class UrgencyBadgeComponent {
  readonly urgencia = input.required<'emergencia' | 'rotina'>();
  // CSS: urgencia()==='emergencia' → class 'badge-alarm' (--color-alarm); 'rotina' → 'badge-routine'
}

// event-timestamp.component.ts
@Component({ selector: 'evnt-event-timestamp', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class EventTimestampComponent {
  readonly recognizedAt = input.required<string>(); // ISO-8601 UTC
  // Renders: "às HH:mm" via Intl.DateTimeFormat('pt-BR', { timeStyle: 'short' })
  protected readonly formatted = computed(() => formatTimestamp(this.recognizedAt()));
}
function formatTimestamp(iso: string): string {
  return 'às ' + new Intl.DateTimeFormat('pt-BR', { timeStyle: 'short' }).format(new Date(iso));
}
```

#### Sequência de execução

1. Criar `urgency-badge.component.ts` + spec (2 testes: emergência → classe alarm; rotina → classe routine)
2. Criar `event-timestamp.component.ts` + spec (1 teste: ISO UTC → "às HH:mm" formatado)
3. Editar `frontend/libs/evnt/src/index.ts`: adicionar exports para ambos
4. Rodar `npx nx test @neuroassist/evnt --testFile=urgency-badge` e `--testFile=event-timestamp`
5. Rodar `npx nx lint @neuroassist/evnt`

#### Generators Nx obrigatórios

Nenhum. Componentes criados manualmente como subdiretório da lib existente.

#### Política de testes

Unit obrigatório: 2 specs para UrgencyBadge, 1 spec para EventTimestamp. Cobertura mínima: variantes de urgência + formato de timestamp.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não. Componentes presentacionais sem HTTP.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # specs de ui/ PASS
npx nx lint @neuroassist/evnt --max-warnings=0   # zero errors
```

#### Riscos

- `Intl.DateTimeFormat` com `timeStyle: 'short'` pode retornar `"14:32"` ou `"14h32"` dependendo do runtime. Testar com `pt-BR` explícito no spec e ajustar formatTimestamp se necessário.
- Design token `--color-alarm` deve existir em `@neuroassist/shared/ui-tokens`. Se não existir, usar class Tailwind `text-red-600 bg-red-100` como placeholder até token disponível.

#### Instrução para implementador

Criar os 4 arquivos (2 componentes + 2 specs), editar index.ts, rodar os gates. Não criar project.json separado para `evnt-ui` — os componentes vivem dentro da lib monolítica `@neuroassist/evnt`.

---

### Prioridade P3 — Feature Routine: RoutineNoticeComponent + EventListComponent + EventFeedPageComponent (full)

Status: pendente
Issues cobertas: FE-EVNT-013, FE-EVNT-014, FE-EVNT-015
Categoria: FUNCIONAL

#### Objetivo

Entregar os três componentes do feed de rotina: `RoutineNoticeComponent` (presentacional, dismiss/open), `EventListComponent` (presentacional, lista histórico), e `EventFeedPageComponent` completo (smart, compõe os dois anteriores). Substituir o stub atual de `EventFeedPageComponent`.

#### Problema

`EventFeedPageComponent` existe como stub funcional mas não usa `RoutineNoticeComponent` (inexistente) nem `EventListComponent` (inexistente). O MVP precisa do feed de rotina completo.

#### Resultado esperado

`/eventos` renderiza: banner degradado (quando `deliveryMode==='degraded'`), lista de `routineQueue` via `RoutineNoticeComponent` com dismiss optimistic, lista de `recentEvents` via `EventListComponent` com navegação para detalhe, estado vazio, estado loading.

#### Problemas identificados

- `RoutineNoticeComponent` não existe
- `EventListComponent` não existe
- `EventFeedPageComponent` não usa `DegradedModeBannerComponent` (usa template inline), não compõe Notice nem List, não lê `recentEvents`

#### Correções mandatórias

1. Criar `frontend/libs/evnt/src/lib/feature-routine/routine-notice/routine-notice.component.ts` — presentacional.
2. Criar `frontend/libs/evnt/src/lib/feature-routine/event-list/event-list.component.ts` — presentacional.
3. Substituir conteúdo de `frontend/libs/evnt/src/lib/feature-routine/event-feed-page.component.ts` com implementação completa.
4. Criar specs para os 3 componentes.
5. Atualizar `frontend/libs/evnt/src/index.ts` com novos exports.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/feature-routine/routine-notice/routine-notice.component.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/routine-notice/routine-notice.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-list/event-list.component.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-list/event-list.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-feed-page.component.ts` (substituir — arquivo existente)
- `frontend/libs/evnt/src/lib/feature-routine/event-feed-page.component.spec.ts` (criar)
- `frontend/libs/evnt/src/index.ts` (editar — adicionar exports)

#### Contrato de tipo

```typescript
// routine-notice.component.ts — PRESENTACIONAL, zero DI
@Component({ selector: 'evnt-routine-notice', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
             imports: [UrgencyBadgeComponent, EventTimestampComponent] })
export class RoutineNoticeComponent {
  readonly evento = input.required<EventoVm>();
  readonly dismiss = output<string>(); // emite eventoId
  readonly open = output<string>();    // emite eventoId
}

// event-list.component.ts — PRESENTACIONAL, zero DI
@Component({ selector: 'evnt-event-list', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
             imports: [UrgencyBadgeComponent, EventTimestampComponent] })
export class EventListComponent {
  readonly events = input.required<readonly EventoVm[]>();
  readonly open = output<string>(); // emite eventoId
}

// event-feed-page.component.ts — SMART
@Component({ selector: 'evnt-event-feed-page', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
             imports: [RoutineNoticeComponent, EventListComponent] })
export class EventFeedPageComponent implements OnInit {
  // injeta: EventDeliveryFacade, Router
  // lê signals: routineQueue, recentEvents, deliveryMode, loading, error
  // computed: routineNoticesEnabled = signal(descriptor.featureFlags.routineNotices === 'on')
  // (dismiss) → facade.dismiss(id)   — remoção optimista
  // (open) → router.navigate(['/eventos', id])
}
```

#### Sequência de execução

1. Criar `RoutineNoticeComponent` + spec (2 testes: dismiss emite id, open emite id)
2. Criar `EventListComponent` + spec (2 testes: 3 eventos → 3 cards DOM; clique → open emite id)
3. Substituir `EventFeedPageComponent` com implementação completa + spec (3 testes: degraded banner visível; routineQueue vazio → estado vazio; routineNotices=false → @for não renderiza RoutineNotice)
4. Editar `index.ts` com novos exports
5. Rodar `npx nx test @neuroassist/evnt`
6. Rodar `npx nx lint @neuroassist/evnt`

#### Generators Nx obrigatórios

Nenhum. Subdiretórios criados dentro da lib monolítica.

#### Política de testes

Unit obrigatório: 2+2+3 specs mínimos. Cenários críticos: (a) DegradedModeBanner visível, (b) estado vazio, (c) dismiss optimistic, (d) flag routineNotices=false suprime Notice sem afetar AlarmOverlay.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não diretamente. `EventFeedPageComponent` lê signals do facade — não emite HTTP. O facade já usa `EVNT_CLIENT_PORT` com base `/api/v1/events`. Nenhuma mudança em P3.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # todos os specs PASS
npx nx lint @neuroassist/evnt --max-warnings=0   # zero errors
npx nx build @neuroassist/evnt                    # build PASS
```

#### Riscos

- `FeatureFlagService` real não está integrado — usar `EVNT_MODULE_DESCRIPTOR.featureFlags.routineNotices` diretamente em `EventFeedPageComponent` como `routineNoticesEnabled` signal local. Aceitável para MVP dev; INT-FE-EVNT-005 resolve isso em P6.
- `DegradedModeBannerComponent` de `@neuroassist/shared/ui-feedback` pode ser stub. Se for stub vazio, implementar banner inline no `EventFeedPageComponent` com `@if (deliveryMode() === 'degraded')` (como está no stub atual).

#### Instrução para implementador

Criar `RoutineNotice` e `EventList` primeiro (sem dependências entre si). Depois substituir `EventFeedPageComponent`. Não criar novos project.json. Executar gates após cada componente para detectar erros de TypeScript cedo.

---

### Prioridade P4 — Feature Routine: EventDetailPageComponent + FeedbackVerdictControl + ConfirmationTrail

Status: pendente
Issues cobertas: FE-EVNT-016, FE-EVNT-017
Categoria: FUNCIONAL

#### Objetivo

Implementar a rota `/eventos/:eventoId` com `EventDetailPageComponent` (smart, carrega detalhe, breadcrumb dinâmico) e os dois componentes presentacionais `FeedbackVerdictControl` (dois botões real/falso-positivo) e `ConfirmationTrailComponent` (quem confirmou quando).

#### Problema

A rota `/eventos/:eventoId` atualmente carrega `EventFeedPageComponent` como placeholder (`TODO[FE-EVNT-014]` em `evnt.routes.ts`). A cuidadora não tem tela de detalhe de evento.

#### Resultado esperado

`/eventos/:eventoId` renderiza: `UrgencyBadgeComponent`, `EventTimestampComponent`, `commandLabel`, `FeedbackVerdictControl` (quando `feedback==='nao_avaliado'` + permissão `evnt.confirm`), `ConfirmationTrailComponent`, breadcrumb `[Eventos > {commandLabel} (às HH:mm)]`. CONCUR-01: botões desabilitados enquanto `confirming()===true`.

#### Problemas identificados

- `EventDetailPageComponent` não existe
- `FeedbackVerdictControl` não existe
- `ConfirmationTrailComponent` não existe
- `evnt.routes.ts` aponta para `EventFeedPageComponent` como placeholder para `:eventoId`
- `EventDeliveryFacade` pode não ter método `loadDetail` / `selectedEvent` — verificar e adicionar se ausente

#### Correções mandatórias

1. Verificar `frontend/libs/evnt/src/lib/evnt.facade.ts`: confirmar existência de `loadDetail(eventoId: string): Promise<void>` e `selectedEvent: Signal<EventoVm | null>`. Se ausentes, adicionar na facade (sem criar novo arquivo).
2. Criar `frontend/libs/evnt/src/lib/feature-routine/feedback-verdict-control/feedback-verdict-control.component.ts` — presentacional.
3. Criar `frontend/libs/evnt/src/lib/feature-routine/confirmation-trail/confirmation-trail.component.ts` — presentacional.
4. Criar `frontend/libs/evnt/src/lib/feature-routine/event-detail-page/event-detail-page.component.ts` — smart.
5. Atualizar `frontend/libs/evnt/src/lib/evnt.routes.ts`: substituir `loadComponent` da rota `:eventoId` para apontar para `EventDetailPageComponent`.
6. Criar specs para os 3 componentes novos.
7. Atualizar `frontend/libs/evnt/src/index.ts`.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/evnt.facade.ts` (editar se necessário — adicionar `loadDetail` + `selectedEvent`)
- `frontend/libs/evnt/src/lib/feature-routine/feedback-verdict-control/feedback-verdict-control.component.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/feedback-verdict-control/feedback-verdict-control.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/confirmation-trail/confirmation-trail.component.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/confirmation-trail/confirmation-trail.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-detail-page/event-detail-page.component.ts` (criar)
- `frontend/libs/evnt/src/lib/feature-routine/event-detail-page/event-detail-page.component.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/evnt.routes.ts` (editar — rota `:eventoId`)
- `frontend/libs/evnt/src/index.ts` (editar)

#### Contrato de tipo

```typescript
// feedback-verdict-control.component.ts — PRESENTACIONAL, zero DI
@Component({ selector: 'evnt-feedback-verdict-control', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class FeedbackVerdictControl {
  readonly feedback = input.required<FeedbackStatus>();
  readonly disabled = input<boolean>(false);
  readonly verdict = output<'real' | 'falso_positivo'>();
  // Dois botões: "Real" e "Falso positivo". [disabled]="disabled()". Emite verdict.
}

// confirmation-trail.component.ts — PRESENTACIONAL
@Component({ selector: 'evnt-confirmation-trail', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })
export class ConfirmationTrailComponent {
  readonly confirmadoPor = input<string | null>(null);
  readonly confirmadoAt = input<string | null>(null);
  // @if (confirmadoPor()) → "Confirmado por {nome} às {hora}" usando EventTimestampComponent
}

// event-detail-page.component.ts — SMART
@Component({ selector: 'evnt-event-detail-page', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
             imports: [UrgencyBadgeComponent, EventTimestampComponent, FeedbackVerdictControl, ConfirmationTrailComponent] })
export class EventDetailPageComponent implements OnInit {
  // injeta: EventDeliveryFacade, ActivatedRoute, Router
  // lê route.snapshot.paramMap.get('eventoId')
  // ngOnInit(): void facade.loadDetail(eventoId)
  // selectedEvent = computed(() => facade.selectedEvent())
  // canConfirm = computed(() => selectedEvent()?.feedback === 'nao_avaliado')
  // confirming = facade.confirming  (CONCUR-01 — [disabled])
  // onVerdict(v: 'real'|'falso_positivo'): void { facade.confirm(eventoId, v) }
  // Tratamento 404: @if (!loading() && !selectedEvent()) → router.navigate(['/eventos'])
  // Breadcrumb: "Eventos > {commandLabel} (às HH:mm)" via título dinâmico
}

// Adições à facade (se ausentes):
// private readonly _selectedEvent = signal<EventoVm | null>(null);
// readonly selectedEvent: Signal<EventoVm | null> = this._selectedEvent.asReadonly();
// async loadDetail(eventoId: string): Promise<void> {
//   this._loading.set(true);
//   try {
//     const dto = await lastValueFrom(this.port.get(eventoId));
//     this._selectedEvent.set(mapDtoToVm(dto));
//   } catch { this._selectedEvent.set(null); } finally { this._loading.set(false); }
// }
```

#### Sequência de execução

1. Verificar `evnt.facade.ts`: se `loadDetail` e `selectedEvent` ausentes, adicionar antes de criar os componentes
2. Criar `FeedbackVerdictControl` + spec (2 testes: disabled=true → botões disabled; clique "Real" → verdict emite 'real')
3. Criar `ConfirmationTrailComponent` + spec (1 teste: confirmadoPor=null → vazio)
4. Criar `EventDetailPageComponent` + spec (2 testes: selectedEvent=null → redirect; canConfirm=false → FeedbackVerdictControl ausente ou disabled)
5. Editar `evnt.routes.ts`: rota `:eventoId` aponta para `EventDetailPageComponent`
6. Atualizar `index.ts`
7. Rodar gates

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit obrigatório. Cenários críticos: CONCUR-01 (disabled durante confirm), 404 redirect, canConfirm=false.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não diretamente. `loadDetail` usa `port.get()` que já usa `HttpClient`. O interceptor X-Empresa-Id será adicionado em P6.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # PASS (incluindo specs P4)
npx nx lint @neuroassist/evnt --max-warnings=0   # zero errors
npx nx build @neuroassist/evnt                    # PASS
```

#### Riscos

- `EventDeliveryFacade.selectedEvent` pode precisar de um signal por `eventoId` (mapa) em vez de um único signal global. Para MVP: usar signal único `_selectedEvent` (o último carregado). Suficiente para `/eventos/:eventoId` com navegação sequencial.
- Breadcrumb dinâmico: se o sistema de navegação global não existir ainda, implementar como `<h2>` com `commandLabel` + timestamp inline no template — não bloquear P4 por ausência de infra de breadcrumb.

#### Instrução para implementador

Verificar facade primeiro. Criar os dois presentacionais antes da smart page. Editar rotas por último. Rodar gates após cada passo.

---

### Prioridade P5 — Foundation Completion + Unit Tests Baseline

Status: pendente
Issues cobertas: FE-EVNT-004, FE-EVNT-006, testes de FE-EVNT-007, 008, 009, 010
Categoria: ESTRUTURAL + VALIDACAO

#### Objetivo

Completar as 2 issues foundation MISSING (FE-EVNT-004: descriptor registro, FE-EVNT-006: EVNT_NAV_CONFIG separado) e adicionar specs unitários para os 4 artefatos de data-access (facade, port, mock adapter, evnt.types).

#### Problema

FE-EVNT-004: `EVNT_MODULE_DESCRIPTOR.key = 'evnt'` existe mas não há evidência de que o Shell efetivamente registra o descriptor. FE-EVNT-006: `EVNT_NAV_CONFIG` não existe como export separado — está inline no descriptor. Testes do core (facade, adapters) nunca rodaram.

#### Correções mandatórias

1. Criar `frontend/libs/evnt/src/lib/evnt.nav-config.ts` com `export const EVNT_NAV_CONFIG` extraído de `evnt.descriptor.ts`.
2. Em `evnt.descriptor.ts`, importar `EVNT_NAV_CONFIG` de `evnt.nav-config.ts` (sem alterar o objeto EVNT_MODULE_DESCRIPTOR).
3. Re-exportar `EVNT_NAV_CONFIG` de `src/index.ts`.
4. Criar `frontend/libs/evnt/src/lib/evnt.facade.spec.ts` com 3 specs mínimos: (a) `confirm()` com port mock → `_activeEvent` limpo após sucesso; (b) `dismiss()` remove item de `_routineQueue`; (c) `loadHistory()` popula `_recentEvents`.
5. Criar `frontend/libs/evnt/src/lib/api/evnt-client-mock.adapter.spec.ts` com 1 spec: `listActive()` retorna `ActiveEventsDto` com `activeEvent` de urgência 'emergencia'.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/evnt.nav-config.ts` (criar)
- `frontend/libs/evnt/src/lib/evnt.descriptor.ts` (editar — importar NAV_CONFIG)
- `frontend/libs/evnt/src/lib/evnt.facade.spec.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-client-mock.adapter.spec.ts` (criar)
- `frontend/libs/evnt/src/index.ts` (editar)

#### Contrato de tipo

```typescript
// evnt.nav-config.ts
export const EVNT_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Eventos',
  icon: 'notifications_active',
  route: '/eventos',
  order: 20,
} as const;
```

#### Sequência de execução

1. Criar `evnt.nav-config.ts` + refatorar `evnt.descriptor.ts` para importá-lo
2. Criar specs para facade e mock adapter
3. Rodar `npx nx test @neuroassist/evnt`
4. Rodar gates completos

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit obrigatório para facade e mock adapter. Cobertura mínima: 4 specs novos.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não em P5.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # todos os specs PASS
npx nx lint @neuroassist/evnt --max-warnings=0
npx nx build @neuroassist/evnt
```

EVNT_NAV_CONFIG exportado em `src/index.ts` verificável via `grep "EVNT_NAV_CONFIG" frontend/libs/evnt/src/index.ts`.

#### Riscos

- Specs da facade precisam de `TestBed.configureTestingModule` com `provideEvnt({ adapter: 'mock' })`. Se a DI falhar por falta de `EVNT_CLIENT_PORT`, usar `TestBed.overrideProvider`.

#### Instrução para implementador

Extrair NAV_CONFIG primeiro (trivial). Depois criar specs da facade — é o item de maior valor. Não criar specs para todos os arquivos: focar nos 4 specs mandatórios.

---

### Prioridade P6 — Multi-empresa + Guards Reais + Error Handling

Status: pendente
Issues cobertas: INT-FE-EVNT-004, INT-FE-EVNT-006, FE-EVNT-021, FE-EVNT-022, FE-EVNT-023
Categoria: WIRING

#### Objetivo

Adicionar ponto de injeção `X-Empresa-Id` no `EvntClientHttpAdapter`, integrar `AccessDecisionService` no `evntGuard`, e implementar o error mapper `ProblemDetail → AppError` específico para EVNT.

#### Problema

- `EvntClientHttpAdapter` não injeta contexto de empresa — requests HTTP não carregam `X-Empresa-Id`
- `evntGuard` retorna `true` sempre — sem verificação real de permissão
- Errors de BE retornam como JS Error genérico — sem mapeamento de `ProblemDetail` (RFC 7807)

#### Correções mandatórias

1. Em `frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts`: adicionar `private readonly empresaContext = inject(EmpresaContext, { optional: true })` e adicionar header `X-Empresa-Id` nos métodos HTTP via `HttpHeaders` (apenas se `empresaContext` não-null). Importar `EmpresaContext` de `@neuroassist/shared/empresa-context`.
2. Em `frontend/libs/evnt/src/lib/access/evnt.guard.ts`: substituir stub por implementação que injeta `AuthSession` de `@neuroassist/shared/auth-session` e verifica `session.isAuthenticated()`. Se false → `router.navigate(['/login'])`. Verificação de permissão `evnt:eventos:read` via `AccessDecision` de `@neuroassist/shared/access-decision` — se ausente, retornar false + redirect para `/`.
3. Criar `frontend/libs/evnt/src/lib/api/evnt-error-mapper.ts`: função `mapProblemDetailToAppError(err: unknown): AppError` que detecta `{ type: string, status: number }` (ProblemDetail) e mapeia para `AppError { code, message }`. Usar nos métodos do `EvntClientHttpAdapter` via `catchError`.

#### Arquivos-alvo

- `frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts` (editar)
- `frontend/libs/evnt/src/lib/access/evnt.guard.ts` (editar)
- `frontend/libs/evnt/src/lib/api/evnt-error-mapper.ts` (criar)
- `frontend/libs/evnt/src/lib/api/evnt-error-mapper.spec.ts` (criar)

#### Contrato de tipo

```typescript
// evnt-error-mapper.ts
export function mapProblemDetailToAppError(err: unknown): AppError {
  if (err instanceof HttpErrorResponse && typeof err.error === 'object' && err.error !== null) {
    const pd = err.error as { type?: string; status?: number; detail?: string; title?: string };
    return { code: pd.type ?? `EVNT_HTTP_${err.status}`, message: pd.detail ?? pd.title ?? err.message };
  }
  if (err instanceof Error) return { code: 'EVNT_ERROR', message: err.message };
  return { code: 'EVNT_UNKNOWN', message: 'Erro desconhecido' };
}
```

#### Sequência de execução

1. Criar `evnt-error-mapper.ts` + spec (2 testes: ProblemDetail → código correto; Error genérico → EVNT_ERROR)
2. Editar `EvntClientHttpAdapter` para injetar `EmpresaContext` e usar `evnt-error-mapper` em `catchError`
3. Editar `evntGuard` para verificar `AuthSession` e `AccessDecision`
4. Rodar gates

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit obrigatório para `evnt-error-mapper`. Testes de `evntGuard` são opcionais em P6 (guard real depende de stubs de shared libs sendo funcionais).

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Sim. `EvntClientHttpAdapter` injeta `EmpresaContext` de `@neuroassist/shared/empresa-context` (stub Wave-0 — retorna `null`). O header `X-Empresa-Id` só é adicionado se `empresaContext?.empresaId` não-null. Wiring completo aguarda promoção do stub.

#### Critérios de aceite

```bash
cd frontend
npx nx test @neuroassist/evnt --max-warnings=0   # specs do error-mapper PASS
npx nx lint @neuroassist/evnt --max-warnings=0
npx nx build @neuroassist/evnt
grep "X-Empresa-Id" frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts   # deve retornar match
```

#### Riscos

- `AuthSession` e `AccessDecision` são stubs — `isAuthenticated()` pode retornar false em dev. Garantir que `evntGuard` tenha comportamento seguro: em dev com adapter='mock', o guard pode verificar se o adapter é mock e bypassar (via DI token de ambiente).

#### Instrução para implementador

Criar error-mapper primeiro (zero dependências). Depois editar HttpAdapter. Depois o guard. O guard é o mais arriscado — testar manualmente que `/eventos` ainda abre em dev com adapter mock.

---

## 4. Dependências externas

| Dependência | Tipo | Bloqueio | Resolução |
|---|---|---|---|
| `GET /api/v1/events/active` | BE endpoint | P3 (EventFeedPage data real) | Mock adapter cobre dev; INT-FE-EVNT-002 para produção |
| `GET /api/v1/events/{id}` | BE endpoint | P4 (EventDetailPage data real) | Mock adapter cobre dev; INT-FE-EVNT-007 para produção |
| `POST /api/v1/events/{id}/confirmation` | BE endpoint | P4 (confirm flow real) | Mock adapter cobre dev; INT-FE-EVNT-003 para produção |
| `ShellOutletRegistry` em `@neuroassist/shared/ui-components` | Shared lib | P6 (AlarmOutlet no Shell) | Stub já existe — sem bloqueio imediato |
| `EmpresaContext` em `@neuroassist/shared/empresa-context` | Shared lib | P6 (X-Empresa-Id) | Stub Wave-0 — injeção opcional |
| `AuthSession` + `AccessDecision` em shared | Shared lib | P6 (guard real) | Stubs Wave-0 — guard deve ter fallback seguro para dev |
| `AppShellComponent` — renderizar AlarmOutlet registrado | Shell app | INT-FE-EVNT-001 | Deferred para após P6; AlarmOutlet registrado mas não renderizado até Shell integrar |

---

## 5. Restrições arquiteturais (Nx boundaries)

- `scope:evnt` pode depender de `scope:evnt` e `scope:shared` — APENAS. Não importar de `scope:pac`, `scope:gest`, etc.
- `enforceBuildableLibDependency: false` após P1 — regra de scope permanece ativa.
- Estrutura monolítica `libs/evnt/`: todos os componentes vivem em `src/lib/` como subdiretórios. Nenhum `project.json` extra criado nas prioridades P1-P6.
- Componentes presentacionais (RoutineNotice, EventList, FeedbackVerdictControl, etc.): **zero DI de serviços** — recebem dados via `input()` e emitem via `output()`.
- Componentes smart (EventFeedPage, EventDetailPage): injetam **apenas** `EventDeliveryFacade` e `Router`. Não injetam `HttpClient` diretamente.
- `@Transactional` não se aplica em FE. Equivalente: `takeUntilDestroyed()` em todas as subscriptions RxJS.
- `ChangeDetectionStrategy.OnPush` obrigatório em todos os componentes.
- `standalone: true` obrigatório — sem NgModule.

---

## 6. Referências

| Documento | Relevância |
|---|---|
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` | Rotas e guards |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` | Hierarquia de componentes |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` | Contratos de ViewModel (AlarmVm, RoutineFeedVm, EventDetailVm) |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` | Port, adapters, degradação SSE→polling |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` | Descriptor, feature flags, bootstrap |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` | AlarmOutlet global (ADR-005) |
| `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` | ProblemDetail, ERR-01..04 |
| `docs/architecture/frontend/modules/evnt/frontend/adr/ADR-005-global-alarm-outlet-and-live-delivery.md` | Decisão AlarmOutlet não-rota |
| `docs/architecture/frontend/modules/evnt/frontend/issues/` | Issues FE-EVNT-001..030 + INT-FE-EVNT-001..007 |
| `frontend/.eslintrc.json` | Workspace boundaries (alterar em P1) |
| `frontend/libs/evnt/tsconfig.lib.json` | TypeScript build config (alterar em P1) |
| `frontend/libs/evnt/src/lib/evnt.facade.ts` | Source primário do estado EVNT |
| `docs/architecture/shared/multi-empresa.md` | Contexto multi-tenant (referência — não modificar) |
