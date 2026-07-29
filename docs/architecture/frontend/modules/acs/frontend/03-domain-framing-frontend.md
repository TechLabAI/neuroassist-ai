# 03 — Domain Framing (Frontend) — ACS

## 1. Ubiquitous language (mirror of BE / `_brief.md`)

ACS-FE does **not** redefine domain terms; it mirrors the BE data model
(`_extensions.data_model` in project-context) and `_brief.md`. Terms used on this
surface:

| Term | Meaning (as used by ACS-FE) |
|---|---|
| **Tenant (empresa)** | The unit of care — `tipo = familia \| clinica`. Multi-tenant, schema-per-empresa. **ACS owns it** and establishes it as the active tenant (`X-Empresa-Id`). |
| **Cuidador** | A person with access inside a tenant. Carries `nome`, `papel`, `tenantId`, active/inactive. **ACS owns the roster.** |
| **Papel** | `cuidadora` (family responder) \| `coordenador_clinica` (clinic roster manager). Drives the permission map; it is **not** the same as coverage. |
| **Vínculo (linkage)** | The `Cuidador ↔ Paciente` responsibility bond. A linked, active caregiver is **alarm-eligible** for that patient. **ACS owns the linkage.** |
| **Paciente** | The monitored person, **owned by PAC**. ACS references it by id to link caregivers; it never authors the patient record. |
| **Cobertura (coverage)** | Client-derived per-patient fact: does the patient have ≥ 1 active, alarm-eligible (linked) caregiver? `coberto \| sem cobertura`. Not a BE enum — a UI-honesty projection (ADR-005). |
| **Alarm-eligible** | A caregiver who (a) is active and (b) is linked to the patient — the set EVNT may route an emergency to. ACS declares eligibility; EVNT delivers. |
| **Acesso concedido / revogado** | Access lifecycle events (grant on link, revoke on unlink/deactivate) — auditable facts recorded in HIST. |

## 2. View models (draft)

```ts
// roster row — what the caregiver roster renders per caregiver
interface CuidadorRowVM {
  id: string;
  nome: string;
  papel: 'cuidadora' | 'coordenador_clinica';
  ativo: boolean;
  patientsCovered: number;         // how many patients this caregiver is linked to
}

// caregiver editor form — mirrors the editable fields of Cuidador
interface CuidadorFormVM {
  nome: string;
  papel: 'cuidadora' | 'coordenador_clinica';
}

// patient linkage panel — coverage-honest view of one patient's responders
interface PatientCoverageVM {
  pacienteId: string;
  pacienteNome: string;            // read from PAC
  caregivers: LinkedCaregiverVM[]; // linked, with active flag
  coverage: 'coberto' | 'sem-cobertura';   // derived (ADR-005)
  isLastCoveringWarn: boolean;     // true when only one active covering caregiver remains
}

interface LinkedCaregiverVM {
  cuidadorId: string;
  nome: string;
  ativo: boolean;
}

interface TenantVM {
  id: string;
  nome: string;
  tipo: 'familia' | 'clinica';
}

type EditorState = 'idle' | 'editing' | 'saving' | 'saved' | 'error';
```

## 3. UI events (user → state)

| Event | Trigger | Effect |
|---|---|---|
| `createTenant` | onboarding step 1 "Criar empresa" | persist `Tenant`, set active tenant → session context |
| `inviteCaregiver` | "Convidar cuidador" | open editor with a blank draft → `editing` |
| `editCaregiver` | row "Editar" | open editor pre-filled → `editing` |
| `saveCaregiver` | "Convidar" / "Salvar" | validate → `saving` → `saved` \| `error` |
| `changeRole` | role selector | update `papel`; permission map re-evaluated |
| `deactivateCaregiver` | row "Desativar" | **coverage guard** → deactivate or block (ADR-005) |
| `linkCaregiver` | linkage panel "Vincular" | grant access (Cuidador ↔ Paciente); coverage recomputes |
| `unlinkCaregiver` | linkage panel "Desvincular" | **coverage guard** → revoke or block (ADR-005) |
| `cancelEdit` | "Cancelar" / navigate away | discard draft → `idle` |

## 4. State transitions (UI level)

```
Roster:    idle --inviteCaregiver/editCaregiver--> editing
Editor:    editing --saveCaregiver--> saving --ok--> saved --> idle(list reloads)
                   \--cancelEdit--> idle (draft discarded)
           saving --error(ProblemDetail)--> error --retry--> saving
Coverage:  coberto --unlink/deactivate(not last)--> coberto (recomputed)
           coberto --unlink/deactivate(last covering)--> BLOCKED (stays coberto; reason shown)
           sem-cobertura --link(active caregiver)--> coberto
```

Invariants on transitions:
- Leaving `editing` via cancel **always** discards the draft — nothing partial is
  persisted.
- A patient reaches `sem-cobertura` **only** through paths ACS cannot block (e.g. a
  patient created in PAC before any caregiver is linked) — never through an unlink or
  deactivation of its **last** covering caregiver, which is refused (ADR-005 local).

## 5. Invariants (client-side)

- `nome` must be non-empty (trimmed) and `papel` selected before a caregiver is savable.
- A `Tenant.tipo` is chosen once at creation (`familia | clinica`) and is not editable in
  MVP.
- **Coverage guard:** the UI must refuse any `unlinkCaregiver` / `deactivateCaregiver`
  that would drop a patient's active alarm-eligible caregiver count to **zero**, with an
  explicit reason — the caregiver must first link/activate another (ADR-005 local).
- **Role ≠ coverage:** changing a caregiver's `papel` never changes their linkage;
  losing the last `coordenador_clinica` is *allowed* (it is an admin-convenience role),
  but losing the last *covering* caregiver is *not*.
- Coordinator-only affordances (roster/role management) must be hidden and route-guarded
  for `papel = cuidadora`.
- A caregiver can be linked to a `Paciente` only within the **active tenant**; cross-tenant
  linkage is impossible by construction (schema-per-empresa).
- No health data is ever collected on a `Cuidador`; only `nome`, `papel`, linkage.
