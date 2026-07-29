---
service: neuroassist-ai-api-gateway
prefix: GWY
displayName: API Gateway
kind: infrastructure
stack: Java 21 · Spring Boot 3.5 · Spring Cloud Gateway (reactive / WebFlux, Netty)
persistence: none (not a Flyway target)
messaging: none (stack.messaging.enabled = false)
tenancy: propagator only (X-Empresa-Id derived from principal; no schema opened)
stage: specs
---

# GWY — API Gateway — Service Spec (entry point)

> **Infrastructure service.** GWY is the single secure ingress of the NeuroAssist AI
> backend. It authenticates callers, resolves targets via the Service Registry, propagates
> tenant + correlation context, applies edge policy (rate limiting, CORS) and shapes typed
> errors. It owns **no** business domain, **no** database schema and produces **no** broker
> messages. This spec set makes the gateway implementable without re-reading the vision.

## Source vision (prerequisite, do not edit here)
- `_brief.md`, `01-architectural-vision.md`, `02-architecture-blueprint.md`,
  `03-initial-domain-framing.md`, `04-backend-architecture-direction.md`,
  `05-persistence-model.md`, `06-risks-gaps-assumptions.md`
- Local ADRs: `adr/ADR-002-single-service-gateway.md`,
  `adr/ADR-005-edge-device-authentication.md` (status **proposta** — see G-1)
- Shared ADRs: `../adr/ADR-001-hexagonal-architecture.md`,
  `../adr/ADR-003-multi-tenancy.md`

## Contract map
| Artifact | Path | Purpose |
|---|---|---|
| API / route surface | `contracts/api-contract.md` | Route table, management endpoints, error model, headers, auth |
| Data model | `contracts/data-model.md` | Declares **no persistence** (no SQL, not a MIG target) |
| Event contracts | `contracts/event-contracts.md` | Declares **no broker**; operational signals only |
| Hub integration | `contracts/hub-integration.md` | REG (Eureka), ACS (JWK/issuer), downstream services trust contract |
| Integration contracts | `contracts/integration-contracts.md` | External systems: Eureka, ACS well-known, LB/DNS, edge auth authority |
| Event catalog | `events/event-catalog.md` | Operational signal catalog (log lines / metrics, not messages) |
| Setup (local) | `setup-local.md` | Ports, env vars, run/test commands |

## SPEC map
| SPEC | Theme | Key ADR / invariant |
|---|---|---|
| SPEC-01 | Route / API surface | ADR-002 · INV-4 |
| SPEC-02 | Filter-pipeline orchestration (routing decision) | ADR-001 · INV-1..INV-6 |
| SPEC-03 | Edge device authentication | ADR-005 (proposta) · INV-1 · G-1 |
| SPEC-04 | Data model + migrations (none) | 05-persistence · INV-6 |
| SPEC-05 | Idempotency, concurrency & statelessness | INV-3 · INV-6 · R-3 |
| SPEC-06 | Security (JWT, device, header hardening, CORS, TLS) | ADR-003 · INV-1/2/5 · R-2 |
| SPEC-07 | Observability + health | R-6 (LGPD logs) |
| SPEC-08 | BDD test scenarios (maps `tests/cucumber/*.feature`) | all invariants |
| SPEC-09 | Messaging architecture (none) | A-4 |
| SPEC-10 | Integrations | REG, ACS, downstream, LB |

## Diagrams
- `diagrams/c4-context.puml` — actors + external systems around the gateway
- `diagrams/c4-container.puml` — gateway + REG + ACS + downstream services
- `diagrams/c4-component.puml` — internal filter pipeline & outbound ports

## Non-goals of this stage
No code, no issues/plan (backlog stage), no changes to docs 01–06, no new ADRs.
