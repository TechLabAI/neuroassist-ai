## [FE-ACS-005] Bootstrap lifecycle acs.bootstrap.ts (init/destroy)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/shell-entry/src/lib/acs.bootstrap.ts` exports `acsBootstrap: ModuleBootstrap` with `init` and
`destroy` lifecycle hooks. `init` wires permissions, primes roster/coverage, and subscribes breadcrumbs.
`destroy` resets module-local signals and aborts in-flight requests without clearing the shared empresa context.

**Context / Problem statement:**
- SPEC-06 §3 defines the exact init/destroy contract.
- `init` must: (1) register permission map + feature flags with the shell; (2) subscribe breadcrumb signal;
  (3) hydrate active tenant from empresa-context; (4) prime `AcsCuidadorFacade.loadRoster()` + CoverageFacade.
- `destroy` must: reset ACS-local signals (roster, links, coverage), abort in-flight GETs, **NOT** clear
  `@neuroassist/shared/empresa-context` (other modules depend on it for the rest of the session).
- Bloqueia: FE-ACS-006 (navigation subscription), FE-ACS-010 (facade must exist for priming)

**Approach (execution path):**
1. Create `libs/acs/shell-entry/src/lib/acs.bootstrap.ts`.
2. Implement `init(ctx: ShellContext)`: inject facades + nav signal via DI inside `runInInjectionContext` or
   accept them as constructor params.
3. In `init`: call `ctx.permissionService.register(MODULE_PERMISSION_MAP)`,
   `ctx.featureFlagService.register(MODULE_FEATURE_FLAGS)`, subscribe `acsNavService.breadcrumb$`,
   call `acsCuidadorFacade.loadRoster()`, hydrate tenant.
4. Implement `destroy()`: call `acsCuidadorFacade.reset()`, `acsLinkFacade.reset()`,
   `coverageFacade.reset()`, abort in-flight requests via `AbortController` or `takeUntilDestroyed`.
5. Ensure `destroy()` does NOT call empresa-context reset.
6. Re-export `acsBootstrap` from barrel.
7. Unit test: spy on `loadRoster()` called in init; spy on facade reset() called in destroy; empresa-context not cleared.

**Non-goals:**
- No auth logic (token exchange is `auth-session`).
- No PAC/EDGE/GEST imports.

**Layer(s):**
frontend / libs/acs/shell-entry

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- `ModuleBootstrap` type from `@neuroassist/shared/contracts`
- No `BehaviorSubject`; breadcrumb exposed as a signal
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/shell-entry/`
- 🔲 `src/lib/acs.bootstrap.ts` — `acsBootstrap: ModuleBootstrap` with init/destroy
- 🔲 `src/index.ts` — re-exports acsBootstrap

**Tests**
- 🔲 `src/lib/acs.bootstrap.spec.ts` — init calls loadRoster; destroy calls facade resets; empresa-context untouched
- 🔲 `nx test acs-shell-entry` PASS
- 🔲 `nx lint acs-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P1

**Acceptance criteria:**
- `npx nx test acs-shell-entry` PASS
- `npx nx lint acs-shell-entry` PASS
- `npx nx build acs-shell-entry` PASS
- `init()` spy: `loadRoster` invoked; permission map registered
- `destroy()` spy: facade signals reset; empresa-context NOT cleared
- E2E scenario: "ACS bootstrap init primes the roster" (caregiver-roster.feature) passes with this implementation

**Dependencies:**
- FE-ACS-001 (MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS)
- FE-ACS-006 (nav breadcrumb signal subscription)
- FE-ACS-010 (AcsCuidadorFacade must exist for priming)
- FE-ACS-012 (AcsLinkFacade reset)
- FE-ACS-013 (CoverageFacade reset)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum; priming calls loadRoster() which internamente chama GET /caregivers

**Testing / Evidence required:**
- Unit tests spying init/destroy behavior passando
- E2E scenario "bootstrap init primes roster" confirmado

**Risks / Notes:**
- Se o `ShellContext` não expor um `permissionService` diretamente, a integração precisa de adaptação.
  Verificar a interface pública de `@neuroassist/shared/contracts` antes de implementar.
- O timing de `init` (chamado antes ou depois do router resolve) impacta se o roster já está carregado
  no primeiro acesso a `/acs`.
