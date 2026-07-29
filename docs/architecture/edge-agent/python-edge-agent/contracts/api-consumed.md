# PEA — APIs Consumed

**Component:** Python Edge Agent | **Date:** 2026-07-29

The PEA is a consumer-only API client. It consumes 5 backend services via HTTPS REST.
No inbound port is exposed (the PEA has no server socket in MVP).

**Correction note (2026-07-29):** GEST does not expose a `/vocabulary/{pacienteId}/active` endpoint.
GEST owns the detection-profile (primitive catalog + engineFeatureRef). Patient commands (Comando —
timing, urgência, label) are owned by PAC. The PEA needs both layers: GEST for classification config,
PAC for command matching. See §2 and §3 below.

---

## 1. ACS — Authentication (client_credentials grant)

### 1.1 Token endpoint

```
POST {backend.url}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={device.id}
&client_secret={device.secret}
&scope=edge:publish
```

**Response (200 OK):**
```json
{
  "access_token": "<JWT RS256>",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "edge:publish"
}
```

**Error (401):**
```json
{ "error": "invalid_client", "error_description": "..." }
```

**EARS:**
- WHEN `device.id` / `device.secret` are valid → response carries `access_token` with role `edge_agent`
- WHEN credentials are invalid → `401 invalid_client`; agent must not retry automatically (requires reconfiguration)
- WHEN ACS is unreachable → agent uses cached token until expiry; after expiry → `AUTH_UNAVAILABLE` error

---

## 2. GEST — Detection profile (classification layer)

GEST is the cloud-side detection authority (ADR-005 BE). It owns the TipoGesto primitive catalog
and the versioned detection profile — the config the PEA uses to classify gestures. It does NOT
own patient commands (those are in PAC, §3).

### 2.1 Detection profile pull (edge endpoint)

```
GET {backend.url}/api/v1/gest/devices/{deviceId}/detection-profile
Authorization: Bearer {access_token}   ← device credential (ACS client_credentials, role edge_agent)
X-Empresa-Id: {device.tenant_id}
If-None-Match: {cached_profile_version}   ← conditional GET; omit on first call
```

**Response (200 OK):**
```json
{
  "version": "v3",
  "active": true,
  "publishedAt": "2026-07-29T08:00:00Z",
  "primitives": [
    {
      "tipoGestoId": "uuid",
      "nome": "piscar duplo",
      "detectabilityState": "supported",
      "engineFeatureRef": "mp.blink.double.v1"
    }
  ]
}
```

**Response (304 Not Modified):** `If-None-Match` matched — use cached profile, no body.

**Response (403 device-not-paired):** device not enrolled or tenant mismatch.

**EARS:**
- WHEN profile version unchanged (`304`) → use cache, no re-parse
- WHEN GEST unreachable AND cache exists → use cache; log WARN
- WHEN GEST unreachable AND no cache → startup fails with `PROFILE_UNAVAILABLE`
- WHEN `403 device-not-paired` → log CRITICAL, stop (device needs re-enrollment)

Refresh: on startup (blocking) + every 60 minutes (background, ETag-conditional).

### 2.2 Detection contract (frozen schema)

```
GET {backend.url}/api/v1/gest/detection-contract
Authorization: Bearer {access_token}
```

**Response (200 OK):**
```json
{
  "contractVersion": "1.0.0",
  "recognizedCommandReportSchema": { "$ref": "…/RecognizedCommandReport.schema.json" }
}
```

Fetched once on startup; used to validate the `RecognizedCommandReport` shape before sending to EVNT.
Cached in memory for the session duration.

---

## 3. PAC — Patient active commands (matching layer)

PAC owns the patient's Comando catalog: the mapping from gesture primitives to labeled actions,
with timing windows and urgency level. The PEA's `CommandMatcher` needs this to decide which
`comandoId` a detected primitive sequence corresponds to.

**Endpoint (pending PAC Layer 1):** `GET /api/v1/pac/patients/{pacienteId}/commands/active`

This endpoint does not yet exist (PAC not yet architected). The PEA spec records it as a
**required contract** that PAC must expose. When PAC Layer 1 is done, close this gap and
update PEA-003 (VocabularyCache implementation).

**Expected response:**
```json
{
  "pacienteId": "uuid",
  "tenantId": "uuid",
  "etag": "v20260729-003",
  "comandos": [
    {
      "comandoId": "uuid",
      "label": "Água",
      "primitivoPrincipalId": "uuid",
      "primitivoPrincipalEngineRef": "mp.blink.single.right.v1",
      "primitivoSecundarioId": null,
      "primitivoSecundarioEngineRef": null,
      "sensibilidade": "alta",
      "urgencia": "rotina",
      "tempoMinGestoMs": 800,
      "tempoMaxGestoMs": 3000
    }
  ]
}
```

> The `engineRef` fields are denormalized from GEST (PAC stores the IDs, PEA needs the refs).
> Alternatively PAC returns TipoGestoIds and PEA cross-references against the GEST profile in memory.
> Design decision deferred to PAC Layer 1.

**EARS (pending):**
- WHEN PAC unreachable AND cache exists → use cache; log WARN
- WHEN PAC unreachable AND no cache → startup fails with `COMMANDS_UNAVAILABLE`
- WHEN response ETag unchanged → skip parse, use cache
- Refresh: every 5 minutes background + on demand (SIGHUP)

---

## 4. EVNT — Event publication

### 3.1 Publish GestoDetectado

```
POST {backend.url}/api/v1/evnt/events
Authorization: Bearer {access_token}
X-Empresa-Id: {device.tenant_id}
X-Idempotency-Key: {event_id}
Content-Type: application/json

{
  "eventId": "{uuid-v4}",
  "pacienteId": "{device.patient_id}",
  "comandoId": "{matched_comando_id}",
  "tipoGestoPrincipal": "{primitivo_principal_code}",
  "tipoGestoSecundario": "{primitivo_secundario_code | null}",
  "confianca": 0.94,
  "durationMs": 1200,
  "capturedAt": "2026-07-29T10:15:30.412Z",
  "deviceId": "{device.id}"
}
```

**Response (202 Accepted):**
```json
{ "eventId": "uuid", "status": "queued" }
```

**Errors:**
- `401 Unauthorized` → token expired; refresh and retry once
- `409 Conflict` → duplicate `X-Idempotency-Key`; treat as success (already delivered)
- `422 Unprocessable` → `comandoId` unknown (vocabulary stale); trigger vocabulary re-fetch
- `5xx` / network error → retain in outbox, retry with back-off

**EARS:**
- WHEN `202 Accepted` → remove from outbox
- WHEN `409 Conflict` → remove from outbox (idempotent duplicate)
- WHEN `422` with `error=unknown-command` → re-fetch vocabulary, do NOT retry this event
- WHEN `401` → refresh token once; if refresh fails → `AUTH_UNAVAILABLE`
- WHEN `5xx` or timeout → outbox entry survives; back-off 1s/5s/30s/2m/10m

---

## 5. EDGE — Device registration and heartbeat

### 4.1 Enrollment (first boot only)

```
POST {backend.url}/api/v1/edge/devices/enroll
Content-Type: application/json
Authorization: Bearer {bootstrap_token}

{
  "enrollmentToken": "{one_time_token}",
  "deviceMeta": {
    "os": "linux",
    "hostname": "neuro-rpi-001",
    "pythonVersion": "3.11.9",
    "agentVersion": "1.0.0"
  }
}
```

**Response (200 OK):**
```json
{
  "deviceId": "uuid",
  "clientId": "uuid",
  "clientSecret": "<secret — store in OS keychain>",
  "patientId": "uuid",
  "tenantId": "uuid"
}
```

One-time: the `clientId`/`clientSecret` must be persisted to OS keychain immediately.
The enrollment token is single-use and expires after 15 minutes.

### 4.2 Detection profile fetch

```
GET {backend.url}/api/v1/edge/devices/{deviceId}/profile
Authorization: Bearer {access_token}
X-Empresa-Id: {device.tenant_id}
```

**Response (200 OK):**
```json
{
  "deviceId": "uuid",
  "captureProfile": {
    "fps": 15,
    "resolution": "640x480",
    "modelVersion": "v1.2"
  }
}
```

Fetched on startup; cached in memory; refreshed every 60 minutes.

### 4.3 Heartbeat

```
POST {backend.url}/api/v1/edge/devices/{deviceId}/heartbeat
Authorization: Bearer {access_token}
X-Empresa-Id: {device.tenant_id}
Content-Type: application/json

{
  "deviceId": "{device.id}",
  "status": "online",
  "fps": 14.8,
  "outboxDepth": 0,
  "agentVersion": "1.0.0",
  "reportedAt": "2026-07-29T10:15:00Z"
}
```

**Response (204 No Content):** success — best-effort, no action on failure.

---

## 5. Security baseline

- All calls use HTTPS (TLS 1.2+). Plain HTTP is rejected at startup.
- JWT stored in memory only (never persisted to disk)
- `client_secret` in OS keychain; path `NEUROASSIST_DEVICE_SECRET` env var as fallback
- `X-Correlation-Id` header generated per session (UUID v4), included in all requests
