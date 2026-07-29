# MIG — Event Contracts

> **Not Applicable — no message broker.** `stack.messaging.enabled = false`
> platform-wide (project-context.json). MIG publishes no event to a bus and
> consumes none. Lifecycle facts — **"Schema migrado"** and **"Empresa
> provisionada"** (the two domain anchor events per `_brief.md` §8) plus
> **"Migração falhou"** — are recorded only to the pipeline activity trail
> (D-423), best-effort: an unavailable Elasticsearch never blocks or fails a
> migration run (doc 06, Dependências Críticas).

## Revisit condition

If a message broker is enabled later (stack note: reconsider if the number of
patients/edge agents grows), MIG would become an **emit-only** producer of
`schema.migrated` / `empresa.provisionada` / `migracao.falhou` — mirroring
the pattern used by other TechLab platforms with a broker. No consumer would
be added: MIG is never *triggered* by an event; provisioning is always a
synchronous call from ACS (ADR-005).

`THE SYSTEM SHALL publish no event to a message broker while stack.messaging.enabled is false.`
`THE SYSTEM SHALL record schema-lifecycle facts (schema migrado, empresa provisionada, migração falhou) to the pipeline activity trail on a best-effort basis, never blocking on it.`
