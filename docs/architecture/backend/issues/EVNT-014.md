# EVNT-014 -- Implement Last-Event-ID replay (seq-based resume, keepalive scheduler at keepAliveSeconds interval)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Completes the SSE channel reliability: a reconnecting client sends `Last-Event-ID` (= the last `seq`
it received); the server must replay all missed events since that seq so no alarm is lost across a
transient drop. Additionally, a scheduled keepalive must be emitted every `keepAliveSeconds` (default
15) to prevent proxy/firewall timeouts. This is the "FE degraded mode" safeguard — if SSE is down,
the client falls back to `GET /events/active` polling (FE side; no server change needed beyond
correct behavior here).

## Spec de referencia
- SPEC-03 (live event delivery — Last-Event-ID resume, keepalive)
- contracts/api-contract.md §3.3 (SSE stream spec)

## Escopo
- [ ] Implement `Last-Event-ID` handling in `EventStreamController`: read `Last-Event-ID` header from `HttpServletRequest`; if present, query `EventoRepositoryPort.findBySeqGreaterThan(tenantId, lastSeq)` and send each as a backfill `evento.created`/`evento.updated` frame before registering the emitter
- [ ] Implement keepalive: a `@Scheduled(fixedDelayString = "${evnt.sse.keepalive-seconds:15}000")` task in `SseEmitterAdapter` that iterates all open emitters and sends a `keepalive` frame with `{serverTime: <ISO>}`
- [ ] Unit test: new connection with `Last-Event-ID=5` + existing events with seq 6,7 → two backfill frames sent on connect
- [ ] Unit test: no `Last-Event-ID` → no backfill frames, just register
- [ ] Unit test: keepalive task fires → all open emitters receive a `keepalive` frame

## Resultado esperado
A client that reconnects with `Last-Event-ID` receives all missed events without polling, and all
connections remain alive across network idle periods.

## Criterios de aceite
- [ ] A reconnecting client with `Last-Event-ID=N` receives exactly the events with `seq > N` in the current tenant before the live stream begins
- [ ] A new connection without `Last-Event-ID` receives no backfill frames
- [ ] Keepalive frames (`event: keepalive`, `data: {serverTime}`) are sent every `EVNT_SSE_KEEPALIVE_SECONDS` to all open emitters
- [ ] Keepalive interval is configurable via `EVNT_SSE_KEEPALIVE_SECONDS` (default 15)

## Dependencias
- EVNT-013 (SseEmitterAdapter — keepalive uses the same emitter registry)
- EVNT-010 (EventoRepositoryPort for seq-based replay query)

## Estimativa
M

## Prioridade
P4
