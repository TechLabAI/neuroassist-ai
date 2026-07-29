# REG — Data Model

> **Not Applicable — the registry owns no database (recorded decision).**
>
> Per `05-persistence-model.md`, REG is stateless of business data and participates
> in neither the `neuroassist_global` schema nor any tenant (`empresa`) schema. It
> contributes **no Flyway migration** to the centralized migrations service
> (`neuroassist-ai-migrations`).
>
> This file deliberately contains **no `sql` blocks**. `be-migration-generator.py`
> will therefore extract nothing for REG — which is the intended outcome. Do not add
> a schema here; that would wrongly provision a database for an infrastructure
> service whose only state is an in-memory lease registry.

## 1. Why there is no schema

- REG holds no business or tenant state and has no tenant concept (doc 01, doc 05).
- The only state REG holds is the **in-memory lease registry** (logical id →
  instances → status/expiry), which is operational, ephemeral, and rebuildable: on
  restart, clients re-register and the registry is reconstructed from their
  heartbeats (`03-initial-domain-framing.md` §Invariants #4).
- REG never stores or forwards patient/caregiver personal data
  (`03-initial-domain-framing.md` §Invariants #5) — it is out of the LGPD data-flow
  scope entirely.

## 2. Ephemeral runtime state (not persisted)

| Construct | Nature | Store |
|---|---|---|
| Lease registry (app → instances) | operational membership | Eureka in-memory store (per node) |
| Instance status (`UP`/`OUT_OF_SERVICE`/...) | operational | Eureka in-memory store |
| Lease expiry timers | operational | Eureka server timers |

`THE SYSTEM SHALL hold lease/membership state in memory only.`
`THE SYSTEM SHALL persist no business or tenant state.`
`WHEN the registry restarts, THE SYSTEM SHALL rebuild membership from client re-registration, not from a datastore.`

## 3. Migration plan

None. REG reserves no Flyway version range and writes no migration. The MIG service
(`neuroassist-ai-migrations`) must **not** allocate a schema for the registry.

## 4. Schema-level mapping

| Table | Schema |
|---|---|
| _(none)_ | _(none — registry owns no schema)_ |
