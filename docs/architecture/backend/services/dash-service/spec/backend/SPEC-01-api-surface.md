# SPEC-01 — API Surface

## Objetivo
Definir a superfície REST + SSE do `dash-service` (BFF do painel do cuidador) e suas invariantes por
endpoint. Fonte canônica: `contracts/api-contract.md`.

## Escopo
- **In:** endpoints `/panel/**` (composição, SSE, ações), `/internal/notifications` (sink EVNT), erros
  RFC 7807, headers obrigatórios.
- **Out:** lógica de composição interna (SPEC-02), entrega SSE (SPEC-03), segurança detalhada (SPEC-06).

## Decisões técnicas
- Superfície dividida entre `/panel/**` (JWT do cuidador) e `/internal/**` (auth service-to-service) — ADR-004.
- SSE (`/panel/stream`) como canal push; long-poll como fallback — ADR-005.

## Implementação
- `PanelController`, `PanelStreamController`, `InternalNotificationController` (ver c4-component).
- Todos os endpoints retornam `application/json`, exceto `/panel/stream` (`text/event-stream`).

## Casos de borda
- Requisição sem `X-Correlation-Id` → DASH gera e ecoa.
- `size` de histórico acima de 100 → truncado para 100.
- `/internal/**` acessível pelo browser via GWY → bloqueado (rota não pública).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL expose `GET /panel/summary`, `/panel/patients/{id}/overview`, `/panel/patients/{id}/history`,
  `/panel/patients/{id}/device`, `/panel/stream`, `/panel/notifications/**`, `/panel/patients/{id}/commands/{cmd}/calibration`,
  `/panel/events/{id}/confirm`, and `/internal/notifications`.
- WHEN any `/panel/**` request lacks a valid ACS JWT, THE SYSTEM SHALL respond `401`.
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate and echo one.
- THE SYSTEM SHALL return all errors as `application/problem+json` including `correlationId`.
- THE SYSTEM SHALL cap history `size` at 100.
