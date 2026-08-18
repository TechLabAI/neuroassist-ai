# DASH — Hub / TechLab AI Integration

> Service: `dash-service` · `ai.hasAi = false`.

## Summary: NO hub / TechLab AI dependency

`dash-service` depends on **none** of the TechLab AI bounded contexts (orchestration, knowledge,
document_composition, analytics). This is a deliberate architectural constraint, not an omission:

- **The human path must be deterministic and inference-free.** DASH composes reads, delivers notifications
  and proxies writes. An emergency reaching the caregiver's screen must never depend on a model's latency or
  availability.
- **No Feign client to techlab-ai**, no bounded-context call, no snapshot consumption on any DASH path.

## What DASH depends on instead

DASH's runtime dependencies are the **project's own services**, not the hub:

| Owner service | Relationship | Contract |
|---|---|---|
| ACS (`acs-service`) | identity + caregiver↔patient scope (hard dependency) | `integration-contracts.md` §ACS |
| PAC (`pac-service`) | command catalog/calibration read + calibration write-through | `integration-contracts.md` §PAC |
| EVNT (`evnt-service`) | dispatch sink + unacked pull + delivery/ack + confirmation write-through | `event-contracts.md`, `integration-contracts.md` §EVNT |
| HIST (`hist-service`) | history/audit timeline read | `integration-contracts.md` §HIST |
| EDGE (`edge-service`) | device liveness poll | `integration-contracts.md` §EDGE |
| GWY / REG | routing (Spring Cloud Gateway) + discovery (Eureka) | ADR-002 shared |

## Propagation headers (shared platform convention)

Even without a hub dependency, DASH honors the platform's propagation contract on every outbound call:

- `X-Correlation-Id` — propagated end-to-end (browser → DASH → owner services).
- `Authorization` — service-to-service credential on DASH→owner calls; the caregiver JWT is **not** forwarded
  raw (DASH acts as a confused-deputy-safe proxy: it authorizes with the caregiver scope, then calls owners
  with its own service identity + explicit scope parameters).
- Tenant + caregiver scope — resolved from ACS and passed explicitly to owners as scope parameters, never as
  an ambient claim the owner must re-derive.

- **Invariant (EARS):** THE SYSTEM SHALL make **no** call to any TechLab AI bounded context on any request
  path.
