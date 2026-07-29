# REG — Integration Contracts (External Systems)

> REG has **no external SaaS / PSP / AI / messaging / datastore integration** — those
> belong to the domain services (doc 04 §External Integrations). Its only couplings
> are platform infrastructure: in-fleet Eureka clients (inbound) and the platform
> observability stack (outbound logs/metrics).

## 1. Eureka clients (inbound registration / discovery)

| Aspect | Value |
|---|---|
| Type | Eureka server protocol (sync HTTP); REG is the server |
| Auth | Platform-internal (network trust); no external credential |
| SLA | Clients tolerate short registry blips via their cached registry |
| Fallback | Self-preservation keeps membership during a renewal-rate dip; never flush to empty on a blip |

`THE SYSTEM SHALL accept registration and discovery from in-fleet clients without an external credential (network-trusted plane).`
`WHILE the fleet-wide renewal rate dips transiently, THE SYSTEM SHALL preserve membership rather than evict en masse.`

## 2. Platform observability stack (outbound logs/metrics)

| Aspect | Value |
|---|---|
| Type | Structured lifecycle/access logs + Actuator metrics (`/actuator/prometheus`) |
| Auth | Platform-internal |
| SLA | Best-effort; logging/metrics collection never blocks the registry |
| Fallback | If the logging pipeline is unavailable, REG continues serving registrations/discovery |

`THE SYSTEM SHALL emit registration/renewal/eviction lifecycle logs as best-effort.`
`IF the logging pipeline is unavailable, THEN THE SYSTEM SHALL continue accepting registrations and answering discovery.`

## 3. Explicitly absent integrations

| Integration class | Present? | Why |
|---|---|---|
| PostgreSQL / datastore | No | no business or tenant state (doc 05) |
| Message broker | No | not an event participant (`stack.messaging.enabled = false`) |
| TechLab AI | No | consumes no AI capability (`ai.hasAi = false`) |
| External SaaS / PSP | No | talks only to in-fleet Eureka clients (doc 04) |
| ACS JWKS | No | REG makes no auth decision (that is GWY) |

`THE SYSTEM SHALL integrate only with in-fleet Eureka clients and the platform observability stack.`

## 4. Resilience strategy summary

| Dependency | On failure | Circuit/retry |
|---|---|---|
| Eureka clients (inbound) | self-preservation holds membership | no eviction during renewal-rate dip |
| Observability pipeline | continue serving; logs/metrics best-effort | no retry storm |

`THE SYSTEM SHALL keep serving discovery even when its only outbound dependency (logging/metrics) is degraded.`
