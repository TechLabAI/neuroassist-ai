# PEA — Local Data Model

**Component:** Python Edge Agent | **Date:** 2026-07-29

The PEA persists two types of data locally using SQLite (`~/.neuroassist/local.db`).
No PII is persisted — only operational/state data.

---

## 1. Outbox table — `event_outbox`

Holds `GestoDetectado` events pending delivery to EVNT.

```sql
CREATE TABLE event_outbox (
    event_id        TEXT PRIMARY KEY,        -- UUID v4 (idempotency key)
    comando_id      TEXT NOT NULL,
    payload_json    TEXT NOT NULL,           -- full GestoDetectado JSON
    enqueued_at     TEXT NOT NULL,           -- ISO-8601 UTC
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TEXT NOT NULL,           -- ISO-8601 UTC
    last_error      TEXT                     -- last HTTP error or exception message
);
```

Constraints:
- Max 50 rows; oldest row deleted when capacity exceeded (ALERT logged)
- Rows deleted on `202 Accepted` or `409 Conflict` from EVNT
- `next_retry_at` set using back-off schedule: 1s → 5s → 30s → 2m → 10m (then stays at 10m)

---

## 2. Vocabulary cache — `vocabulary_cache`

Local snapshot of the patient's active vocabulary fetched from GEST.

```sql
CREATE TABLE vocabulary_cache (
    patient_id      TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    version         TEXT NOT NULL,           -- e.g. "v20260729-001" (ETag from GEST)
    cached_at       TEXT NOT NULL,           -- ISO-8601 UTC
    expires_at      TEXT NOT NULL,           -- cached_at + 5 minutes
    payload_json    TEXT NOT NULL,           -- full response JSON from GEST
    PRIMARY KEY (patient_id, tenant_id)
);
```

One row per patient (MVP: always one patient per device).

---

## 3. In-memory only (not persisted)

| Data | Lifetime | Reason |
|---|---|---|
| JWT access token | Process lifetime (refreshed before expiry) | Security: not written to disk |
| LandmarkSet (per frame) | Single frame cycle (~66ms at 15fps) | LGPD: biometric data never persisted |
| Classifier output scores | Single frame cycle | No retention needed |
| Current matched command state | Until cooldown expires | Transient matcher state |
| Detection profile | Session (refreshed every 60min) | Low-volatility config |

---

## 4. Credentials storage (outside SQLite)

| Secret | Storage | Fallback |
|---|---|---|
| `client_secret` (device OAuth2 secret) | OS keychain (Windows Credential Manager / Linux Secret Service / macOS Keychain) | `~/.neuroassist/credentials` (mode 600) |
| Enrollment token (one-time, 15min TTL) | Not persisted — provided via env var at enrollment time | N/A |

The `client_id` (device ID) is stored in `~/.neuroassist/config.toml` (not secret).
