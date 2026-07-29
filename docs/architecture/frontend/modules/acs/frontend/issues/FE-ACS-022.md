## [FE-ACS-022] acsCoordinatorGuard (libs/acs/access — coordinator-only redirect)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`acsCoordinatorGuard` is a functional Angular router guard in `libs/acs/access`. It checks that the active
session holds a coordinator-capable `papel` (`coordenador_clinica` or conductor bootstrap). If not, it
redirects the caller away from the roster/admin surface. A `cuidadora` is never shown the coordinator-only
routes.

**Context / Problem statement:**
- SPEC-01 §3 defines the guard: reads `papel` from the shared `auth-session` / `access-decision` service.
- `acsCoordinatorGuard` is listed in `canActivate` on ALL four ACS routes (authGuard + this).
- Redirect target: `/` (shell dashboard or login redirect — whatever the shared access-decision returns).
- E2E: "Cuidadores nav item hidden and non-coordinator redirected" scenario (caregiver-roster.feature).

**Approach (execution path):**
1. Create `libs/acs/access/src/lib/acs-coordinator.guard.ts` as a functional guard.
2. Inject `@neuroassist/shared/access-decision` (reads `papel` from token claims).
3. If `papel` is coordinator-capable → return `true`.
4. Else → return `router.createUrlTree(['/'])` (redirect).
5. Re-export from `libs/acs/access/src/index.ts`.
6. Unit tests: coordinator → true; cuidadora → redirect; unauthenticated → delegate to authGuard.

**Non-goals:**
- No auth logic (shared `authGuard` runs first).
- No permission-map checks (that's `acsCaregiverWriteGuard` / `acsLinkWriteGuard`).

**Layer(s):**
frontend / libs/acs/access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` (§3 guard contracts)

**Architecture alignment:**
- Functional guard (Angular 16+); inject() inside guard fn; returns `boolean | UrlTree`
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/access/`
- 🔲 `src/lib/acs-coordinator.guard.ts` — functional guard
- 🔲 `src/index.ts` — re-exports acsCoordinatorGuard

**Tests**
- 🔲 `src/lib/acs-coordinator.guard.spec.ts` — coordinator=true; cuidadora=redirect
- 🔲 `nx test acs-access` PASS
- 🔲 `nx lint acs-access` PASS

**Milestone:**
Wave 5 / P5 Access guards

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P5

**Acceptance criteria:**
- `npx nx test acs-access` PASS
- `npx nx lint acs-access` PASS
- `coordenador_clinica` → guard returns `true`
- `cuidadora` → guard returns `UrlTree(['/'])`
- E2E: non-coordinator redirected off the roster

**Dependencies:**
- FE-ACS-001 (MODULE_KEY para contexto)
- FE-ACS-002 (guard referenciado nas rotas)
- FE-ACS-003 (lib skeleton)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto (lê papel do JWT via access-decision)

**Testing / Evidence required:**
- Testes de guard unit (true/redirect)
- E2E: não-coordenador redirecionado de `/acs`

**Risks / Notes:**
- A definição de "coordinator-capable" deve estar alinhada com o BE (SPEC-01 §3 cita `coordenador_clinica`
  e o "bootstrap conductor" — verificar se o bootstrap conductor tem um papel separado no JWT).
