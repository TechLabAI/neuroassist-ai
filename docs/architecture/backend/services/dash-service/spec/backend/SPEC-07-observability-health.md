# SPEC-07 — Observability & Health

## Objetivo
Garantir visibilidade do caminho humano (entrega de notificação) e da saúde das dependências upstream, com
correlação fim-a-fim.

## Escopo
- **In:** health/readiness, métricas (fan-out, SSE, entrega), logging estruturado, propagação de correlação,
  staleness de EDGE.
- **Out:** dashboards/alerting externos.

## Decisões técnicas
- **Health:** `/actuator/health` com readiness sensível ao ACS (dependência hard) e liveness do processo.
- **Métricas (Micrometer):** `dash.compose.latency` por upstream, `dash.upstream.degraded` counter,
  `dash.sse.sessions.active` gauge, `dash.notification.delivered/no_open_session/acknowledged` counters,
  `dash.edge.stale` counter.
- **Correlação:** `X-Correlation-Id` gerado se ausente e propagado a todos os upstreams e ao log.
- **Logging estruturado JSON** — sem dado sensível/biométrico (não há na superfície do DASH).

## Casos de borda
- Upstream soft degradado → incrementa `dash.upstream.degraded{service}` sem falhar a resposta.
- Sessão SSE sem heartbeat além do timeout → evicção + decremento do gauge.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL expose `/actuator/health` with readiness reflecting ACS availability.
- THE SYSTEM SHALL propagate `X-Correlation-Id` to every upstream call and log line.
- THE SYSTEM SHALL emit a metric for active SSE sessions and for delivered/no_open_session/acknowledged
  counts.
- WHEN an upstream section degrades, THE SYSTEM SHALL increment a degradation metric labeled by service.
- THE SYSTEM SHALL NOT log biometric, video or landmark data (none is present).
