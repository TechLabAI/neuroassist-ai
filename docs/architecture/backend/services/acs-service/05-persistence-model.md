<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Schema-per-tenant (ADR-003 shared) — but ACS is special: it straddles both schema tiers. The
**`Tenant` directory** and the **JWT signing-key material** are **global** data, living in
`neuroassist_global` (they exist before any tenant schema does, and describe the tenants themselves).
The **`Cuidador`** account and the **`CaregiverPatientLink`** are **tenant data**, living in each
empresa's schema (`t_<tenantId>` / per-empresa schema) — a caregiver belongs to exactly one tenant and
never appears in another's schema. There is no cross-tenant read path over caregiver/linkage data; the
only global-scoped read is the tenant directory itself.

## Initial Entities
> Draft — refined in Specs (`data-model.md`). Types indicative.

**`tenant`** (global schema `neuroassist_global`)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | `uuid_generate_v7()` (PS.001) |
| `nome` | text | display name |
| `tipo` | text/enum | `familia` \| `clinica` |
| `status` | text/enum | `provisioning` \| `active` \| `suspended` |
| `schema_name` | text | resolved `t_<tenantId>` created by MIG |
| `criado_em` / `atualizado_em` / `criado_por_usuario_id` / `atualizado_por_usuario_id` | — | audit (PS.002) |

**`cuidador`** (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | `uuid_generate_v7()` |
| `tenant_id` | uuid | scoping key (logical FK to global `tenant.id`, PS.003) |
| `nome` | text | |
| `login` | text | unique per tenant (email or username) |
| `credential_hash` | text | bcrypt/argon2; never cleartext |
| `papel` | text/enum | `cuidadora` \| `coordenador_clinica` |
| `status` | text/enum | `active` \| `inactive` |
| audit cols | — | PS.002 |

**`caregiver_patient_link`** (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `tenant_id` | uuid | scoping key |
| `cuidador_id` | uuid FK → `cuidador.id` | same-tenant |
| `paciente_id` | uuid | reference to PAC `Paciente` (logical FK, not a DB FK — PS.003) |
| `status` | text/enum | `active` \| `revoked` |
| audit cols | — | PS.002 |

- `paciente_id` is **not** a DB FK — it references a PAC-owned aggregate across a service boundary;
  integrity is validated by ACS at link time via `AcsDirectoryPort`/PAC resolve, not by a constraint.
- Uniqueness: one `active` link per (`cuidador_id`, `paciente_id`); `login` unique per tenant.
- JWT signing keys are stored/rotated in `neuroassist_global` (or an external secret store in a later
  iteration) — refined in Specs.

## Migration Strategy
Flyway is **centralized in the MIG service** (`neuroassist-ai-migrations`). Two facets specific to ACS:
1. **Own DDL:** ACS ships its `tenant` / `cuidador` / `caregiver_patient_link` migration scripts to
   MIG; MIG applies the global-schema scripts once and the tenant-schema scripts to every empresa
   schema. ACS never runs embedded Flyway.
2. **Runtime provisioning:** because ACS *creates tenants*, it triggers MIG at runtime to materialize a
   new `t_<tenantId>` schema from the canonical tenant template (ADR-004). This is a provisioning call,
   distinct from version migration; ordering is global-first, then per-tenant (ADR-003 shared).

## Data Lifecycle
- **Retention:** tenants, caregivers and links are long-lived. Deactivation/revocation are **soft**
  (`status` change), never hard deletes — historical audit references in HIST stay interpretable.
- **Credentials:** stored only as hashes; on a password change the old hash is overwritten. No
  credential history in the MVP.
- **Keys:** JWT signing keys rotate on a schedule; the JWK set publishes current + recently-retired
  public keys so in-flight tokens validate through rotation.
- **LGPD data-subject rights:** ACS is the owner of the delete/export path for a caregiver's personal
  data (as the identity system of record); this is handled here, not by ad-hoc deletes in other
  services. A tenant off-boarding suspends the tenant and its caregivers rather than purging, subject to
  legal retention of audit trails in HIST.
- **Privacy:** no biometric/image/landmark/clinical data is ever persisted by ACS — identity and access
  metadata only (project-wide LGPD hard rule).
