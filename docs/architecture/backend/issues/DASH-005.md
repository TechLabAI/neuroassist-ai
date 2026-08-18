# DASH-005 -- Implement PanelStreamController: GET /panel/stream SSE channel with heartbeat

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

`GET /panel/stream` e o canal SSE principal do painel do cuidador. Ao conectar, o caregiver
recebe notificacoes em tempo real (via push do EVNT atraves do DASH) e heartbeats periodicos.
A conexao mantem um `PanelSession` viva no registro em memoria. Ao desconectar ou timeout,
a sessao e eviccionada e futuros dispatches respondem `no_open_session`.

## Spec de referencia

- SPEC-01 (endpoint: `GET /panel/stream`, Accept: text/event-stream)
- SPEC-03 (entrega SSE: session open, heartbeat, catchup-complete, eviction)

## Escopo

- [ ] `api/PanelStreamController` com `@GetMapping(value="/panel/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)`
- [ ] Ao conectar: extrair caregiverId/tenantId do JWT, criar `PanelSession` com novo `SseEmitter(timeout)`, registrar em `PanelSessionRegistry`
- [ ] Emitir evento `PanelSessionOpened` internamente para disparar o catch-up (DASH-011)
- [ ] `SseEmitter.onCompletion` e `onTimeout`: chamar `PanelSessionRegistry.evict(sessionId)` para garantir limpeza
- [ ] Teste de slice `@WebMvcTest`: verifica que `GET /panel/stream` com JWT valido retorna `Content-Type: text/event-stream` e emite pelo menos um evento `heartbeat`

## Resultado esperado

`GET /panel/stream` com JWT valido mantem a conexao aberta, emite `heartbeat` e registra a sessao.
Desconexao eviccia a sessao.

## Criterios de aceite

- [ ] Endpoint retorna `200` e `Content-Type: text/event-stream; charset=UTF-8`
- [ ] `PanelSession` e registrada em `PanelSessionRegistry` na conexao
- [ ] Evento `event: heartbeat` e emitido a cada `HEARTBEAT_SECONDS`
- [ ] Desconexao do cliente (onCompletion/onTimeout) eviccia a sessao do registro
- [ ] `GET /panel/stream` sem JWT valido retorna `401`

## Dependencias

- DASH-004 (PanelSessionRegistry)
- DASH-021 (OAuth2 configurado para /panel/**)

## Estimativa

M

## Prioridade

P2
