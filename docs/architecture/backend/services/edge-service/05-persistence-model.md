<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Schema-per-empresa (ADR-003 shared). A device belongs to exactly one unidade de cuidado
(família/clínica); all device rows live in that tenant's `t_<tenantId>` schema. `tenant_id` is
mirrored on every row for defense-in-depth (a row physically in `t_<x>` must also carry
`tenant_id = <x>`), matching PAC's convention so a schema-resolution bug cannot silently cross tenants.
The `neuroassist_global` schema holds **no** EDGE business data in the MVP.

## Initial Entities
> Table shapes are provisional; the authoritative data model is produced in `/be-architect-specs`.
> Base fields come from BA data-model `DispositivoEdge` (id, pacienteId, status, ultimaSincronizacao, versao).

- **`dispositivo_edge`** [empresa] — `id` (PK, uuid), `tenant_id`, `paciente_id` (cross-service ref →
  PAC, **not** a FK; null until paired), `pairing_state`
  (`registered|paired|unpaired|retired`), `versao` (agent version string, nullable until first
  heartbeat), `ultima_sincronizacao` (timestamptz, nullable until first heartbeat),
  `running_profile_version` (the profile version the device last reported, nullable),
  `row_version` (optimistic concurrency), `created_at`, `updated_at`.
  `status` (`online`/`offline`) is **derived at read time** from `ultima_sincronizacao` vs the staleness
  threshold — it is *not* a stored column (avoids a stale persisted flag; ADR-006). Unique active
  pairing: at most one row per `paciente_id` with `pairing_state = paired`.
- **`dispositivo_credential`** [empresa] — device-authentication material reference for `dispositivo_edge`
  (hashed secret / key id — never a plaintext secret), issued at registration, rotated on re-pairing
  (ADR-005). Exact shape confirmed in Specs alongside the auth mechanism.
- **`dispositivo_edge_transition`** [empresa] — append-only audit of pairing-lifecycle and liveness
  transitions: `dispositivo_edge_id`, `from_state`, `to_state`, `kind` (`pairing`|`liveness`), `actor`
  (operator or `system`), `reason`, `at`. Backs the safety audit and invariant #3/#5 (no silent change).

> Not stored here: `Paciente` (PAC), the detection profile / primitive catalog (GEST), `Evento` (EVNT),
> caregiver identity (ACS). EDGE stores only ids and version pointers across those boundaries.

## Migration Strategy
All DDL is owned by the **centralized MIG service** (Flyway across all schemas). `edge-service` ships
**no** local Flyway migrations — it contributes its migration scripts to MIG's managed set. Every
empresa schema receives the same EDGE tables; the global schema receives none for EDGE in the MVP.

## Data Lifecycle
- **`dispositivo_edge`:** long-lived; a replaced device is `retired`, never hard-deleted, so historical
  `Evento`/`Comando` rows that reference `dispositivo_edge_id` keep resolving (referential honesty for a
  safety product).
- **`ultima_sincronizacao` / `running_profile_version`:** high-churn columns updated on every heartbeat;
  only the latest value is kept on the aggregate (the transition audit captures liveness *changes*, not
  every heartbeat, to avoid unbounded growth).
- **`dispositivo_edge_transition`:** append-only, retained for the full pilot — it is safety evidence
  (when did monitoring drop, who re-paired) not operational churn.
- **`dispositivo_credential`:** rotated on re-pairing; superseded secrets are revoked, never left live.
- **LGPD (`lgpd = true`):** EDGE stores device metadata plus a `paciente_id` reference — the latter is
  personal data *by association*, kept inside the per-empresa schema and subject to the tenant's
  retention/erasure. No video, image, landmark or other biometric data is ever accepted or persisted
  (privacy invariant inherited from GEST doc 02). On patient erasure, the `paciente_id` link is cleared
  and the device unpaired/retired; the device row itself carries no personal content beyond that link.
