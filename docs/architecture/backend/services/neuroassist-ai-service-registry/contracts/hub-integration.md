# REG — Hub / Platform Integration

> For an infrastructure registry service, the "hub" is inverted: REG is the
> **dependency target** of the platform, not a dependent. Every other service and the
> gateway are Eureka *clients* of REG; REG calls **no** hub service and consumes
> **no** TechLab AI capability (`ai.hasAi = false`). This document records the
> registration/discovery couplings and the membership contract REG offers its
> clients.

## 1. TechLab AI capabilities

| Capability | Used? |
|---|---|
| orchestration | no |
| knowledge | no |
| document_composition | no |
| analytics | no |

`THE SYSTEM SHALL NOT invoke any TechLab AI capability.`

## 2. REG as the discovery backbone

| Aspect | Value |
|---|---|
| Role | Eureka server: every service registers here and resolves peers here |
| Mode | Synchronous HTTP (Eureka client/server protocol); membership is in-memory |
| Direction | Clients call REG (register/renew/query); REG calls no one back |
| Failure mode for a client | If REG is briefly unreachable, clients route on their **cached** registry; REG self-preservation avoids flushing membership |

`THE SYSTEM SHALL accept registration, renewal, de-registration, and discovery queries from in-fleet Eureka clients.`
`THE SYSTEM SHALL NOT call any other service.`
`THE SYSTEM SHALL advertise only instances currently renewing their lease and reporting UP.`

## 3. Eureka clients (all backend services + GWY)

Pure dependents — they register with and resolve through REG. REG never calls them.

| Service | Logical id | Module | Relationship to REG |
|---|---|---|---|
| neuroassist-ai-api-gateway | `NEUROASSIST-AI-API-GATEWAY` | GWY | registers + resolves `lb://` targets (most discovery-dependent) |
| neuroassist-ai-migrations | `NEUROASSIST-AI-MIGRATIONS` | MIG | registers (one-shot/scheduled job) |
| gest-service | `GEST-SERVICE` | GEST | registers + resolves peers |
| evnt-service | `EVNT-SERVICE` | EVNT | registers + resolves peers |
| pac-service | `PAC-SERVICE` | PAC | registers + resolves peers |
| acs-service | `ACS-SERVICE` | ACS | registers + resolves peers |
| hist-service | `HIST-SERVICE` | HIST | registers + resolves peers |
| dash-service | `DASH-SERVICE` | DASH | registers + resolves peers |
| edge-service | `EDGE-SERVICE` | EDGE | registers + resolves peers |

`THE SYSTEM SHALL be the single discovery source from which GWY resolves every lb:// route target.`
`WHEN a new or scaled instance registers, THE SYSTEM SHALL advertise it so consumers discover it without reconfiguration.`
`WHEN an instance stops heartbeating beyond the lease window, THE SYSTEM SHALL stop advertising it so consumers are not sent to a dead instance.`

## 4. Header / context propagation

REG carries no business request context. It does not resolve tenants, does not read
`X-Empresa-Id`, and does not propagate it — the registration and discovery protocol
is tenant-agnostic.

`THE SYSTEM SHALL NOT read, derive, or propagate any tenant (empresa) context.`
`THE SYSTEM SHALL participate in correlation only for its own lifecycle/access logs (SPEC-07), not for forwarded business requests.`
