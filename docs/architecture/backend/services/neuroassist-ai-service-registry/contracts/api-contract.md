# REG — API Contract (Registry Surface)

> REG exposes **no business resource of its own**. Its "API surface" is the Spring
> Cloud Netflix **Eureka server protocol** (registration, lease renewal,
> de-registration, discovery query) plus the built-in Eureka **dashboard** and the
> Actuator health/readiness/metrics endpoints. This contract defines that surface, the
> required behaviors, and the membership/health invariants. REG **discovers, never
> routes**: it answers "where is service X?" and never proxies business traffic
> (that is GWY's job).
>
> EARS invariants per local ADR-001 / ADR-002. Guide:
> `docs/architecture/backend/standards/ears-notation.md`.

## 1. Nature of the surface

REG does not author a REST contract by hand — the surface is the standard Eureka
HTTP protocol provided by `spring-cloud-starter-netflix-eureka-server`, plus the
dashboard and Actuator. Clients use the Eureka client library (`eureka.client.*`),
not raw HTTP, so the protocol is documented here as the authoritative behavioral
contract rather than as endpoints to implement.

`THE SYSTEM SHALL expose the standard Eureka server registration and discovery protocol.`
`THE SYSTEM SHALL NOT proxy, route, or compose any business API traffic.`
`THE SYSTEM SHALL author no business REST resource of its own.`

## 2. Auth model

- REG makes **no authentication or authorization decision**. Edge caregiver/device
  token validation is GWY's job (ADR-005 at GWY); domain authz stays in the business
  services.
- The registration/discovery plane is **platform-internal** (network trust): in the
  deployed topology only in-fleet services and the gateway reach REG; REG is never
  exposed at the public edge.

`THE SYSTEM SHALL NOT validate caregiver JWT or edge device token.`
`THE SYSTEM SHALL NOT make any authorization decision.`
`THE SYSTEM SHALL treat the registration/discovery plane as platform-internal (network trust), not public.`

## 3. Eureka protocol surface (behavioral contract)

The Eureka server protocol, expressed as behavior. `{app}` is a logical service id
(e.g. `NEUROASSIST-AI-API-GATEWAY`); `{instanceId}` identifies a registered instance.

| Interaction | Eureka operation | Behavior |
|---|---|---|
| Register | `POST /eureka/apps/{app}` | record instance lease under the logical id, status `UP` |
| Renew (heartbeat) | `PUT /eureka/apps/{app}/{instanceId}` | refresh lease expiry while healthy |
| De-register | `DELETE /eureka/apps/{app}/{instanceId}` | remove instance on graceful shutdown |
| Query all | `GET /eureka/apps` | full registry snapshot (all apps + instances) |
| Query one app | `GET /eureka/apps/{app}` | instances for a logical id |
| Status override | `PUT /eureka/apps/{app}/{instanceId}/status` | operator/OUT_OF_SERVICE toggle |

`WHEN a service instance registers under a logical id, THE SYSTEM SHALL record its lease and advertise it as UP.`
`WHEN a registered instance renews its lease within the lease window, THE SYSTEM SHALL keep it advertised.`
`WHEN an instance de-registers gracefully, THE SYSTEM SHALL remove it from discovery immediately.`
`WHEN a discovery query arrives for a logical id, THE SYSTEM SHALL return only instances currently advertised as UP.`
`IF an instance reports a non-UP status, THEN THE SYSTEM SHALL NOT advertise it for discovery.`

## 4. Lease lifecycle and eviction

| Setting | MVP value | Meaning |
|---|---|---|
| Lease renewal interval (client) | 30 s (Eureka default) | how often a client heartbeats |
| Lease expiration duration | 90 s (Eureka default) | grace before an instance is eviction-eligible |
| Eviction loop | periodic (server timer) | removes expired leases when self-preservation allows |

`WHILE an instance keeps renewing its lease within the expiration window, THE SYSTEM SHALL keep it in the registry.`
`IF an instance stops renewing its lease beyond the expiration window, THEN THE SYSTEM SHALL make it eviction-eligible.`
`WHEN the eviction loop runs and self-preservation is not active, THE SYSTEM SHALL remove expired instances from discovery.`

## 5. Self-preservation

Self-preservation protects the lone registry node from flushing the registry when a
transient network blip causes many clients to miss heartbeats at once. This is
Eureka's default protective behavior, sized for the pilot's small fleet
(`06-risks-gaps-assumptions.md` §Riscos — "Self-preservation false positives").

`WHILE the observed renewal rate drops below the self-preservation threshold, THE SYSTEM SHALL stop evicting instances and preserve the last known registry.`
`WHEN the renewal rate recovers above the threshold, THE SYSTEM SHALL resume normal eviction.`
`THE SYSTEM SHALL prefer stale-but-present membership over an empty registry during a renewal-rate dip.`

## 6. Dashboard and health surface

| Path | Purpose | Notes |
|---|---|---|
| `/` (Eureka dashboard) | operator view of registered apps/instances | built-in status page |
| `/actuator/health` | liveness | server up |
| `/actuator/health/readiness` | readiness | registry accepting registrations |
| `/actuator/prometheus` | metrics | registered apps, renewals/min, evictions |

`THE SYSTEM SHALL expose the Eureka dashboard for operator inspection of the live fleet.`
`THE SYSTEM SHALL expose liveness, readiness, and metrics endpoints separate from the Eureka protocol surface.`
`WHILE the registry is not yet accepting registrations, THE SYSTEM SHALL report readiness as not-ready.`

## 7. Single-node topology (ADR-002)

For the MVP, REG is a single node and does not register with or fetch from itself.

`THE SYSTEM SHALL run as a single registry node for the MVP (register-with-eureka=false, fetch-registry=false).`
`WHERE peer-replicated HA is later enabled, THE SYSTEM SHALL replicate registry state across peers (deferred — ADR-002 Consequências).`
