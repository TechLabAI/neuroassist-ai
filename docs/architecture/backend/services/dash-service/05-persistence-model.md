<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
DASH is, by design, **nearly stateless**: it owns **no domain persistence** in the MVP. Every business fact
it shows or changes lives in an owner service (HIST, PAC, EVNT, EDGE, ACS), read live or written through
(ADR-004 / ADR-006 local). The one runtime "state" DASH holds — the **live SSE session registry** — is
**in-memory and ephemeral**, never a database row; it is rebuilt by panels reconnecting after a restart.
Should the optional per-caregiver **UI preference** ever be built, it follows the project's multi-tenant
**schema-per-empresa** model (ADR-003 shared): the table lives in each tenant schema `t_<empresa>`, is
tenant-scoped, resolved at request time, and never cross-schema. The global schema (`neuroassist_global`)
holds no DASH business data. **MVP expectation: zero DASH tables.**

## Initial Entities
> Draft persistence shape; the authoritative data model is frozen in Specs (`contracts/data-model.md`).

### (MVP) No persisted entities
The composition, delivery and proxy responsibilities require no durable store. `PanelViewModel`,
`NotificationEnvelope` and `PanelSession` are all transient/in-memory (doc 03). This is the intended
outcome, not an omission — persisting any of them would create a second, divergent copy of safety-critical
state that another service already owns and retries.

### `caregiver_ui_preference` (tenant schema — **optional, deferred**)
The only candidate durable table, built only if a concrete presentation need appears:

| Column | Type | Notes |
|---|---|---|
| `caregiver_id` | uuid (PK) | ref → Cuidador (ACS); tenant-scoped |
| `default_screen` | varchar | non-sensitive presentation setting |
| `notification_sound` | boolean | UI preference |
| `preferences` | jsonb | open, **non-sensitive** presentation settings |
| `updated_at` | timestamptz | last change |

No clinical, biometric or operational data — presentation only. If added, its single index is the PK
`(caregiver_id)` within the tenant schema.

### In-memory (not persisted)
- **SSE session registry** — `sessionId → {tenantId, caregiverId, connectionRef, connectedAt,
  lastHeartbeatAt}`. Lives in the process; dropped on disconnect/restart; drives delivery and on-connect
  catch-up. Deliberately not a table (its truth is the live TCP/SSE connection, which cannot outlive the
  process).

## Migration Strategy
Flyway is **centralized in the MIG service** (all schemas), not run standalone by dash-service (ADR-003
shared). In the MVP DASH contributes **no migration scripts** because it owns no tables. If the optional
`caregiver_ui_preference` table is later approved, dash-service authors a versioned script
(`V<n>__dash_caregiver_ui_preference.sql`) that MIG applies across each `t_<empresa>` schema at
provisioning/upgrade time; DASH still runs no boot-time DDL. New tenants would then receive the preference
table when MIG materializes their schema.

## Data Lifecycle
- **Retention:** DASH retains **no business data** — there is nothing to retain, purge or archive in the MVP.
  The history, events, calibration and audit trail all persist in their owner services with their own
  lifecycles.
- **Sessions:** SSE sessions are ephemeral and expire on disconnect, heartbeat timeout, or process restart;
  no session history is kept. A dropped session simply triggers on-connect catch-up when the panel returns.
- **Notifications:** DASH stores no inbox; a notification's durable state (`queued/delivered/acknowledged/
  failed`) and its retention live in EVNT (EVNT ADR-005). DASH only relays and reports delivery/ack.
- **Optional UI preferences (if built):** low-value, non-sensitive presentation settings; retained while the
  caregiver exists in ACS, removed with the tenant at off-boarding (tenant-schema drop). Never contains
  clinical or biometric data.
- **LGPD:** because DASH persists essentially nothing personal (no durable domain store; optional preferences
  are non-sensitive), it holds **no LGPD data subject to erasure** in the MVP — erasure is realized entirely
  in the owner services and, at the tenant boundary, by MIG's tenant-schema drop. DASH never touches
  biometric/video data (project-wide hard rule).
