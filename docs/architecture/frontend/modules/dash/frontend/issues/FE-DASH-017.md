## [FE-DASH-017] roleGuard — ocultar item de navegação Onboarding para cuidadora

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/access/src/lib/role.guard.ts` (ou diretiva equivalente) garante que o item "Onboarding" da sidebar fica oculto/desabilitado para o papel `cuidadora`, visível apenas para `coordenador_clinica`/condutor técnico. Cobre coverage gap G1 (role-hide nav).

**Context / Problem statement:**
- O guard de role é **nav-level**, não de rota — o Onboarding é uma rota ACS que DASH apenas linka.
- DASH não inventa lógica RBAC; usa `DASH_PERMISSION_MAP['dash.onboarding.entry']` (SPEC-05 §2).
- Fonte: SPEC-01 §3; SPEC-05 §2; coverage gap G1.
- Bloqueia: FE-DASH-027 (e2e G1)

**Approach (execution path):**
1. Criar `RoleGuardService` ou use `PermissionDirective` de `@neuroassist/shared/access-decision`.
2. A sidebar (`SidebarComponent` em `scope:web`) usa `*ngIf="hasPermission('dash.onboarding.entry')"` no item Onboarding.
3. `hasPermission` resolve via `DashPermissionService` que lê `DASH_PERMISSION_MAP` vs role do `AuthSessionService`.
4. `libs/dash/access` exporta `DashPermissionService` ou a diretiva de permissão.
5. Adicionar ao barrel de `dash/access`.

**Non-goals:**
- Não bloquear a rota Onboarding (o BE re-enforça; aqui é só UI affordance).
- Não modificar RBAC central (ACS).

**Layer(s):**
frontend / libs/dash/access (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2 DASH_PERMISSION_MAP)

**Architecture alignment:**
- Functional/service pattern; sem NgRx
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/access**
- 🔲 `libs/dash/access/src/lib/dash-permission.service.ts`
- 🔲 `libs/dash/access/src/index.ts` (barrel)

**Shell (scope:web)**
- 🔲 `apps/neuroassist-ai-web/src/app/shell/sidebar/sidebar.component.html` (adicionar condição de visibilidade para Onboarding)

**Tests**
- 🔲 `npx nx test dash-access` PASS (testar: cuidadora → hasPermission('dash.onboarding.entry') = false; coordenador_clinica → true)
- 🔲 `npx nx lint dash-access` PASS

**Milestone:**
Wave 5 / Guards

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P5

**Acceptance criteria:**
- `npx nx test dash-access` PASS
- `npx nx lint dash-access` PASS
- `npx nx build dash-access` PASS
- Role `cuidadora` → item "Onboarding" oculto na sidebar (coverage gap G1)
- Role `coordenador_clinica` → item "Onboarding" visível

**Dependencies:**
- FE-DASH-001 (DASH_PERMISSION_MAP)
- FE-DASH-016 (authGuard — sessão necessária para ter role)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: sidebar com role cuidadora (sem Onboarding) e com coordenador_clinica (com Onboarding)
- e2e coberto em FE-DASH-027 (G1)

**Risks / Notes:**
- DASH não inventa RBAC — lê da sessão ACS. Verificar que AuthSessionService expõe o papel do usuário autenticado.
