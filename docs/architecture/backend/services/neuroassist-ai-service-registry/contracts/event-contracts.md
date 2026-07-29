# REG — Event Contracts

> **No message broker.** `stack.messaging.enabled = false` (`_brief.md` §7). REG
> **neither produces nor consumes domain events**. Its relationship to every other
> module (GWY, MIG, GEST, EVNT, PAC, ACS, HIST, DASH, EDGE) is **registration +
> discovery over HTTP only**, never events.
>
> This file is intentionally empty of event definitions so the backlog/scaffold do
> not wire a broker producer/consumer into the registry.

## Events published

None.

## Events consumed

None.

## Idempotency / versioning

Not applicable — there are no events. Registration/heartbeat idempotency at the
Eureka protocol level (a repeated registration or renewal must not create a
duplicate lease) is covered in `spec/backend/SPEC-05-idempotency-concurrency.md`.

## Invariants

`THE SYSTEM SHALL NOT produce any broker event.`
`THE SYSTEM SHALL NOT consume any broker event.`
`THE SYSTEM SHALL relate to other modules exclusively through registration and discovery over HTTP.`
