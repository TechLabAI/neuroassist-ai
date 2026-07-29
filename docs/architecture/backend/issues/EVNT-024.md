# EVNT-024 -- Implement BDD Cucumber: SSE stream + Last-Event-ID replay + false-positive rate aggregate (GET /events?groupBy=comando)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements step definitions for the SSE stream and false-positive rate Cucumber features
(`sse-live-delivery.feature`) authored in M2. SSE scenarios: (a) client connects to `/events/stream`,
a new `emergencia` Evento is created → client receives `evento.created` frame; (b) client reconnects
with `Last-Event-ID=N` → receives all frames with `seq > N`. False-positive scenario: caregiver marks
two events `falso_positivo` for the same `comandoRef` → `GET /events?groupBy=comando` returns
`falsePositiveRate > 0` for that `comandoRef`.

## Spec de referencia
- SPEC-08 (BDD test scenarios)
- SPEC-03 (SSE delivery)
- tests/cucumber/sse-live-delivery.feature

## Escopo
- [ ] Implement step definitions for SSE scenario: open `SseEmitter` connection; trigger `POST /reports` (emergencia); assert `evento.created` frame received with correct `eventoId` and `urgencia`
- [ ] Implement Last-Event-ID replay step: create 3 events (seq 1,2,3); client connects with `Last-Event-ID=1`; assert frames for seq 2 and 3 received as backfill
- [ ] Implement false-positive rate scenario: ingest two events with same `comandoRef`; POST confirmation `falso_positivo` for both; call `GET /events?groupBy=comando&comandoRef=<ref>`; assert `falsePositiveRate = 1.0`
- [ ] Use `SseEmitter` test client (`org.springframework.test.web.servlet.MvcResult` async or `WebTestClient` with SSE support)

## Resultado esperado
SSE live delivery, Last-Event-ID resume, and false-positive rate aggregate all work end-to-end and
are verified by BDD scenarios.

## Criterios de aceite
- [ ] SSE scenario: `evento.created` frame received within the same request cycle as event creation
- [ ] Last-Event-ID replay: exactly 2 backfill frames received for seq 2 and 3 when Last-Event-ID=1
- [ ] False-positive rate scenario: `GET /events?groupBy=comando` returns `falsePositiveRate=1.0` for a `comandoRef` with 2/2 falso_positivo verdicts
- [ ] All Cucumber scenarios in `sse-live-delivery.feature` pass
- [ ] SSE test client handles `text/event-stream` content type and parses `event:`, `data:`, `id:` fields

## Dependencias
- EVNT-013 (SseEmitterAdapter)
- EVNT-014 (Last-Event-ID replay)
- EVNT-008 (ConfirmEvento — for false-positive scenario)
- EVNT-010 (JPA adapters)

## Estimativa
M

## Prioridade
P8
