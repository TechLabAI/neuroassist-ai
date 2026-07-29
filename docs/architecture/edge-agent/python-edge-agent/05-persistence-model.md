# PEA — Persistence Model

**Component:** Python Edge Agent | **Date:** 2026-07-29

---

## 1. Overview

The PEA uses **SQLite** (`~/.neuroassist/local.db`) for local persistence.
SQLite is chosen because:
- Zero-config, zero-admin (no separate process)
- Survives process restarts (unlike in-memory state)
- Single-file, easy to backup or wipe
- aiosqlite provides async access compatible with the asyncio event loop

The Java backend's PostgreSQL is **not accessible** to the PEA — they share the machine but not the database. The PEA's SQLite is private to the PEA process.

---

## 2. Tables

### 2.1 `event_outbox`

Holds `GestoDetectado` events pending delivery to EVNT.

```sql
CREATE TABLE IF NOT EXISTS event_outbox (
    event_id        TEXT    PRIMARY KEY,   -- UUID v4; X-Idempotency-Key on retry
    comando_id      TEXT    NOT NULL,
    urgency         TEXT    NOT NULL,      -- 'emergencia' | 'rotina'
    payload_json    TEXT    NOT NULL,      -- full GestoDetectado JSON (no biometric data)
    enqueued_at     TEXT    NOT NULL,      -- ISO-8601 UTC
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TEXT    NOT NULL,      -- ISO-8601 UTC; drives drain loop scheduling
    last_error      TEXT                   -- last HTTP error or exception string
);
CREATE INDEX IF NOT EXISTS idx_outbox_next_retry ON event_outbox(next_retry_at);
```

**Capacity:** max 50 rows. `EventPublisher.enqueue()` deletes the oldest row before inserting when at capacity, then logs ALERT.

**Back-off schedule:** `next_retry_at` set to `now + delay` where delay sequence is `[1, 5, 30, 120, 600]` seconds (capped at 600s on repeat failures).

**Cleanup:** rows deleted on `202 Accepted` or `409 Conflict` from EVNT.

---

### 2.2 `detection_profile_cache`

Local snapshot of the GEST detection profile.

```sql
CREATE TABLE IF NOT EXISTS detection_profile_cache (
    device_id    TEXT PRIMARY KEY,
    tenant_id    TEXT NOT NULL,
    version      TEXT NOT NULL,      -- ETag / profile version from GEST
    cached_at    TEXT NOT NULL,      -- ISO-8601 UTC
    expires_at   TEXT NOT NULL,      -- cached_at + 60 minutes
    payload_json TEXT NOT NULL       -- full DetectionProfile JSON from GEST
);
```

One row per device (MVP: one device). Refreshed on startup and every 60 minutes
(ETag-conditional GET — no payload transfer if unchanged).

---

### 2.3 `command_cache`

Local snapshot of the patient's active `Comando` list from PAC.

```sql
CREATE TABLE IF NOT EXISTS command_cache (
    patient_id   TEXT PRIMARY KEY,
    tenant_id    TEXT NOT NULL,
    etag         TEXT NOT NULL,      -- ETag / version from PAC
    cached_at    TEXT NOT NULL,
    expires_at   TEXT NOT NULL,      -- cached_at + 5 minutes
    payload_json TEXT NOT NULL       -- full CommandVocabulary JSON from PAC
);
```

One row per patient (MVP: one patient). Refreshed on startup + every 5 minutes + on
`422 unknown-command` from EVNT (vocabulary stale signal).

---

## 3. What is NOT persisted

| Data | Reason |
|---|---|
| Raw frames (BGR images) | LGPD — biometric data stays in RAM only |
| LandmarkSet coordinates | LGPD — ephemeral, lifetime of one frame cycle |
| ONNX classifier scores | Not needed beyond current frame |
| GestureWindow state | In-memory; reset acceptable on restart |
| JWT access token | Security — never written to disk |
| Cooldown timers | In-memory; losing them on restart is safe (worst case: one extra event) |

---

## 4. File system layout

```
~/.neuroassist/
├── config.toml          # device config (id, patient_id, tenant_id, backend.url)
├── local.db             # SQLite database (event_outbox + caches)
└── credentials          # client_secret fallback (mode 600); prefer OS keychain

/path/to/neuroassist-edge-agent/   # application directory
├── config.toml                    # defaults (checked into repo, no secrets)
└── models/
    └── gesture_v1.onnx            # bundled ONNX model
```

**OS keychain** (preferred for `client_secret`):
- Windows: Windows Credential Manager (`keyring` service: `neuroassist`, username: `device`)
- Linux: Secret Service API (GNOME Keyring / KWallet)
- macOS: Keychain (macOS support is informational only; not a target platform for MVP)

---

## 5. Migration strategy

SQLite schema is managed by the PEA's `db.py` using `CREATE TABLE IF NOT EXISTS`. No
migration framework is used in MVP — the schema is append-only and simple enough that
a fresh `local.db` is always valid. If a breaking schema change is needed, the agent
deletes and recreates `local.db` (losing the outbox — acceptable since events are retried
from the point of restart, not from the dawn of time).
