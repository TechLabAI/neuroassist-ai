# Architecture Decision Records — ACS (acs-service, local)

Service-level decisions for `acs-service` (module **ACS — Cuidadores e Acesso**). These build on the
shared, project-level ADRs and record only what is specific to this service. ACS holds a special role:
it is the identity/access authority and the multi-tenancy provisioning root of the platform.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-004 | ACS as tenant and identity provisioning authority (schema request delegated to MIG) | aceita |
| ADR-005 | Local authentication and RBAC authority model (ACS = OAuth2 authorization server; JWT + JWK) | aceita |
| ADR-006 | Caregiver-patient linkage as single source of routing and data-scope | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one.

## Relation map
- **ADR-004** applies **ADR-003** (multi-tenancy): ACS decides *that* a `t_<tenantId>` schema must
  exist and requests it from MIG, which still owns the DDL. It is the provisioning root of the tenant
  tree every other service scopes to.
- **ADR-005** applies **ADR-001** (hexagonal) and realizes the constitution's RBAC aspect: ACS is the
  OAuth2 authorization server; the `role → authorities` mapping feeds the `@PreAuthorize` checks every
  resource server enforces. Depends on ADR-004 (a token always carries a valid provisioned `tenantId`).
- **ADR-006** depends on **ADR-004/005** — the caregiver↔patient linkage is only meaningful inside a
  provisioned, isolated tenant, and its role-based scope is what the authorities in ADR-005's token
  ultimately gate. It is the single source both EVNT (routing) and entity-scope (data filters) read.
- **ADR-003** (multi-tenancy) scopes every `Cuidador`, linkage and token per empresa; the `Tenant`
  directory itself is the only global-scoped ACS data.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
