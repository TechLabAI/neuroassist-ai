## [PAC-FE-006] libs/pac/navigation — breadcrumb signal + pac.nav.config

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/navigation` expõe `PacBreadcrumbService` com um signal reativo que emite a trilha de breadcrumbs correta (`[Calibração]` ou `[Calibração, Novo comando]` / `[Calibração, Editar "<rótulo>"]`) e contribui `null` quando `PatientRegisterComponent` está composto dentro do wizard ACS (onboarding owns that trail).

**Context / Problem statement:**
- SPEC-01 §1 define três estados de breadcrumb por rota: `/pac` → `[Calibração]`; `/pac/comandos/novo` → `[Calibração, Novo comando]`; `/pac/comandos/:id/editar` → `[Calibração, Editar "<rótulo>"]`.
- SPEC-06 §2: quando `PatientRegisterComponent` roda dentro do wizard ACS, o PAC não contribui breadcrumb (onboarding owns it).
- O `PacBreadcrumbService` é lido pelo `pac.bootstrap.ts` (PAC-FE-005) e contribuído ao `ctx.breadcrumbRegistry` do shell.
- O `pac.nav.config.ts` exporta a configuração estática de navegação (label, icon, route) para registro no shell menu.

**Approach (execution path):**
1. Criar `libs/pac/navigation/src/lib/pac-breadcrumb.service.ts`.
2. Implementar signal `breadcrumb = signal<BreadcrumbItem[] | null>(null)`.
3. Expor métodos: `setCatalog()` → `[{ label: 'Calibração', route: '/pac' }]`; `setNew()` → catalog + `{ label: 'Novo comando' }`; `setEdit(rotulo)` → catalog + `{ label: `Editar "${rotulo}"` }`; `clear()` → `null` (onboarding context).
4. Criar `libs/pac/navigation/src/lib/pac.nav.config.ts` com `PAC_NAV_CONFIG = { label: 'Calibração', icon: 'tune', route: '/pac', order: 30 }`.
5. Unit test: cada setter → assert signal value; clear → null.

**Non-goals:**
- Não registrar o breadcrumb no shell diretamente — é responsabilidade do `pac.bootstrap.ts`.
- Não construir UI de breadcrumb aqui (é um signal de dados, não de renderização).

**Layer(s):**
frontend / libs/pac/navigation

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-01-route-map.md` (§1 breadcrumb column)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2 contributeBreadcrumbs)

**Architecture alignment:**
- Signals-first (`signal<T>`)
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/navigation**
- [ ] `libs/pac/navigation/src/lib/pac-breadcrumb.service.ts`
- [ ] `libs/pac/navigation/src/lib/pac.nav.config.ts`
- [ ] `libs/pac/navigation/src/lib/pac-breadcrumb.service.spec.ts`
- [ ] `libs/pac/navigation/src/index.ts` (re-exports)

**Tests**
- [ ] `npx nx test pac-navigation` PASS
- [ ] `npx nx lint pac-navigation` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P1

**Acceptance criteria:**
- `npx nx test pac-navigation` PASS: `setEdit('Respirar')` → signal `[..., { label: 'Editar "Respirar"' }]`.
- `npx nx test pac-navigation` PASS: `clear()` → signal `null`.
- Breadcrumb `null` quando o `PatientRegisterComponent` está em modo onboarding (mock de contexto no test).

**Dependencies:**
- `@neuroassist/shared/contracts` (tipo `BreadcrumbItem`)

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum.

**Testing / Evidence required:**
- Unit: 4 métodos testados, signal values assertados.

**Risks / Notes:**
- O rótulo no breadcrumb de edição (`Editar "<rótulo>"`) vem do `ComandoRowVM.rotulo` (read do facade); o `PacBreadcrumbService` deve receber o rótulo como parâmetro (não como dependency do facade) para evitar acoplamento de navigation → data-access.
