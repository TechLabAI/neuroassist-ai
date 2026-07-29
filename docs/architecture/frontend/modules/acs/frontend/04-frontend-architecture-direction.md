# 04 — Frontend Architecture Direction — ACS

## 1. Stack

- Angular 21, Nx 22.x, TypeScript strict.
- RxJS 7.8 (HTTP) bridged into Angular **Signals** (state).
- Jest (unit), Playwright (`apps/acs-e2e`).
- Tailwind utilities + design tokens (`@neuroassist/shared/ui-tokens`); no SCSS token
  layer.
- Reactive/`FormsModule` two-way binding for the caregiver editor and tenant-setup forms,
  with synchronous validators.

## 2. App boundary

Entry is a **library** (ADR-006): `libs/acs/shell-entry` with importPath
`@neuroassist/acs`, tags `scope:acs` + `type:app`. It exports the barrel: `acs.module`,
`acs.contract`, `acs.routes`, `acs.permission-map`, `acs.feature-flags`, `acs.bootstrap`,
`acs.nav.config`, and `ACS_MODULE_DESCRIPTOR`. `apps/acs/` is a thin host
(`loadChildren: () => import('@neuroassist/acs').then(m => m.AcsModule)`) used for
`nx serve/build acs` and as the e2e web-server base.

## 3. Library organization (Nx tags)

| Lib | Tags | Responsibility |
|---|---|---|
| `libs/acs/shell-entry` | `scope:acs` `type:app` | entry + descriptor |
| `libs/acs/data-access` | `scope:acs` `type:data-access` | `AcsTenantClient` + `AcsCuidadorClient` + `AcsLinkClient` (HTTP) + `AcsTenantFacade` / `AcsCuidadorFacade` / `AcsLinkFacade` / `CoverageFacade` (signals) |
| `libs/acs/feature-tenant-setup` | `scope:acs` `type:feature` | create-empresa / active-tenant step (`TenantSetupComponent`) |
| `libs/acs/feature-caregiver-roster` | `scope:acs` `type:feature` | roster list + coverage badges (`CaregiverRosterComponent`) |
| `libs/acs/feature-caregiver-editor` | `scope:acs` `type:feature` | invite/register + role form (`CaregiverEditorComponent`) |
| `libs/acs/feature-patient-linkage` | `scope:acs` `type:feature` | bind/unbind + coverage guard (`PatientLinkageComponent`) |
| `libs/acs/access` | `scope:acs` `type:domain` | coordinator-only guard |
| `libs/acs/integration` | `scope:acs` `type:domain` | `AcsShellIntegrationService` |
| `libs/acs/navigation` | `scope:acs` `type:domain` | breadcrumb/menu state |
| `libs/acs/ui` (opt) | `scope:acs` `type:ui` | `RoleBadge`, `CoverageBadge`, `CaregiverRow` |

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access-http-base` — HTTP + `X-Empresa-Id` interceptor.
- `@neuroassist/shared/empresa-context` — active tenant. **ACS is the writer**; other
  modules are readers. (Contract to freeze in specs — see doc 06 A1.)
- `@neuroassist/shared/auth-session` — the authenticated principal ACS assigns a role to.
- `@neuroassist/shared/access-decision` — route entitlement (papel → permissions).
- `@neuroassist/shared/contracts` — `Tenant`, `Cuidador`, `Vinculo`, `Paciente` (read) DTOs.
- `@neuroassist/shared/error-model` — map `ProblemDetail` → `AppError`.
- `@neuroassist/shared/ui-*` — tokens, components, feedback toasts.

`data-access` may depend only on `shared/contracts`, `access-http-base`,
`empresa-context`, `auth-session`, `error-model` (nx-boundaries.md). It **must not**
depend on `scope:pac`, `scope:edge`, `scope:gest`.

## 5. Routing strategy & composition

- Shell → `@neuroassist/acs` via `loadChildren`; sub-views (`roster`, `caregiver-editor`,
  `linkage`, `tenant-setup`) via `loadComponent`; `canActivate: [authGuard,
  acsCoordinatorGuard]` where coordinator-only.
- **Cross-module composition (critical) — the onboarding wizard.** ACS owns the wizard's
  *sequence*, but the wizard shell page lives at **`scope:web`** because it composes step
  features from PAC (`PatientRegisterComponent`, `CommandEditorComponent`) and EDGE
  (device-pairing) alongside ACS's own `TenantSetupComponent` and
  `CaregiverLinkStepComponent`. Because `scope:acs` cannot import `scope:pac`/`scope:edge`
  (hard boundary), the **shell** (not ACS) does those imports and wires the shared session
  context (active tenant, current patient id) between steps. ACS exposes its step
  components through its barrel and provides the wizard's step-sequence descriptor. This
  is the production decomposition of the prototype's `onboarding` flow. See ADR-006, PAC
  doc 04 §5 (PAC's patient step is composed here), doc 02 §3.
- The caregiver roster, editor and linkage panels are plain ACS routes — no cross-module
  composition needed.

## 6. State strategy

- Signals-first: `AcsCuidadorFacade` holds `private readonly _roster =
  signal<CuidadorRowVM[]>([])` exposed as `roster = this._roster.asReadonly()`; editor
  state (`EditorState`, draft) likewise a private signal + readonly view.
- **Coverage is a derived signal:** `CoverageFacade` computes `PatientCoverageVM` per
  patient by joining the linkage set with each caregiver's `ativo` flag — the source of
  the `coberto | sem-cobertura` honesty and the last-covering guard (ADR-005 local). It
  is never stored as a mutable flag.
- HTTP Observables from the clients are bridged into signals inside the facades;
  components read signals only.
- No NgRx, no manual `BehaviorSubject` in components.

## 7. Module contract strategy (`acs.contract.ts`)

Fields to anticipate: `MODULE_KEY = 'acs'`; menu metadata (ACS **is** a top-level nav
item — the "Cuidadores / Acesso" destination, `showInNav: true`, coordinator-gated);
permissions (`acs.cuidador.read`, `acs.cuidador.write`, `acs.vinculo.write`,
`acs.tenant.write` — the last two coordinator/conductor only); feature flags
(`coverageGuard` — governs the last-covering block, default **on**; `inviteByEmail` —
toggles email-invite vs direct register, default off in MVP). The `papel → permissions`
mapping lives in `acs.permission-map.ts`.
