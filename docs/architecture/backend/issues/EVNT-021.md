# EVNT-021 -- Configure Micrometer metrics (ingress counter, dispatch latency, false-positive rate gauge) + MDC structured logging

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Adds business-level observability to EVNT: Micrometer metrics for operational monitoring (ingress
throughput, dispatch latency, false-positive rate), and MDC-based structured logging so every log
line carries `correlationId`, `tenantId`, and `eventoId` for log aggregation (ELK/Loki). Without
these, diagnosing alert delivery failures or false-positive spikes in production is blind.

## Spec de referencia
- SPEC-07 (observability + health)

## Escopo
- [ ] Register Micrometer `Counter` `evnt.reports.ingested` (tags: `urgencia`, `result=new|duplicate|rejected`)
- [ ] Register Micrometer `Timer` `evnt.dispatch.latency` (tags: `tier`, `outcome=delivered|failed`)
- [ ] Register Micrometer `Gauge` `evnt.sse.active-emitters` backed by `SseEmitterAdapter.registrySize()`
- [ ] Register Micrometer `Counter` `evnt.dispatch.sweep.recovered` (incremented by EVNT-009)
- [ ] Configure MDC population in request lifecycle: set `correlationId` (from `X-Correlation-Id` filter), `tenantId` (from `TenantContextHolder`) on every log statement within a request; clear in `finally`
- [ ] Configure JSON log format (Logback JSON encoder or similar) so log lines are parseable by ELK
- [ ] Unit test: mock `MeterRegistry` — assert counter incremented after `ingestReport`; assert timer recorded after dispatch attempt

## Resultado esperado
Operators can monitor ingress throughput, dispatch success rate, false-positive trends, and SSE
connection count via Prometheus/Grafana. Every log line is correlatable by `correlationId`.

## Criterios de aceite
- [ ] `evnt.reports.ingested` counter is incremented on every `ingestReport` call with correct `urgencia` and `result` tags
- [ ] `evnt.dispatch.latency` timer is recorded after each dispatch attempt with `tier` and `outcome` tags
- [ ] `evnt.sse.active-emitters` gauge reflects the current number of open SSE connections
- [ ] Every log line produced during a request includes `correlationId` and `tenantId` in the MDC context
- [ ] Log output is JSON-formatted (one JSON object per line) for ELK ingestion

## Dependencias
- EVNT-006 (ingestReport — instrumented here)
- EVNT-007 (dispatch — instrumented here)
- EVNT-013 (SSE adapter — emitter count gauge)
- EVNT-020 (Actuator must be enabled for Micrometer to export `/actuator/metrics`)

## Estimativa
M

## Prioridade
P7
