<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

> REG has **no business domain**. The "domain" here is the operational vocabulary of
> service discovery (Eureka). It is documented for completeness and to keep the
> pipeline schema consistent; there are no persisted business aggregates.

## Ubiquitous Language
- **Registry:** the authoritative in-memory table of live service instances.
- **Application:** a logical service name (e.g. `NEUROASSIST-AI-API-GATEWAY`) that
  groups one or more instances.
- **Instance:** a concrete running process registered under an Application
  (instance id, host, port, health URL, metadata).
- **Lease:** the time-bounded contract that keeps an Instance in the Registry; kept
  alive by renewals.
- **Heartbeat / Renewal:** a periodic signal from a client that renews its Lease.
- **Eviction:** removal of an Instance whose Lease expired.
- **Self-preservation:** protective mode where REG stops evicting when renewals drop
  below a threshold (assumes a network partition, not mass death).

## Aggregates and Entities
Conceptual (managed by the Eureka engine, **not** modelled or persisted by us):
- **Registry** (aggregate root, in-memory) → holds many **Application** entries.
- **Application** → holds many **Instance** entries.
- **Instance** → owns exactly one **Lease**.

No JPA entities, no repositories, no tables are authored for REG.

## Domain Events
REG publishes **no** domain events to any broker (`stack.messaging.enabled = false`).
Internally, Eureka raises lifecycle transitions that surface only in the dashboard
and logs:
- `InstanceRegistered` — a new Lease was created.
- `LeaseRenewed` — a heartbeat kept an Instance alive.
- `InstanceCanceled` — a client deregistered gracefully.
- `InstanceEvicted` — a Lease expired and the Instance was removed.

## State Transitions
Instance lifecycle (high level):
```
(none) --register--> UP --renew--> UP
UP --graceful shutdown / cancel--> REMOVED
UP --no heartbeat within lease window--> DOWN(expired) --evict--> REMOVED
```
Registry-level: `NORMAL <-> SELF_PRESERVATION` depending on the observed renewal rate.

## Invariants
1. An Instance is only discoverable while its Lease is active.
2. Each Instance belongs to exactly one Application (logical name).
3. Instance ids are unique within an Application.
4. On REG restart the Registry starts empty and is rebuilt solely by client
   re-registration — no state is recovered from disk.
5. REG never stores or forwards patient/caregiver personal data.
