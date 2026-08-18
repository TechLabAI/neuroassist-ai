# DASH — Data Model

> Service: `dash-service` · Package: `com.neuroassist.dash`
> Multi-tenancy: schema-per-`empresa` (project-wide), global schema `neuroassist_global`.
> **MVP posture: ZERO DASH tables.** DASH is a Backend-for-Frontend and owns no domain persistence
> (ADR-004). This document is authoritative for the persistence contract consumed by the migration
> generator: **DASH contributes no `V1__` schema in the MVP** — this is intentional, not a placeholder.

## 1. Conventions

- **Ownership rule:** every business fact DASH renders or changes lives in an **owner service** — HIST
  (history/audit), PAC (patient + command catalog + calibration), EVNT (events + notification dispatch
  state), EDGE (device liveness), ACS (identity + caregiver↔patient linkage). DASH reads live or writes
  through; it stores nothing durable (ADR-004 / ADR-006).
- **Migrations:** centralized in the MIG service (ADR-003 shared). DASH runs no boot-time DDL. In the MVP
  DASH authors **no Flyway scripts** because it owns no tables.
- **PK strategy (if a table is ever added):** `uuid` primary keys, `timestamptz` timestamps, tenant tables
  live in `t_<empresa>` and are resolved at request time — never cross-schema.

## 2. Persisted entities (MVP)

**None.** The composition, delivery and proxy responsibilities require no durable store. `PanelViewModel`,
`NotificationEnvelope` and `PanelSession` are all transient/in-memory (doc 03). Persisting any of them would
create a second, divergent copy of safety-critical state that another service already owns and retries.

> Because there are no `sql` blocks in this section, the migration generator correctly produces **no DDL**
> for dash-service. This is the desired outcome for the MVP.

## 3. In-memory runtime state (NOT persisted)

### 3.1 `PanelSession` (in-memory registry)
The one runtime "state" DASH holds — the live SSE session registry. It is never a database row; its truth is
the live TCP/SSE connection, which cannot outlive the process.

| Field | Type | Notes |
|---|---|---|
| `sessionId` | string (uuid) | key |
| `tenantId` | string (uuid) | from JWT `tenant` |
| `caregiverId` | string (uuid) | from JWT `sub` |
| `connectionRef` | `SseEmitter` ref | live connection handle |
| `connectedAt` | instant | session open time |
| `lastHeartbeatAt` | instant | drives timeout eviction |

- **Lifecycle:** created on `GET /panel/stream` connect; evicted on disconnect / heartbeat timeout / process
  restart. Rebuilt by panels reconnecting (triggers on-connect catch-up).
- **Invariant:** a DASH restart drops all sessions and loses **no** domain data.

### 3.2 `NotificationEnvelope` (in-flight, transient)
The in-flight representation of an EVNT `NotificationDispatch` while DASH pushes it and awaits ack. Carries
`dispatchId`, `tier`, `targetCaregiverId`, `patientId`, payload snapshot. Never persisted; its authoritative
state (`queued/delivered/acknowledged/failed`) lives in EVNT (EVNT ADR-005).

### 3.3 `PanelViewModel` family (transient)
Composed read projections (`SummaryView`, `PatientOverview`, `HistoryPage`, `DeviceStatus`), assembled per
request from upstream data and discarded after the response. Never stored.

## 4. Optional deferred table (NOT part of the MVP — do not materialize)

The only candidate durable table is a per-caregiver **UI preference**, built **only if** a concrete
presentation need appears. It is **explicitly out of MVP scope**; it is documented here as a plain (non-`sql`)
block so the migration generator does **not** emit it. When (and only when) approved, dash-service will add a
versioned MIG script `V<n>__dash_caregiver_ui_preference.sql` per `t_<empresa>` schema.

```text
-- schema: empresa   (DEFERRED — not part of MVP; do NOT generate)
CREATE TABLE caregiver_ui_preference (
    caregiver_id       uuid        NOT NULL,   -- ref -> Cuidador (ACS), tenant-scoped
    default_screen     varchar(64) NULL,       -- non-sensitive presentation setting
    notification_sound boolean     NOT NULL DEFAULT true,
    preferences        jsonb       NOT NULL DEFAULT '{}'::jsonb,  -- open, non-sensitive UI settings
    updated_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (caregiver_id)
);
COMMENT ON TABLE caregiver_ui_preference IS 'Non-sensitive per-caregiver UI presentation settings (deferred, optional).';
```

- **Invariants (EARS):**
  - IF the optional preference table is ever built, THEN THE SYSTEM SHALL store only non-sensitive
    presentation settings and SHALL NOT store any clinical, biometric or operational data.
  - THE SYSTEM SHALL resolve the preference row within the caregiver's tenant schema only.

## 5. Relations (read-only references)

DASH holds foreign references by **id only**, resolved live against owners — never as FK rows:

```
Cuidador(ACS) --sub--> [DASH JWT scope] --linkage(ACS)--> Paciente(PAC/ACS)
Paciente --> Comando(PAC) --> calibração(PAC)
Paciente --> Evento(EVNT) --> NotificationDispatch(EVNT) --dispatchId--> [DASH delivery]
Paciente --> DispositivoEdge(EDGE) --liveness poll--> [DASH derived status]
Paciente --> AuditRecord(HIST) --timeline read--> [DASH HistoryPage]
```

## 6. Migration plan

- **MVP:** no reserved Flyway range is consumed by DASH — zero scripts.
- **If deferred preference is approved:** a single `V<n>__dash_caregiver_ui_preference.sql` authored by DASH,
  applied by MIG across each `t_<empresa>` schema at provisioning/upgrade. DASH still runs no boot-time DDL.

## 7. Data lifecycle & LGPD

- **Retention:** DASH retains no business data — nothing to purge or archive.
- **Sessions:** ephemeral; expire on disconnect/timeout/restart; no session history kept.
- **Notifications:** no inbox in DASH; durable state and retention live in EVNT.
- **LGPD:** DASH persists essentially nothing personal (optional preferences are non-sensitive) → **no data
  subject to erasure** in the MVP. Erasure is realized in owner services and, at the tenant boundary, by
  MIG's tenant-schema drop. DASH never touches biometric/video data (project-wide hard rule).
