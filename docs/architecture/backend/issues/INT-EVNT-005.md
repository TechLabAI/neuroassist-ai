# INT-EVNT-005 -- Contract tests EVNT -> DASH (WireMock: NotificationDispatchPort fail-open, delivery ack, retry on 5xx)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Validates the EVNT → DASH contract (`NotificationDispatchPort`) with WireMock, specifically the
**fail-open** behavior: a DASH `503` must leave the dispatch `failed` for retry without failing the
HTTP response. Three scenarios: (1) DASH returns `200` → dispatch `delivered`; (2) DASH returns `503`
→ dispatch stays `failed`, `NotificacaoFalhou` published, retry re-queued; (3) DASH eventually
returns `200` on a retry → dispatch transitions to `delivered`.

## Spec de referencia
- SPEC-10 (integrations — DASH contract)
- contracts/hub-integration.md §2.4

## Escopo
- [ ] Create WireMock stubs: `dash/notify-ok.json` (POST /api/v1/notifications → 200); `dash/notify-503.json` (→ 503); `dash/notify-retry-ok.json` (first call 503, second 200 — stateful WireMock sequence)
- [ ] Write `DashNotificationContractTest`: dispatch success → adapter returns; 503 → `DeliveryFailedException`; verify `notification_dispatch.status = failed` after exception; verify retry eventually succeeds with stateful stub
- [ ] Assert `evnt.dispatch.latency` timer is recorded for both success and failure outcomes (Micrometer test reporter)

## Resultado esperado
DASH fail-open contract is verified; a `503` response does not propagate as an HTTP error but correctly
leaves the dispatch for retry.

## Criterios de aceite
- [ ] DASH `200` → `DashFeignAdapter.dispatch` returns without exception; `notification_dispatch.status = delivered`
- [ ] DASH `503` → `DeliveryFailedException`; `notification_dispatch.status = failed`; `NotificacaoFalhou` published
- [ ] Stateful WireMock sequence: retry after failure → `notification_dispatch.status = delivered`
- [ ] `evnt.dispatch.latency` timer recorded with `outcome` tag for both success and failure

## Dependencias
- EVNT-017 (DashFeignAdapter must exist)
- EVNT-007 (OrchestrateNotification — retry is exercised here)

## Estimativa
S

## Prioridade
P5
