# DASH-011 -- Implement on-connect catch-up replay: GET /panel/notifications/unacknowledged + catchup-complete SSE event

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

Quando um painel (re)conecta via SSE, pode ter perdido notificacoes durante a desconexao. O
DASH faz um catch-up imediato: busca notificacoes nao-acknowledgeadas do EVNT e as reenvia via
SSE antes de entrar em modo live. Ao terminar o replay, emite `event: catchup-complete`. O DASH
nao armazena o inbox localmente (a fonte autoritativa e o EVNT).

## Spec de referencia

- SPEC-03 (on-connect catch-up: GET /panel/notifications/unacknowledged, catchup-complete SSE event)
- SPEC-05 (nao persistir notificacoes localmente)

## Escopo

- [ ] `application/PanelStreamService.replayCatchup(session)`: chama `EventStatePort.fetchUnacknowledged(caregiverId)` no EVNT, itera a lista e emite `event: notification` para cada `NotificationEnvelope` via `session.connectionRef`; ao terminar emite `event: catchup-complete data:{}`
- [ ] O catch-up e disparado internamente ao criar a `PanelSession` (listener do `PanelSessionOpened`)
- [ ] `api/PanelController.getUnacknowledged()` (GET /panel/notifications/unacknowledged): tambem chamavel diretamente, retorna `{ notifications: [...] }` como REST
- [ ] Teste: mock do `EventStatePort` com 2 notificacoes pendentes → verifica 2 eventos SSE notification + 1 catchup-complete

## Resultado esperado

Ao conectar, o caregiver recebe o historico de notificacoes nao-vistas antes de entrar no modo live.

## Criterios de aceite

- [ ] Ao criar `PanelSession`, `replayCatchup` e acionado automaticamente
- [ ] Cada `NotificationEnvelope` pendente e emitida como `event: notification` no SSE
- [ ] `event: catchup-complete` e emitido apos todos os eventos de replay
- [ ] EVNT indisponivel no catch-up: log de aviso, `catchup-complete` emitido mesmo assim (fail-open)

## Dependencias

- DASH-004 (PanelSessionRegistry)
- DASH-005 (PanelStreamController)
- DASH-018 (EvntEventRestAdapter: fetchUnacknowledged)

## Estimativa

S

## Prioridade

P3
