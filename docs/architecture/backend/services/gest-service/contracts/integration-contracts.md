# GEST — Integration Contracts (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service`
> External integrations: **EDGE service only** (device pairing verification).
> No third-party external APIs in MVP.

## 1. EDGE — Device pairing verification (outbound)

GEST calls `edge-service` to verify that a `deviceId` belongs to the calling tenant before
serving a detection profile pull (api-contract §4.4).

| Aspect | Value |
|---|---|
| Service | `edge-service` (discovered via REG) |
| Endpoint | `GET /api/v1/edge/devices/{deviceId}/tenant` |
| Auth | Bearer token: GEST's own service credential (OAuth2 `client_credentials`) |
| Forwarded headers | `X-Correlation-Id` |
| Response | `{ "deviceId": "D-12", "tenantRef": "empresa-test" }` or `404` if not paired |
| SLA | < 200ms p99 (co-located microservices) |

### 1.1 Resilience

| Knob | Value |
|---|---|
| Read timeout | 2 000 ms |
| Connect timeout | 500 ms |
| Retry | 1 retry on `ConnectException` / `ReadTimeoutException` (idempotent GET) |
| Circuit breaker | Resilience4j; threshold 50% / 10-call window; wait 30s before HALF_OPEN |
| Fallback | `503 Service Unavailable` — GEST SHALL NOT bypass device pairing silently |

### 1.2 Behavior invariants (EARS)

- WHEN `EdgePairingClient` returns `404`, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/device-not-paired`).
- WHEN the circuit is OPEN (EDGE unavailable), THE SYSTEM SHALL respond `503 Service
  Unavailable` on `GET /devices/{deviceId}/detection-profile` and `POST /recognized-commands`.
- THE SYSTEM SHALL forward `X-Correlation-Id` to EDGE on every call.
- THE SYSTEM SHALL NOT send any biometric or patient-identifying data to EDGE — the only
  outbound payload is the opaque `deviceId` in the URL path.

### 1.3 Open item

The device-credential format used when EDGE calls **back into** GEST (for report posting and
profile pulls from the edge `GestureDetectionEngine` directly) is not yet frozen. The JWT-device
token assumption is provisional (api-contract §6.3). Final shape (mTLS vs. OAuth2
`client_credentials`) is frozen jointly with the EDGE team during backlog.

## 2. No other external integrations

GEST has no third-party API dependencies in the MVP. Future candidates (if edge engine
telemetry cloud aggregation becomes a requirement) would be added here.
