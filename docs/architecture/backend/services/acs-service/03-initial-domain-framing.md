<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Tenant (empresa):** the unit of care and isolation — a `familia` or a `clinica`. Root of the tenant
  tree; scopes every downstream aggregate. Owned by ACS.
- **Cuidador (caregiver):** a person with an account and a role who may access a tenant's data. Owned
  by ACS. Distinct from *patient* (owned by PAC) — a patient is never a user.
- **Papel (role):** `cuidadora` or `coordenador_clinica`; determines the authority set. `cuidadora`
  sees assigned patients; `coordenador_clinica` has tenant-wide administration.
- **Vínculo cuidador–paciente (caregiver–patient link):** the record that a caregiver is responsible
  for a patient; the authoritative source for alarm routing and data scope.
- **Credential:** the secret used to authenticate a caregiver, stored only as a hash.
- **Authority:** a granular permission code (e.g. `CAREGIVER_READ`, `PATIENT_ASSIGN`) checked by
  `@PreAuthorize`; derived from `papel`.
- **Access token (JWT):** the signed, tenant-scoped, authority-bearing credential ACS issues and peers
  verify against the JWK set.
- **JWK set:** the public keys ACS publishes so resource servers validate tokens without calling ACS.
- **Provisioning:** the act of creating a tenant and its dedicated schema (via MIG) before any
  tenant-scoped data can exist.

## Aggregates and Entities
> Draft — refined in Specs (`data-model.md`).

- **`Tenant`** (aggregate root, global schema) — `id`, `nome`, `tipo (familia|clinica)`,
  `status (provisioning|active|suspended)`. Owns the lifecycle of tenant provisioning.
- **`Cuidador`** (aggregate root, tenant-scoped) — `id`, `tenantId`, `nome`, `email`/login,
  `credentialHash`, `papel (cuidadora|coordenador_clinica)`, `status (active|inactive)`. An account is
  born into exactly one tenant and never moves.
- **`CaregiverPatientLink`** (aggregate root, tenant-scoped) — `id`, `tenantId`, `cuidadorId`,
  `pacienteId` (reference to PAC by id), `status (active|revoked)`. The anchor relationship.
- **Value objects:** `Papel`, `TenantTipo`, `Credential` (hash + algorithm), `Authority`,
  `SigningKey` (JWT key material, infrastructure-adjacent).

## Domain Events
> No event bus (messaging disabled). "Events" here are domain facts, materialized as **synchronous
> audit calls to HIST** and as state changes — not bus messages.

- **`TenantProvisionado`** — a tenant reached `active` (schema created via MIG).
- **`CuidadorRegistrado`** — a caregiver account was created.
- **`CuidadorVinculadoAoPaciente`** — the **anchor**: a caregiver was linked to a patient.
- **`CuidadorDesvinculado`** — a linkage was revoked.
- **`CuidadorDesativado`** — an account was deactivated (access ends on next token lifecycle).
- **`AutenticacaoRealizada` / `AutenticacaoFalhou`** — audit facts for sign-in success/failure.

## State Transitions
**Tenant:** `provisioning --(MIG schema created)--> active --(admin action)--> suspended`.
A tenant stuck in `provisioning` (MIG failed) never accepts caregivers; retry re-attempts provisioning.

**Cuidador:** `active --(deactivate)--> inactive`. An `inactive` caregiver cannot authenticate;
existing tokens expire naturally (short TTL) — no server-side session to revoke in the MVP.

**CaregiverPatientLink:** `active --(unlink)--> revoked`. A `revoked` link stops routing/scope on the
next lookup; historical audit references remain interpretable in HIST.

## Invariants
1. Every `Cuidador` and every `CaregiverPatientLink` belongs to exactly one `Tenant`; a link's
   `cuidador` and `paciente` are in the **same** tenant (no cross-tenant link).
2. A `Cuidador` can be created only against an `active` `Tenant`.
3. `credentialHash` is never null and never stored in cleartext; the raw credential never leaves the
   auth boundary.
4. `papel` is one of the declared roles; the `role → authorities` mapping is total (every role resolves
   to a defined authority set — ADR-005).
5. An issued JWT always carries a valid `tenantId` and a non-empty `authorities` claim; a token without
   a tenant is invalid by construction.
6. Only a `coordenador_clinica` (or the bootstrap conductor) may create caregivers or linkages within a
   tenant; a `cuidadora` may not self-elevate.
7. Deactivating a caregiver or revoking a link never hard-deletes the record — access is removed, audit
   history preserved.
8. No biometric/clinical data is ever an attribute of any ACS aggregate (LGPD hard rule).
