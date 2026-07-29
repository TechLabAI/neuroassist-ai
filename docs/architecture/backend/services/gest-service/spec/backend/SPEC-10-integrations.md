# SPEC-10 — Integrations (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-005 BE (edge/cloud split), ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the synchronous integration contracts between `gest-service` and peer services:
PAC (eligibility consumer), EVNT (report receiver), EDGE (device pairing), GWY, REG.
No broker integrations in the MVP (messaging disabled).

## 2. Scope

**In:**
- Peer-service integrations over REST (Feign clients)
- EDGE device-pairing resolution for the edge profile pull
- GWY routing and REG discovery
- Circuit breaker / retry / fallback posture

**Out:**
- External third-party APIs (GEST has none in MVP)
- Broker integrations (deferred)
- FE live-test bridge transport (owned jointly by FE + edge teams)

## 3. Peer-service integration map

| Caller | Endpoint served by GEST | Notes |
|---|---|---|
| PAC | `POST /eligibility:check` | PAC calls synchronously before building a Comando |
| EVNT | `POST /recognized-commands` | EVNT calls to validate a report before persisting |
| Edge (`GestureDetectionEngine`) | `GET /devices/{id}/detection-profile` | Edge pulls active profile; conditional GET |
| Any service | `GET /detection-contract` | Read frozen schema descriptor |

### 3.1 GEST → EDGE (device pairing resolution)

GEST needs to validate that `deviceId` belongs to the caller's tenant before serving a profile
pull. This is the only **outbound** REST call GEST makes.

- **Feign client:** `EdgePairingClient` → `EdgeService` (`GET /api/v1/edge/devices/{deviceId}/tenant`).
- **Auth:** service-to-service bearer token (same OAuth2 client credentials as `gest-service`).
- **Circuit breaker:** Resilience4j `CircuitBreaker("edge-pairing")` — threshold: 50% failures
  in 10-call sliding window → OPEN; wait 30s before HALF_OPEN.
- **Fallback:** WHEN `EdgePairingClient` is unavailable, THE SYSTEM SHALL respond `503 Service
  Unavailable` on the affected endpoint rather than silently bypassing the pairing check.
- **Retry:** 1 retry on `ConnectException` / `ReadTimeoutException` (idempotent read).
- **Timeout:** 2s read timeout; 500ms connect timeout.

### 3.2 GWY routing

`gest-service` registers under the logical name `gest-service` in REG. GWY routes
`/api/v1/gest/**` to this service. No sticky-session affinity needed (stateless per request,
schema routing via JWT).

### 3.3 REG discovery

`gest-service` registers on startup via Eureka client (`@EnableDiscoveryClient`). Health check:
`GET /actuator/health` (configured as Eureka health indicator).

## 4. Feign client declarations

```java
// EdgePairingClient (outbound to EDGE)
@FeignClient(name = "edge-service",
             configuration = GestFeignConfig.class,
             fallback = EdgePairingFallback.class)
public interface EdgePairingClient {
    @GetMapping("/api/v1/edge/devices/{deviceId}/tenant")
    EdgeTenantResponse getDeviceTenant(@PathVariable String deviceId);
}
```

Headers forwarded: `Authorization` (service token), `X-Correlation-Id`.

## 5. Circuit breaker posture (Resilience4j)

| Circuit | Threshold | Window | Timeout | Fallback |
|---|---|---|---|---|
| `edge-pairing` | 50% errors | 10 calls | 2s read | `503 Service Unavailable` |

- THE SYSTEM SHALL log a `WARN` entry whenever the circuit transitions to OPEN, including the
  `correlationId` and affected `deviceId` (no tenant PII in the log).

## 6. LGPD note

GEST makes no outbound call that carries biometric or patient-identifying data. The only
outbound call (EDGE pairing) passes only the opaque `deviceId`; the response carries only the
logical `tenantRef`. Privacy invariant preserved end-to-end.

## 7. Acceptance criteria

- `POST /eligibility:check` from PAC with valid token → `200` with eligibility result.
- `POST /recognized-commands` from EVNT with valid device credential → `200` with validation result.
- `GET /devices/{id}/detection-profile` with valid device token → `200` or `304`.
- WHEN `EdgePairingClient` is unavailable (circuit open), GEST returns `503` (no silent bypass).
- Feign calls forward `X-Correlation-Id`.
- No biometric data is sent to EDGE in the pairing call.
