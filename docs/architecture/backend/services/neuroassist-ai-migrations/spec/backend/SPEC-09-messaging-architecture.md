# SPEC-09 — Messaging Architecture

> **Not Applicable.** `stack.messaging.enabled = false` (project-context.json)
> — no message broker exists in the NeuroAssist AI MVP. This spec exists (per
> the mandatory SPEC set) to record the decision and its revisit condition,
> not to define a producer/consumer contract.

## Objective

Record why MIG has no messaging architecture in the MVP and what would
change if a broker is introduced later.

## Scope

- **In:** the decision (no broker), the revisit condition, the synchronous
  alternative already in place (ADR-005).
- **Out:** any producer/consumer contract (none exists).

## Technical decisions

- Tenant provisioning is invoked **synchronously** by ACS (ADR-005); this
  fully replaces what an event-driven "provision requested" flow would do in
  a platform with a broker.
- Lifecycle facts are recorded to the pipeline activity trail (D-423), not
  published to a topic (see `event-contracts.md`).
- **Revisit condition:** re-evaluate introducing a broker (e.g. RabbitMQ) "if
  the number of pacientes/edge agents grows" (stack note,
  project-context.json `stack.messaging.$comment`). If that happens, MIG
  would become an **emit-only** producer of schema-lifecycle notifications;
  it would still consume no event (provisioning stays synchronous, ADR-005
  is not expected to flip to async).

## Implementation

N/A — no producer, no consumer, no topic/queue configuration in this
service.

## Edge cases

N/A.

## Acceptance criteria (EARS)

`THE SYSTEM SHALL declare no message broker producer or consumer while stack.messaging.enabled is false.`
`IF a message broker is enabled in a future revision, THEN THE SYSTEM SHALL become an emit-only producer of schema-lifecycle notifications and SHALL still consume no event.`
