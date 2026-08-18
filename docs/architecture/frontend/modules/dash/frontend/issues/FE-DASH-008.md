## [FE-DASH-008] DashStreamClient — SSE EventSource + heartbeat + reconnect + long-poll fallback

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/data-access/src/lib/dash-stream.client.ts` abre/mantém `EventSource` para `/panel/stream`, parseia eventos (`notification`, `heartbeat`, `catchup-complete`), reconecta com backoff jittered e cai em long-poll (`/panel/notifications/poll`) após esgotar tentativas.

**Context / Problem statement:**
- O SSE é o canal primário de entrega de notificações em tempo real (SPEC-04 §4).
- Heartbeat perdido deve disparar reconexão limitada; após N falhas → fallback long-poll.
- A feature-flag `dash.sseStream` (SPEC-05 §3) controla se SSE está ativo ou se long-poll é usado direto.
- Fonte: SPEC-04 §4; SPEC-05 §3; SPEC-07 §4.
- Bloqueia: FE-DASH-007

**Approach (execution path):**
1. Criar `DashStreamClient` com `@Injectable({ providedIn: 'root' })`.
2. Método `open()`: verifica feature-flag `dash.sseStream`; se on, instancia `EventSource('/panel/stream', { withCredentials: true })`.
3. Listener `onmessage`: parseia payload JSON → emite em `events$: Subject<NotificationEnvelope>`.
4. Listener `heartbeat`: reinicia timer de watchdog (ex: 35s).
5. Listener `catchup-complete`: sinaliza que o replay on-connect terminou.
6. Watchdog: se heartbeat não chegar em `HEARTBEAT_TIMEOUT` ms, fecha e reabre com backoff jittered (ex: 1s × 2^n + random 0..1s, máx 5 tentativas).
7. Após 5 tentativas sem sucesso → ativar long-poll via `DashApiService.pollNotifications()` em loop.
8. Método `close()`: fecha `EventSource`, cancela timers, completa `events$`.
9. Feature-flag off: pular SSE e iniciar direto no long-poll.

**Non-goals:**
- Não implementar o parsing de ViewModel (responsabilidade da NotificationsFacade).
- Não implementar autenticação (JWT via interceptor compartilhado).

**Layer(s):**
frontend / libs/dash/data-access (type:data-access)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§4)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-stream.client.ts`
- 🔲 `libs/dash/data-access/src/lib/dash-stream.client.spec.ts`
- 🔲 `libs/dash/data-access/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (mock EventSource; testar reconnect após heartbeat timeout; fallback long-poll após N tentativas)
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
- `close()` fecha o EventSource e não emite mais eventos
- Após 5 reconexões sem heartbeat, o cliente ativa long-poll
- Feature-flag `dash.sseStream=off` → long-poll ativado diretamente sem tentar SSE

**Dependencies:**
- FE-DASH-005 (DashApiService — long-poll fallback)

**Data impact:**
- Nenhum (in-memory apenas)

**API / Contract impact:**
- Consome `GET /panel/stream` (SSE) e `GET /panel/notifications/poll` (fallback)

**Testing / Evidence required:**
- Test com EventSource mock (jest.fn ou fake-timers)
- Test verifica que `events$` completa quando `close()` é chamado

**Risks / Notes:**
- `EventSource` não suporta headers custom no browser nativo; JWT deve ser passado via cookie (confirmar com BE) ou via query param (menos seguro). Alinhar com DASH-005 BE.
- e2e de destroy SSE coberto em FE-DASH-029 (coverage gap G7).
