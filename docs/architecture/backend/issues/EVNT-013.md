# EVNT-013 -- Implement SseEmitterAdapter (SseEventBroadcastPort, fan-out on EventoCriado/EventoConfirmado per tenant)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the SSE fan-out layer that is the primary live channel for the caregiver panel (FE ADR-005b
resolved in M2). `SseEmitterAdapter` implements `SseEventBroadcastPort` and manages a per-tenant
registry of open `SseEmitter` connections. On `EventoCriado`, it emits an `evento.created` SSE frame
to every open emitter in the matching tenant. On `EventoConfirmado`/`FalsoPositivoMarcado`, it emits
`evento.updated`. This is fully in-process — no broker required.

## Spec de referencia
- SPEC-03 (live event delivery)
- contracts/event-contracts.md §4 (SSE frame contract)
- contracts/api-contract.md §3.3

## Escopo
- [ ] Create `SseEmitterAdapter` in `infrastructure.sse` implementing `SseEventBroadcastPort`
- [ ] Maintain a `ConcurrentHashMap<String /*tenant*/, CopyOnWriteArrayList<SseEmitter>>` as the emitter registry
- [ ] Implement `register(tenant, emitter)`: add to registry; on emitter completion/timeout/error → remove from registry
- [ ] Implement `broadcast(tenant, eventType, data)`: iterate tenant's emitters; for each, send SSE frame with `event:` name, `data:` JSON, `id: seq`; on `IOException` → complete + remove the dead emitter
- [ ] Wire `@EventListener` on `EventoCriado` → `broadcast(tenant, "evento.created", ...)`; on `EventoConfirmado`/`FalsoPositivoMarcado` → `broadcast(tenant, "evento.updated", ...)`
- [ ] Connect `EventStreamController` (EVNT-003 placeholder) to call `SseEmitterAdapter.createEmitter(tenant)` and register it
- [ ] Unit tests: two emitters in tenant A; broadcast → both receive frame; one completes → removed; wrong-tenant broadcast → no frame

## Resultado esperado
Open SSE connections receive `evento.created` and `evento.updated` frames in real time when domain
events fire, scoped strictly to the event's tenant. Dead emitters are cleaned up automatically.

## Criterios de aceite
- [ ] `evento.created` SSE frame is emitted to every open emitter in the correct tenant within the same request cycle as `EventoCriado` publication
- [ ] `evento.updated` SSE frame is emitted on `EventoConfirmado` and `FalsoPositivoMarcado`
- [ ] A completed/timed-out emitter is automatically removed from the registry (no memory leak)
- [ ] A broadcast to `tenant_A` sends no frames to `tenant_B` emitters
- [ ] Each SSE frame carries `id: <evento.seq>` as specified in api-contract §3.3

## Dependencias
- EVNT-006 (EventoCriado source)
- EVNT-008 (EventoConfirmado/FalsoPositivoMarcado source)
- EVNT-003 (EventStreamController placeholder — wires SSE endpoint to this adapter)

## Estimativa
L

## Prioridade
P4
