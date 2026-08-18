## [FE-DASH-016] authGuard — proteção das rotas / e /notificacoes

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/access` (ou reaproveitando `@neuroassist/shared/access-decision`) disponibiliza `authGuard` funcional que protege `/` e `/notificacoes`, redirecionando para `/entrada` quando a sessão ACS não é válida, preservando a URL de retorno.

**Context / Problem statement:**
- Todo cuidador autenticado tem `dash.view` — o guard é de sessão, não de role.
- Redireciona para `/entrada` com `queryParams: { returnUrl: ... }` para retomar após login.
- Fonte: SPEC-01 §3; SPEC-07 §2 (`#unauthenticated` → redirect).
- Bloqueia: FE-DASH-027 (e2e G1/G2)

**Approach (execution path):**
1. Verificar se `@neuroassist/shared/access-decision` já exporta `authGuard`; se sim, apenas configurar no `dash.routes.ts` (FE-DASH-002) — sem duplicar código.
2. Se não existir, criar `libs/dash/access/src/lib/auth.guard.ts` como `CanActivateFn` que:
   - Injeta `AuthSessionService` de `@neuroassist/shared/auth-session`.
   - Retorna `true` se `isAuthenticated()` é true.
   - Retorna `router.createUrlTree(['/entrada'], { queryParams: { returnUrl: state.url } })` caso contrário.
3. Exportar pelo barrel de `dash/access`.

**Non-goals:**
- Não verificar RBAC (role-based) — isso é o roleGuard (FE-DASH-017).
- Não implementar o fluxo de login (ACS).

**Layer(s):**
frontend / libs/dash/access (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2 — #unauthenticated → redirect)

**Architecture alignment:**
- Functional guard (Angular 14+ CanActivateFn)
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/access**
- 🔲 `libs/dash/access/src/lib/auth.guard.ts`
- 🔲 `libs/dash/access/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-access` PASS (testar: autenticado → true; não autenticado → UrlTree para /entrada; returnUrl preservado)
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
- Não autenticado em `/` → redirect para `/entrada?returnUrl=/`
- Não autenticado em `/notificacoes` → redirect para `/entrada?returnUrl=/notificacoes`
- Autenticado → guard retorna `true`

**Dependencies:**
- FE-DASH-002 (dash.routes.ts — configura o guard nas rotas)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (apenas verifica sessão local)

**Testing / Evidence required:**
- Test unitário do guard com AuthSessionService mockado

**Risks / Notes:**
- Se `@neuroassist/shared/access-decision` já tem o guard, reutilizar e não duplicar. Verificar no scaffold da shared lib.
