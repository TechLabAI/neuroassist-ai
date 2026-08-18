## [FE-DASH-007] NotificationsFacade — signals items/state/filter/unreadCount + writes

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/data-access/src/lib/notifications.facade.ts` expõe sinais readonly (`items`, `state`, `filter`, `unreadCount`) e métodos de UI (`setFilter`, `markRead`, `acknowledge`, `open`), consumindo `DashStreamClient` para entrega SSE e `DashApiService` para catch-up e writes.

**Context / Problem statement:**
- `NotificationCenterPageComponent` consome apenas signals; sem BehaviorSubject.
- `markRead` atualiza o sinal local imediatamente e reverte silenciosamente em caso de falha (non-critical, SPEC-03 §2).
- Erros na lista renderizam estado inline (EmptyOrErrorStateComponent) e nunca bloqueiam o Home.
- Fonte: SPEC-03 §2; SPEC-04 §1.
- Bloqueia: FE-DASH-015

**Approach (execution path):**
1. Criar `NotificationsFacade` com `@Injectable({ providedIn: 'root' })`.
2. Signals: `items = signal<readonly NotificationItemVm[]>([])`, `state = signal<LoadState>('loading')`, `filter = signal<NotificationFilterVm>({ scope: 'recent' })`.
3. `unreadCount = computed(() => this.items().filter(i => !i.read).length)`.
4. Método `setFilter(f)`: atualiza `filter` signal e re-aplica filtro sobre items locais.
5. Método `markRead(dispatchId)`: optimistic local update → `DashApiService.acknowledge()` → reverter se falhar (silenciosamente).
6. Método `acknowledge(dispatchId)`: chama `DashApiService.acknowledge(dispatchId)` — idempotente por `dispatchId`.
7. Método `open(dispatchId)`: navega para contexto do paciente via shell router.
8. Inicialização: `DashStreamClient.events$` → mapear `NotificationEnvelope → NotificationItemVm` → append a `items`.
9. Catch-up: `DashApiService.getUnacknowledged()` → inicializar `items`.
10. Expor tudo como `.asReadonly()`.

**Non-goals:**
- Não implementar SSE (DashStreamClient — FE-DASH-008).
- Não renderizar o alarm overlay (EVNT).

**Layer(s):**
frontend / libs/dash/data-access (type:data-access)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§2)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§1)

**Architecture alignment:**
- Signals-first; sem NgRx
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/notifications.facade.ts`
- 🔲 `libs/dash/data-access/src/lib/notifications.mapper.ts` (NotificationEnvelope → NotificationItemVm)
- 🔲 `libs/dash/data-access/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (testar: markRead reverte em falha; unreadCount derivado; setFilter filtra items)
- 🔲 `npx nx lint dash-data-access` PASS

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:data-access
layer:frontend
scope:dash
priority:P2

**Acceptance criteria:**
- `npx nx test dash-data-access` PASS
- `npx nx lint dash-data-access` PASS
- `npx nx build dash-data-access` PASS
- `markRead` reverte o flag `read` silenciosamente quando `acknowledge` falha
- `unreadCount` é derivado e não armazenado
- `items` inclui `full_screen_alarm` como entrada read-only (EVNT é o dono do overlay)

**Dependencies:**
- FE-DASH-005 (DashApiService)
- FE-DASH-008 (DashStreamClient — streams SSE events$)

**Data impact:**
- `notification.read` é o único estado local não-durável

**API / Contract impact:**
- Consome `GET /panel/notifications/unacknowledged` + `POST /panel/notifications/{dispatchId}/acknowledge`

**Testing / Evidence required:**
- Test cobre o rollback silencioso do markRead
- Test verifica append SSE sem re-fetch

**Risks / Notes:**
- `full_screen_alarm` tier: DASH lista read-only; nunca renderiza o overlay (invariante — EVNT é o dono).
