# DASH-FE-002 — Scaffold DashStreamClient (SSE + Long-Poll Fallback)

**Type:** Feature  
**Priority:** P1 (High)  
**Module:** DASH  
**Layer:** Frontend  
**Assignee:** PLANNER → IMPLEMENTADOR  
**Created:** 2026-08-19  
**Status:** OPEN

---

## Título
Implement real-time notification stream client (EventSource + heartbeat + long-poll fallback).

## Descrição
The DASH module requires real-time notification delivery via Server-Sent Events (SSE) with graceful fallback to polling when SSE unavailable (firewall, browser support, etc.).

**Spec Reference:** plan-FE-DASH.md P2 FE-DASH-008

### Requirements
1. **DashStreamClient service** — manages EventSource connection to GET `/panel/stream` (text/event-stream)
2. **Message types:**
   - `data: <JSON>` → parse NotificationEnvelope
   - `:heartbeat` → keep-alive ping
   - `event: close` → server-initiated disconnect
3. **Reconnect logic:** exponential backoff (2s, 4s, 8s... max 30s)
4. **Fallback to polling:** if EventSource fails, switch to GET `/panel/notifications/poll` + interval
5. **JWT auth:** EventSource cannot pass custom headers → use cookie or query param (align with BE DASH-005)
6. **Signals emitted:**
   - `notifications$: Signal<NotificationEnvelope[]>` (readonly)
   - `isConnected: Signal<boolean>`
   - `fallbackActive: Signal<boolean>` (SSE down, using poll)
   - `error$: Signal<StreamError | null>`

## Aceitação
- [ ] `DashStreamClient` service created in `api/` subdirectory
- [ ] Open/close/reconnect lifecycle methods
- [ ] Heartbeat handling (no false timeouts on `:heartbeat` messages)
- [ ] Fallback to polling when SSE fails
- [ ] Signals exported (readonly) from `NotificationsFacade`
- [ ] Unit tests for reconnect + fallback scenarios
- [ ] `npx nx test @neuroassist/dash` passes

## Critério de Conclusão
```bash
npx nx test @neuroassist/dash  # streaming tests pass
npx nx build neuroassist-ai-web  # no build errors
```

## Notas de Implementação
- **Port:** NotificationsFacade should inject DashStreamClient and expose `notifications$`, `isConnected`
- **Flag:** `dash.sseStream` feature flag (default: on) — toggles SSE vs. poll-only
- **Test data:** Create mock stream events in jest fixtures
- **Timeout:** EventSource connection timeout ~10s; retry if no heartbeat for 45s
- **Cleanup:** `takeUntilDestroyed()` in facade for subscription lifecycle

## Referências
- Backend API contract: `backend/services/dash-service/contracts/api-contract.md` (GET /panel/stream)
- Plan: `docs/architecture/frontend/plans/plan-FE-DASH.md` P2 FE-DASH-008
- SPEC: `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md`
