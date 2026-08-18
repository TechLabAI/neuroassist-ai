# DASH-004 -- Implement PanelSession in-memory registry with heartbeat eviction

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O canal SSE do DASH requer um registro em memoria das sessoes abertas do painel do cuidador.
Cada sessao e representada por um `PanelSession` (sessionId, tenantId, caregiverId, SseEmitter,
connectedAt, lastHeartbeatAt). O registro e thread-safe (ConcurrentHashMap) e nunca persistido:
um restart do DASH descarta todas as sessoes sem perda de dado de dominio (ADR-004).

## Spec de referencia

- SPEC-03 (entrega de notificacao SSE: sessao, heartbeat, eviction)
- SPEC-04 (PanelSession e estado in-memory, nao linha de banco)
- SPEC-05 (idempotencia: deduplicacao por dispatchId dentro da sessao)

## Escopo

- [ ] `domain/model/PanelSession` — record com sessionId (UUID), tenantId, caregiverId, `SseEmitter` ref, connectedAt, lastHeartbeatAt
- [ ] `application/PanelSessionRegistry` — ConcurrentHashMap<UUID, PanelSession> com `register(session)`, `evict(sessionId)`, `findByCaregiverId(caregiverId)`, `findAll()`
- [ ] Heartbeat scheduler (`@Scheduled(fixedDelayString)`) — itera todas as sessoes, emite evento SSE `heartbeat`, atualiza `lastHeartbeatAt`; sessoes com `lastHeartbeatAt` mais antigo que `HEARTBEAT_SECONDS * 2` sao eviccionadas
- [ ] Teste unitario de `PanelSessionRegistry`: register, evict, concurrent access, findByCaregiverId

## Resultado esperado

`PanelSessionRegistry` gerencia sessoes em memoria de forma thread-safe. Sessoes expiradas sao
eviccionadas pelo scheduler sem nenhuma escrita em banco.

## Criterios de aceite

- [ ] `PanelSessionRegistry.register()` adiciona sessao indexada por sessionId E por caregiverId
- [ ] `PanelSessionRegistry.evict()` remove a sessao e chama `SseEmitter.complete()`
- [ ] Scheduler emite `event: heartbeat` com `data: {"at":"<iso>"}` a cada `HEARTBEAT_SECONDS`
- [ ] Sessao com `lastHeartbeatAt` expirado e eviccionada automaticamente
- [ ] Teste unitario passa cobrindo insert, evict e lookup concurrente

## Dependencias

- DASH-001 (estrutura de pacotes)
- DASH-003 (PanelSession como value object)

## Estimativa

M

## Prioridade

P2
