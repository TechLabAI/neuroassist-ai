<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Schema-per-tenant (ADR-003 shared). `Paciente` and `Comando` are **tenant data**, living in each
empresa's schema (`t_<tenantId>` / per-empresa schema), never in `neuroassist_global`. The global schema
holds only cross-tenant registry data owned by other services (e.g. tenant directory in ACS) — PAC reads
tenant validity by reference, it does not store global rows. Every query and mutation is tenant-scoped;
there is no cross-empresa read path in PAC.

## Initial Entities
> Draft — refined in Specs (`data-model.md`). Types indicative.

**`paciente`** (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `tenant_id` | uuid | empresa (ACS) — scoping key |
| `nome` | text | |
| `dispositivo_edge_id` | uuid null | reference to EDGE `DispositivoEdge` (by id) |
| `created_at` / `updated_at` | timestamptz | audit |

**`comando`** (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `paciente_id` | uuid FK → `paciente.id` | |
| `rotulo` | text | free-text family label (non-empty) |
| `necessidade` | text | the mapped need |
| `primitivo_principal_id` | uuid | reference to GEST `TipoGesto` (by id) |
| `primitivo_secundario_id` | uuid null | composite gesture (by id) |
| `sensibilidade` | text/enum | `baixa` \| `media` \| `alta` |
| `tempo_min_gesto_ms` | int | > 0, ≤ `tempo_max_gesto_ms` |
| `tempo_max_gesto_ms` | int | ≥ `tempo_min_gesto_ms` |
| `urgencia` | text/enum | `emergencia` \| `rotina` |
| `estado` | text/enum | `drafted` \| `validated` \| `published` \| `retired` |
| `calibracao_versao` | int | monotonic, bumped on recalibration |
| `created_at` / `updated_at` | timestamptz | audit |

- Uniqueness: consider a per-patient guard against duplicate active mappings of the same
  gesture+need (refined in Specs).
- Primitive ids (`primitivo_*_id`) are **not** FKs — they reference GEST-owned `TipoGesto` across a
  service boundary; integrity is enforced by the synchronous eligibility gate, not a DB constraint.

## Migration Strategy
Flyway is **centralized in the MIG service** (`neuroassist-ai-migrations`), which owns and applies
migrations across all schemas — PAC ships its migration scripts to MIG, it does not run an embedded
Flyway. New PAC tables/columns are added as versioned scripts applied to every empresa schema by MIG.
This keeps schema evolution consistent across tenants and services (see MIG module).

## Data Lifecycle
- **Retention:** `Paciente` and `Comando` are long-lived catalog data; retired commands are **soft
  data** kept indefinitely so historical `Evento`/audit references stay interpretable (ADR-006). No hard
  delete of a command that has been referenced by an event.
- **Versioning:** calibration changes bump `calibracao_versao` in place for a `published` command; a
  material change to a referenced command that would alter historical meaning is modeled as retire +
  new command, not in-place mutation.
- **Archival:** none in the MVP; if a patient leaves care, the catalog is retired (not purged) subject to
  LGPD data-subject rights, which are handled at the tenant/ACS level, not by ad-hoc PAC deletion.
- **Privacy:** no biometric/image/landmark data is ever persisted by PAC — only catalog metadata and
  primitive ids (project-wide LGPD hard rule).
