## [PAC-FE-017] pacTenantPatientGuard — active tenant + registered paciente redirect

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/access/src/lib/pac-tenant-patient.guard.ts` implementa `pacTenantPatientGuard` que verifica: (1) empresa ativa presente no `empresa-context`; (2) `PacPacienteFacade.patient()` não-null; redirecionando para onboarding se qualquer condição falhar.

**Context / Problem statement:**
- SPEC-01 §3: `pacTenantPatientGuard` — "active tenant (empresa-context) AND a registered Paciente for the tenant; otherwise redirect to onboarding".
- O guard é colocado em todas as rotas PAC (catalog + editor).
- Como `pac.bootstrap.ts` (PAC-FE-005) chama `PacPacienteFacade.loadCurrent()` antes da primeira navegação, o signal `patient()` deve estar disponível sem aguardar — o guard usa o valor atual do signal (não async).
- Se `patient()` for `null` (nenhum paciente cadastrado), redireciona para onboarding step 2 (ACS owns that route).
- Bloqueia: PAC-FE-002 (rotas usam este guard)

**Approach (execution path):**
1. Criar `libs/pac/access/src/lib/pac-tenant-patient.guard.ts` como `CanActivateFn`.
2. Injetar `EmpresaContextService` (de `@neuroassist/shared/empresa-context`) e `PacPacienteFacade`.
3. Check 1: `!empresaContext.activeEmpresaId()` → redirect to `/onboarding` (ou rota configurada de missing-tenant).
4. Check 2: `!pacFacade.patient()` → redirect to `/onboarding/paciente` (step 2 ACS wizard).
5. Retornar `true` se ambas as condições passam.
6. Unit test: mock `patient()` como null → assert redirect; mock com valor → assert true.

**Non-goals:**
- Sem verificação de permissão (isso é do `authGuard` + `pacCommandWriteGuard`).
- Sem fetch async — usa apenas o signal atual (primed pelo bootstrap).

**Layer(s):**
frontend / libs/pac/access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-01-route-map.md` (§3 Guard contracts)

**Architecture alignment:**
- Functional guard (`CanActivateFn`) — sem classe
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/access**
- [ ] `libs/pac/access/src/lib/pac-tenant-patient.guard.ts`
- [ ] `libs/pac/access/src/lib/pac-tenant-patient.guard.spec.ts`
- [ ] `libs/pac/access/src/index.ts` (re-exports)

**Tests**
- [ ] `npx nx test pac-access` PASS
- [ ] `npx nx lint pac-access` PASS

**Milestone:**
Wave 5 / P5 Access Guards

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P5

**Acceptance criteria:**
- `npx nx test pac-access` PASS: `patient() === null` → `UrlTree` redirect to onboarding.
- `npx nx test pac-access` PASS: `patient()` presente → retorna `true`.
- `npx nx test pac-access` PASS: empresa context vazio → redirect.

**Dependencies:**
- PAC-FE-010 (PacPacienteFacade — signal patient)
- `@neuroassist/shared/empresa-context`

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum.

**Testing / Evidence required:**
- Unit: 3 cenários — sem empresa, sem paciente, tudo OK.

**Risks / Notes:**
- Se o bootstrap `loadCurrent()` ainda não terminou quando o guard executa (edge case: navegação muito rápida antes do init), o signal pode ser `null` mesmo havendo paciente. Considerar `PacPacienteFacade.loadingState()` para esperar resolve async se necessário.
