# 04 — Frontend Architecture Direction — PAC

## 1. Stack

- Angular 21, Nx 22.x, TypeScript strict.
- RxJS 7.8 (HTTP) bridged into Angular **Signals** (state).
- Jest (unit), Playwright (`apps/pac-e2e`).
- Tailwind utilities + design tokens (`@neuroassist/shared/ui-tokens`); no SCSS
  token layer.
- Reactive/`FormsModule` two-way binding for the command editor form (as in the
  prototype), with synchronous validators.

## 2. App boundary

Entry is a **library** (ADR-006): `libs/pac/shell-entry` with importPath
`@neuroassist/pac`, tags `scope:pac` + `type:app`. It exports the barrel:
`pac.module`, `pac.contract`, `pac.routes`, `pac.permission-map`,
`pac.feature-flags`, `pac.bootstrap`, `pac.nav.config`, and `PAC_MODULE_DESCRIPTOR`.
`apps/pac/` is a thin host
(`loadChildren: () => import('@neuroassist/pac').then(m => m.PacModule)`) used for
`nx serve/build pac` and as the e2e web-server base.

## 3. Library organization (Nx tags)

| Lib | Tags | Responsibility |
|---|---|---|
| `libs/pac/shell-entry` | `scope:pac` `type:app` | entry + descriptor |
| `libs/pac/data-access` | `scope:pac` `type:data-access` | `PacComandoClient` + `PacPacienteClient` (HTTP) + `PacComandoFacade` / `PacPacienteFacade` / `TiposGestoReadFacade` (signals) |
| `libs/pac/feature-command-catalog` | `scope:pac` `type:feature` | list + empty state + KPIs (`CommandCatalogComponent`) |
| `libs/pac/feature-command-editor` | `scope:pac` `type:feature` | editor form + preview + GEST live-test slot (`CommandEditorComponent`) |
| `libs/pac/feature-patient-register` | `scope:pac` `type:feature` | onboarding patient step (`PatientRegisterComponent`) |
| `libs/pac/access` | `scope:pac` `type:domain` | active-tenant + patient guard |
| `libs/pac/integration` | `scope:pac` `type:domain` | `PacShellIntegrationService` |
| `libs/pac/navigation` | `scope:pac` `type:domain` | breadcrumb/menu state |
| `libs/pac/ui` (opt) | `scope:pac` `type:ui` | `CommandRow`, `UrgencyBadge`, `SensitivitySelector` |

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access-http-base` — HTTP + `X-Empresa-Id` interceptor.
- `@neuroassist/shared/empresa-context` — active tenant.
- `@neuroassist/shared/access-decision` + `auth-session` — route entitlement.
- `@neuroassist/shared/contracts` — `Paciente`, `Comando`, `TipoGesto` DTOs.
- `@neuroassist/shared/error-model` — map `ProblemDetail` → `AppError`.
- `@neuroassist/shared/ui-*` — tokens, components, feedback toasts.

`data-access` may depend only on `shared/contracts`, `access-http-base`,
`empresa-context`, `error-model` (nx-boundaries.md). It **must not** depend on
`scope:gest`.

## 5. Routing strategy & composition

- Shell → `@neuroassist/pac` via `loadChildren`; sub-views (`catalog`, `editor`,
  `patient`) via `loadComponent`; `canActivate: [authGuard, pacTenantPatientGuard]`.
- **Cross-module composition (critical):** the command editor needs GEST's live
  test. Because `scope:pac` cannot import `scope:gest` (hard boundary), composition
  happens at the **`scope:web` shell-page level**: the shell command-editor page
  renders `CommandEditorComponent` (PAC) *and* `GestureLiveTestComponent` (GEST),
  wiring the current `Comando` draft into GEST via `@Input`/signal. GEST exposes the
  component through its barrel; the **shell** (not PAC) does the import. This is the
  production decomposition of the prototype's single `calibracao` screen. See
  ADR-006, GEST doc 04 §5.
- The onboarding patient step (`PatientRegisterComponent`) is likewise composed by
  the ACS-owned onboarding shell page, which orchestrates the multi-module wizard
  (tenant=ACS, patient=PAC, device=EDGE, first command=PAC editor, caregiver=ACS).

## 6. State strategy

- Signals-first: `PacComandoFacade` holds `private readonly _catalog =
  signal<ComandoRowVM[]>([])` exposed as `catalog = this._catalog.asReadonly()`;
  editor state (`EditorState`, draft) likewise a private signal + readonly view.
- HTTP Observables from the clients are bridged into signals inside the facades;
  components read signals only.
- No NgRx, no manual `BehaviorSubject` in components.
- **Sync state** is a derived signal: it joins the saved catalog with device-ack
  status (EDGE-sourced, read via shared status) to compute `synced | pending` per
  command (ADR-005 local) — the catalog never invents an `active` state.

## 7. Module contract strategy (`pac.contract.ts`)

Fields to anticipate: `MODULE_KEY = 'pac'`; menu metadata (PAC **is** a top-level
nav item — the "Calibração / Catálogo" destination, `showInNav: true`);
permissions (`pac.comando.read`, `pac.comando.write`, `pac.paciente.write`);
feature flags (`inlineNewTipoGesto` — toggles the inline add-type affordance,
default on; `deferredEdgeSync` — governs the pending-sync surfacing, default on).
