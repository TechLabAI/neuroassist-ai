# 02 — Architecture Blueprint — ACS (Frontend)

## 1. System boundary

ACS-FE is a **client of the backend** through
`@neuroassist/shared/access-http-base` → API Gateway (GWY). Uniquely among the modules,
ACS is the surface that **establishes** the tenant scope other modules ride on: creating
the `Tenant` and selecting it as active is what populates the `X-Empresa-Id`
interceptor (schema-per-empresa). It authors three things it owns — the `Tenant`
(empresa), the `Cuidador` roster, and the `Cuidador ↔ Paciente` linkage — and reads one
it does not: the `Paciente` records owned by PAC (referenced by id for linkage).

The browser holds **no authentication logic**: credential exchange is the shared
`auth-session` layer's job. ACS authors *authorization* (roles, linkage, coverage) on
top of an already-authenticated session. ACS also does **not** deliver alarms; it only
declares who is *eligible* to receive one, which EVNT consumes.

## 2. External actors and systems

| Actor / system | Role | Channel |
|---|---|---|
| Caregiver / coordinator / conductor (authenticated) | Authors tenant + roster + linkage | Browser UI |
| Shared `auth-session` | Provides the authenticated principal ACS assigns a role to | shared lib |
| API Gateway (GWY) | Tenant, caregiver, linkage CRUD | REST via `access-http-base` |
| PAC module | `Paciente` records to link caregivers to | shared contracts (read by id) |
| EVNT module | Consumes "alarm-eligible caregivers for this patient" for routing | shared contracts (read) |
| EDGE / PAC (wizard) | Contribute their onboarding step features composed by the ACS wizard | `scope:web` composition |
| HIST module | Records access/role/linkage changes as immutable audit facts | REST via GWY |

## 3. End-to-end user flows

### Golden path — invite a backup and link to the patient (roster)

```
[Roster open — 1 caregiver, patient shown "Coberto (1)"]
   coordinator/caregiver presses "Convidar cuidador" ---> caregiver form (draft)
        |
        v
   fills nome / papel (cuidadora | coordenador_clinica)
        |
   presses "Convidar" -----------------------------------> AcsCuidadorFacade.invite(draft)
        |                                                    POST /cuidadores (X-Empresa-Id)
   state: saving                                             |
        |                                                    v
   state: saved  <------------------------------------------+
        |
   selects patient(s) to cover, presses "Vincular" -------> AcsLinkFacade.link(cuidadorId, pacienteId)
        |                                                    POST /vinculos
        v
   patient coverage recomputes: "Coberto (2)"; grant recorded (HIST)
```

Alternative / edge cases:

```
empty roster (impossible post-onboarding)  -> onboarding wizard is the entry, not an empty list
unlink last covering caregiver             -> BLOCKED: "Este paciente ficaria sem cuidador responsável" (ADR-005)
deactivate last covering caregiver         -> BLOCKED with the same coverage reason (ADR-005)
patient with 0 alarm-eligible caregivers   -> patient row marked "Sem cobertura" (danger), never hidden
invite fails (ProblemDetail)               -> AppError toast (ui-feedback); draft preserved
role change to remove last coordinator     -> allowed (role ≠ coverage) but linkage coverage still enforced
```

### Secondary path — onboarding wizard (ACS-owned shell orchestrator)

```
[Onboarding wizard — ACS owns the scope:web shell page]
  step 1  Tenant (ACS feature)        -> create empresa, set active tenant
  step 2  Paciente (PAC feature)      -> composed: PatientRegisterComponent (@neuroassist/pac)
  step 3  Dispositivo (EDGE feature)  -> composed: device-pairing step (@neuroassist/edge)
  step 4  Primeiro comando (PAC)      -> composed: CommandEditorComponent (@neuroassist/pac)
  step 5  Cuidador + vínculo (ACS)    -> register primary caregiver, link to patient
        |
        v
  wizard completes -> patient covered; hand over the panel
```

The wizard **shell page lives in `scope:web`** and composes each module's step feature;
ACS owns the wizard's sequencing/breadcrumb, not the internals of PAC/EDGE steps
(ADR-006, doc 04 §5).

## 4. Internal boundaries (module slicing per module-pattern.md)

ACS follows the canonical NeuroAssist module slicing. The entry is a **library**
(ADR-006), not an app folder.

```
libs/acs/shell-entry/         (type:app, scope:acs)   # @neuroassist/acs — lazy entry
└── src/lib/
    ├── acs.module.ts                  # the module's only NgModule
    ├── acs.contract.ts                # MODULE_KEY + metadata + permissions + flags
    ├── acs.bootstrap.ts               # init/destroy lifecycle
    ├── acs.routes.ts                  # ACS_ROUTES[]  (roster, caregiver-editor, linkage)
    ├── acs.permission-map.ts          # papel -> permissions
    ├── acs.feature-flags.ts
    ├── acs.nav.config.ts
    └── acs.descriptor.ts              # ACS_MODULE_DESCRIPTOR (consumed by Shell)

apps/acs/                     (type:app, scope:acs)   # thin host: loads @neuroassist/acs
apps/acs-e2e/                 (type:e2e)              # Playwright specs

libs/acs/
├── data-access/  (type:data-access) # AcsTenantClient + AcsCuidadorClient + AcsLinkClient + facades (signals)
├── feature-tenant-setup/     (type:feature) # create empresa / active-tenant step
├── feature-caregiver-roster/ (type:feature) # roster list + coverage badges + empty/loading
├── feature-caregiver-editor/ (type:feature) # invite/register + role assignment form
├── feature-patient-linkage/  (type:feature) # bind/unbind caregivers ↔ patient + coverage guard
├── access/       (type:domain)       # acs-specific guard (coordinator-only affordances)
├── integration/  (type:domain)       # AcsShellIntegrationService
├── navigation/   (type:domain)       # breadcrumb/menu state
├── ui/ (opcional, type:ui)           # RoleBadge, CoverageBadge, CaregiverRow
└── styles/ (opcional)                # Tailwind utilities + tokens (no SCSS tokens)
```

**Composition rule (critical).** `scope:acs` **must not** import `scope:pac`,
`scope:edge` or `scope:gest`. The onboarding wizard sequences steps owned by other
modules; that composition happens only at the **`scope:web` shell-page level** (the sole
scope allowed to depend on every module). ACS exposes its own step features
(`TenantSetupComponent`, `CaregiverLinkStepComponent`) through its barrel; the shell
wizard page imports each module's step and wires shared session context between them.
See ADR-006, doc 04 §5, and PAC doc 04 §5 (same rule, PAC's patient step is composed
here).

## 5. Cross-cutting concerns

- **Auth / entitlement** — routes guarded by the shared `AccessDecisionService`; an
  acs-local guard additionally gates coordinator-only affordances (roster/role
  management) so a `cuidadora` cannot reach them.
- **Tenancy** — ACS is the module that **sets** the active tenant; thereafter every
  backend call (its own and other modules') carries `X-Empresa-Id` via the shared
  `access-http-base` interceptor (schema-per-empresa).
- **Observability** — access grant/revoke, role change and coverage transitions emitted
  via the shared observability lib (ids + role/coverage outcome only; no PII beyond the
  caregiver's display name).
- **Design tokens** — colors/typography from `@neuroassist/shared/ui-tokens`
  (`--color-danger` for *sem cobertura*, `success`, `brand`); no ad-hoc colors.
- **i18n** — UI copy pt-BR (human-facing); code/identifiers en-US.
- **LGPD** — `Cuidador` is a data subject; ACS collects the minimum (name + role +
  linkage) and stores no health data. Access/role changes are auditable (HIST) as a
  privacy-and-safety control.
