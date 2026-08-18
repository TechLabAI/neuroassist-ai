# SPEC-10 — Integrations

## Objetivo
Consolidar a matriz de integração upstream do DASH. Contrato canônico: `contracts/integration-contracts.md`.
Sem sistemas externos de terceiros, sem broker, sem hub/AI (`hub-integration.md`).

## Escopo
- **In:** ACS, PAC, EVNT, HIST, EDGE (via GWY/REG); políticas de timeout, retry, circuit breaker, degradação.
- **Out:** TechLab AI (nenhuma dependência — `ai.hasAi = false`).

## Matriz de integração
| Upstream | Port | Operações | Criticidade |
|---|---|---|---|
| ACS | `IdentityDirectoryPort` | JWK, scope resolve | hard (503 se down) |
| PAC | `CommandCatalogPort` | read catalog, write-through calibration | required por tela |
| EVNT | `EventStatePort`, `NotificationDispatchBackPort` | dispatch sink, unacked pull, delivered/ack, confirm | required (leitura soft-degrada) |
| HIST | `HistoryQueryPort` | audit timeline read | required por tela |
| EDGE | `DeviceLivenessPort` | liveness poll (ETag/304, 15s) | soft (degrada indicador) |

## Decisões técnicas
- Cada integração atrás de uma **Port** hexagonal; transporte não vaza para `application`/`domain`.
- Timeout default 2s; SSE dispatch path 1s. 1 retry só para GETs idempotentes; write-through nunca reexecutado.
- Circuit breaker por upstream; OPEN → degrada seção (soft) ou `503` (hard).

## Casos de borda
- EDGE `304` → serve último liveness conhecido; stale além do limiar → `unknown`/`degraded`.
- PAC rejeita write-through → relay do ProblemDetail com mesmo status.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL access every upstream via a hexagonal outbound port over REST through GWY/REG.
- THE SYSTEM SHALL make no call to any TechLab AI bounded context.
- IF ACS is unavailable, THEN THE SYSTEM SHALL respond `503` (no scope ⇒ no authorized view).
- WHILE EDGE is unavailable, THE SYSTEM SHALL soft-degrade the device indicator.
- THE SYSTEM SHALL relay PAC/EVNT write-through rejections unchanged (same status).
