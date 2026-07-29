## [INT-FE-ACS-002] AcsShellIntegrationService — register + breadcrumbs + onboarding seam + tenant-writer declaration

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`AcsShellIntegrationService` (in `libs/acs/integration`) bridges ACS into the shell without ACS reaching into
`scope:web`. Exposes `register()` (contributes descriptor + nav config), `contributeBreadcrumbs()` (exposes
nav signal to shell), and `onboardingWizardComposition()` (declares the wizard step sequence). Declares ACS as
the writer of `@neuroassist/shared/empresa-context`.

**Context / Problem statement:**
- SPEC-06 §2 defines the full `AcsShellIntegrationService` contract.
- `register()` contributes `ACS_MODULE_DESCRIPTOR` (FE-ACS-004) + nav config to the shell registry.
- `contributeBreadcrumbs()` exposes the `AcsNavService.breadcrumbs` signal to the shell.
- `onboardingWizardComposition()` declares the ACS step sequence (step 1: TenantSetup, step 5: CaregiverLinkStep).
- ACS declares itself as the active-tenant writer so peers know the source of `X-Empresa-Id`.
- Bloqueia: INT-FE-ACS-003 (empresa-context writer seam), INT-FE-ACS-004 (onboarding composition)

**Approach (execution path):**
1. Create `libs/acs/integration/src/lib/acs-shell-integration.service.ts` (`@Injectable({ providedIn: 'root' })`).
2. `register(registry: ShellRegistry)`: call `registry.registerModule(ACS_MODULE_DESCRIPTOR)`;
   call `registry.registerNavConfig(AcsMenuConfig)`; declare empresa-context writer.
3. `contributeBreadcrumbs(shellBreadcrumbService)`: pipe `acsNavService.breadcrumbs` to the shell service.
4. `onboardingWizardComposition()`: return wizard step descriptors (step 1 = TenantSetupComponent,
   step 5 = CaregiverLinkStepComponent) without importing PAC/EDGE scope.
5. `destroy()`: unsubscribe breadcrumb subscription.
6. Unit tests: register contributes descriptor; breadcrumbs signal piped; onboarding steps declared.

**Non-goals:**
- No PAC/EDGE/GEST imports (MUST NOT import other scopes — FE 02 §4).
- No auth logic.

**Layer(s):**
frontend / libs/acs/integration

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- `@Injectable({ providedIn: 'root' })`; no cross-scope imports; signals for breadcrumb
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/integration/`
- 🔲 `src/lib/acs-shell-integration.service.ts` — register + contributeBreadcrumbs + onboardingWizardComposition
- 🔲 `src/index.ts` — re-exports AcsShellIntegrationService

**Tests**
- 🔲 `src/lib/acs-shell-integration.service.spec.ts` — register; breadcrumb piping; onboarding steps
- 🔲 `nx test acs-integration` PASS
- 🔲 `nx lint acs-integration` PASS (0 scope violations)

**Milestone:**
Wave 4 / P4 Integrations

**Labels:**
type:integration
layer:frontend
scope:acs
priority:P4

**Acceptance criteria:**
- `npx nx test acs-integration` PASS
- `npx nx lint acs-integration` PASS (0 PAC/EDGE/GEST imports)
- `register()`: ACS_MODULE_DESCRIPTOR contribuído ao shell registry
- `contributeBreadcrumbs()`: shell recebe sinal de breadcrumb ACS
- Onboarding: step 1 = TenantSetupComponent, step 5 = CaregiverLinkStepComponent

**Dependencies:**
- FE-ACS-004 (ACS_MODULE_DESCRIPTOR)
- FE-ACS-006 (AcsNavService)
- FE-ACS-020 (TenantSetupComponent)
- FE-ACS-021 (CaregiverLinkStepComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto (apenas wiring de serviços)

**Testing / Evidence required:**
- Testes de unit passando
- Lint: 0 violações de boundary (scope:acs não importa scope:pac/edge/gest)

**Risks / Notes:**
- `ShellRegistry` e `ShellBreadcrumbService` são APIs públicas de `scope:web` —
  verificar os tipos antes de implementar. Se essas APIs não existirem ainda, criar stubs anotados com
  `TODO[scaffold-web]: replace with real ShellRegistry`.
