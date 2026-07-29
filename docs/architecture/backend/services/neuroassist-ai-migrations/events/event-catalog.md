# MIG — Event Catalog

> **Not Applicable.** No message broker (`stack.messaging.enabled = false`).
> MIG publishes no event and consumes none — see `contracts/event-contracts.md`.

| Event | Type | Payload | Known consumers |
|---|---|---|---|
| _(none)_ | — | — | — |

Lifecycle facts are recorded only to the pipeline activity trail (D-423), not
to a bus:

| Fact | Recorded when |
|---|---|
| Schema migrado | a target schema (global or tenant) reaches head |
| Empresa provisionada | a tenant schema is created + migrated to head |
| Migração falhou | a run fails on one or more schemas |
