---
service: neuroassist-ai-service-registry
prefix: REG
displayName: Service Registry
kind: infrastructure
stack: Java 21 · Spring Boot 3.5 · Spring Cloud Netflix Eureka Server
persistence: none (not a Flyway target)
messaging: none (stack.messaging.enabled = false)
tenancy: not applicable (platform layer, above schema-per-empresa)
stage: specs
---

# REG — Service Registry — Service Spec (entry point)

> **Infrastructure service.** REG is the Spring Cloud Netflix **Eureka server**: every
> other backend service registers here on startup and resolves its peers here. It owns
> **no** business domain, **no** database schema, and produces **no** broker messages.
> It **discovers, never routes** (routing/edge policy is GWY's job). This spec set makes
> the registry implementable without re-reading the vision.

## Source vision (prerequisite, do not edit here)
- `_brief.md`, `01-architectural-vision.md`, `02-architecture-blueprint.md`,
  `03-initial-domain-framing.md`, `04-backend-architecture-direction.md`,
  `05-persistence-model.md`, `06-risks-gaps-assumptions.md`
- Local ADRs: `adr/ADR-001-eureka-as-service-registry.md`,
  `adr/ADR-002-standalone-vs-ha-cluster.md`
- Shared ADRs: `../adr/ADR-001-hexagonal-architecture.md` (context only — REG does not
  apply the hexagonal layering), `../adr/ADR-003-multi-tenancy.md` (context only — not
  applicable, REG has no tenant concept)

## Contract map
| Artifact | Path | Purpose |
|---|---|---|
| API / protocol surface | `contracts/api-contract.md` | Eureka protocol behavior, lease/eviction, self-preservation, dashboard/health |
| Data model | `contracts/data-model.md` | Declares **no persistence** (no SQL, not a MIG target) |
| Event contracts | `contracts/event-contracts.md` | Declares **no broker**; lifecycle log signals only |
| Hub integration | `contracts/hub-integration.md` | REG as discovery target; every backend service as an Eureka client |
| Integration contracts | `contracts/integration-contracts.md` | External systems: none of substance; observability stack (outbound logs) |
| Event catalog | `events/event-catalog.md` | Operational signal catalog (log lines / metrics, not messages) |
| Setup (local) | `setup-local.md` | Port, env vars, run/test commands |

## SPEC map
| SPEC | Theme | Key ADR / source |
|---|---|---|
| SPEC-01 | Registry / protocol surface | ADR-001 · 02-architecture-blueprint §Internal Boundaries |
| SPEC-02 | Registry core (registration, lease, eviction, discovery) | ADR-001 · 03-initial-domain-framing §Invariants |
| SPEC-03 | Standalone posture and fail-open discovery | ADR-002 · 01-architectural-vision §Principle 3 |
| SPEC-04 | Data model + migrations (none) | 05-persistence-model.md |
| SPEC-05 | Idempotency and concurrency | 03-initial-domain-framing §Invariants 1-3 |
| SPEC-06 | Security | 02-architecture-blueprint §Cross-cutting Concerns · 06-risks-gaps-assumptions §Riscos |
| SPEC-07 | Observability + health | 02-architecture-blueprint §Cross-cutting Concerns |
| SPEC-08 | BDD test scenarios (maps `tests/cucumber/*.feature`) | all SPECs |
| SPEC-09 | Messaging architecture (none) | `_brief.md` §7 |
| SPEC-10 | Integrations | Eureka clients, observability stack |

## Diagrams
- `diagrams/c4-context.puml` — operator + Eureka clients around the registry
- `diagrams/c4-container.puml` — Eureka server + in-memory lease registry
- `diagrams/c4-component.puml` — bootstrap, registry core, dashboard, config, actuator

## Non-goals of this stage
No code, no issues/plan (backlog stage), no changes to docs 01-06, no new ADRs.

## Next action
-> /be-architect-backlog neuroassist-ai REG
