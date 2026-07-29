# ADR Index — API Gateway (GWY)

Architecture Decision Records for `neuroassist-ai-api-gateway`.

Per D-453, **shared (project-level)** decisions are recorded once under
`docs/architecture/backend/adr/` and merely referenced here; **local (service-level)**
decisions live in this folder.

## Índice

| ADR | Escopo | Título | Status |
|-----|--------|--------|--------|
| ADR-001 | shared | Hexagonal architecture | see `../../../adr/ADR-001-hexagonal-architecture.md` |
| ADR-002 | **local** | Single-service gateway (no decomposition) | aceita |
| ADR-003 | shared | Multi-tenancy (schema-per-empresa) | see `../../../adr/ADR-003-multi-tenancy.md` |
| ADR-004 | shared | Composition stance (independent microservices) | see `../../../adr/ADR-002-independent-microservices.md` |
| ADR-005 | **local** | Edge device authentication scheme | proposta |

> Messaging ADR is intentionally absent: `stack.messaging.enabled = false` — the gateway
> has no broker dependency and all downstream calls are synchronous over discovery.

## Mapa de Relações
- **ADR-002 (local)** depends on **ADR-001 (shared)** — the single service still follows
  the hexagonal/layering discipline (adapted to a filter pipeline).
- **ADR-005 (local)** depends on **ADR-003 (shared)** — the device token carries the
  `empresa_id` claim used to propagate tenant context; and on the EDGE pairing design
  (external, gap G-1).
- Both local ADRs are constrained by the project error model
  (`arch/decisions/error-model.arch.json`, RFC 7807).

## Processo
- Status possíveis: `proposta` | `aceita` | `substituída por ADR-NNN`.
- Local ADRs may differ per service; shared ADRs are uniform across all services and must
  not be duplicated here.
- ADR-005 is `proposta` pending confirmation of the EDGE device pairing flow before Specs.
