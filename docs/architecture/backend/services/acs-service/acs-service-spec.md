---
module: ACS
service: acs-service
package: com.neuroassist.acs
version: "1.0.0"
status: specs-complete
port: 8088
adrs:
  - ADR-004-tenant-identity-provisioning-authority.md
  - ADR-005-authentication-rbac-authority-model.md
  - ADR-006-caregiver-patient-linkage-data-scope.md
generatedAt: "2026-07-29"
---

# acs-service Spec Entry Point

> Module: **ACS — Cuidadores e Acesso** · Service: `acs-service`
> Package: `com.neuroassist.acs` · Multi-tenant: schema-per-empresa (ACS straddles both tiers)
> Pipeline phase: **specs** complete (contracts + SPECs + Gherkin features + C4 diagrams)

## Purpose

`acs-service` is the **identity and access authority** of NeuroAssist AI. It answers *who is this
person*, *what role do they have*, *which tenant do they belong to*, and *which patient(s) are they
responsible for*. It owns:

- The `Tenant` (empresa — `familia | clinica`) directory in `neuroassist_global` and its provisioning
  lifecycle (via MIG).
- The `Cuidador` account (credentials, `papel`) — tenant-scoped.
- The `CaregiverPatientLink` — the authoritative source of alarm **routing** and data **scope** consumed
  by EVNT/EDGE/DASH/HIST.
- The platform's **OAuth2 authorization server**: it mints short-TTL JWTs (`sub`, `tenant`,
  `authorities`) and publishes a **JWK set** every other service validates against locally.

ACS holds identity and access metadata **only** — never biometric, image, landmark, or clinical event
data (LGPD hard rule). Credentials are stored hashed (bcrypt/argon2).

## Document map

| Document | Path | Status |
|---|---|---|
| Vision (01-06) | `01..06-*.md` | complete (M1) |
| ADR-004 tenant/identity provisioning authority | `adr/ADR-004-tenant-identity-provisioning-authority.md` | complete (M1) |
| ADR-005 authentication + RBAC authority model | `adr/ADR-005-authentication-rbac-authority-model.md` | complete (M1) |
| ADR-006 caregiver↔patient linkage data-scope | `adr/ADR-006-caregiver-patient-linkage-data-scope.md` | complete (M1) |
| **API contract** | `contracts/api-contract.md` | complete (M2) |
| **Data model** | `contracts/data-model.md` | complete (M2) |
| **Event contracts** | `contracts/event-contracts.md` | complete (M2) |
| Hub integration | `contracts/hub-integration.md` | complete (M2) |
| Integration contracts | `contracts/integration-contracts.md` | complete (M2) |
| SPEC-01 API surface | `spec/backend/SPEC-01-api-surface.md` | complete |
| SPEC-02 Identity & tenant provisioning | `spec/backend/SPEC-02-identity-tenant-provisioning.md` | complete |
| SPEC-03 Authentication & token issuance | `spec/backend/SPEC-03-authentication-token-issuance.md` | complete |
| SPEC-04 Data model + migrations | `spec/backend/SPEC-04-data-model-migrations.md` | complete |
| SPEC-05 Idempotency + concurrency | `spec/backend/SPEC-05-idempotency-concurrency.md` | complete |
| SPEC-06 Security | `spec/backend/SPEC-06-security.md` | complete |
| SPEC-07 Observability + health | `spec/backend/SPEC-07-observability-health.md` | complete |
| SPEC-08 BDD test scenarios | `spec/backend/SPEC-08-bdd-test-scenarios.md` | complete |
| SPEC-09 Messaging architecture | `spec/backend/SPEC-09-messaging-architecture.md` | complete |
| SPEC-10 Integrations | `spec/backend/SPEC-10-integrations.md` | complete |
| Gherkin features (4) | `tests/cucumber/*.feature` | complete |
| Event catalog | `events/event-catalog.md` | complete |
| C4 diagrams | `diagrams/c4-{context,container,component}.puml` | complete |
| Setup local | `setup-local.md` | complete |

## Key invariants at a glance

| # | Invariant | Enforcement |
|---|---|---|
| 1 | Every `Cuidador`/`CaregiverPatientLink` belongs to exactly one `Tenant`; a link's caregiver and patient are same-tenant | `EmpresaContextHolder` + service same-tenant check → `422 cross-tenant-link` |
| 2 | A `Cuidador` can be created only against an `active` `Tenant` | `CreateCaregiverUseCase` precondition → `409 tenant-not-active` |
| 3 | `credential_hash` never null, never cleartext; raw credential never leaves the auth boundary | bcrypt/argon2 `DelegatingPasswordEncoder`; `NOT NULL` column |
| 4 | `papel → authorities` mapping is total (every role resolves to a defined authority set) | `RoleAuthorityPolicy` (ADR-005) |
| 5 | An issued JWT always carries a valid `tenant` claim + non-empty `authorities` | `TokenIssuer` invariant; token without tenant invalid by construction |
| 6 | Only `coordenador_clinica` (or bootstrap conductor) may create caregivers/links | `@PreAuthorize` authorities `acs.cuidador.criar` / `acs.vinculo.criar` |
| 7 | Deactivation/revocation is soft (`status` change), never hard delete | `status` enums; no `DELETE` in application code |
| 8 | Tenant marked `active` only after MIG confirms schema (fail-closed saga) | `ProvisionTenantUseCase` state machine (ADR-004) |
| LGPD | No biometric/clinical data ever an attribute of any ACS aggregate | schema review; privacy invariant enforced in data-model |

## Next step

`/be-architect-backlog neuroassist-ai ACS`
