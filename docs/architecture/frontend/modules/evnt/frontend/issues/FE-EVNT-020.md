## [FE-EVNT-020] evntGuard — vínculo cuidadora-tenant + tenant-mismatch (GUARD-02/03)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/access/src/lib/evnt.guard.ts` implementa `evntGuard: CanActivateFn` que verifica se a cuidadora autenticada está vinculada ao tenant do paciente; redireciona sem revelar existência de eventos de outros tenants.

**Context / Problem statement:**
- GUARD-02 gap (M3.5): `evntGuard` falha porque a cuidadora não está vinculada ao tenant → redirect `/eventos` (estado bloqueado) ou Home.
- GUARD-03 gap (M3.5): `/eventos/:eventoId` referencia evento de tenant diferente → redirect para `/eventos` **sem revelar** se o id existe em outro tenant (information disclosure prevention).
- O guard depende de `AccessDecisionService` (`@neuroassist/shared/access`) que verifica a sessão + permissão `evnt.view`.
- Para `eventoId`, o guard pode verificar a permissão; o BE retornará `403 tenant-mismatch` se o guard client-side deixar passar.

**Approach (execution path):**
1) Criar `libs/evnt/access/src/lib/evnt.guard.ts` como `CanActivateFn`.
2) Injetar `AccessDecisionService` para checar `evnt.view` permission.
3) Se não tem `evnt.view` → retornar `router.createUrlTree(['/'])` (Home/blocked) — GUARD-02.
4) Para `/eventos/:eventoId`: não verificar o eventoId no client-side (evita leak de timing); deixar o BE barrar com 403 e o error handler (FE-EVNT-023) redirecionar — GUARD-03.
5) Exportar `evntGuard` via `libs/evnt/access/src/index.ts`.

**Non-goals:**
- `authGuard` (shared — FE-EVNT-019).
- Tratamento de 403 no componente (FE-EVNT-023).

**Layer(s):**
frontend / libs/evnt/access

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` (§2, §4)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2 — `evnt.view` rationale)

**Architecture alignment:**
- `CanActivateFn` (functional guard Angular 15+)
- Tag Nx: `scope:evnt`, `type:domain`

**Implementation checklist (by file):**

**Lib access**
- [ ] `libs/evnt/access/src/lib/evnt.guard.ts` — `evntGuard: CanActivateFn`
- [ ] Verificação: `AccessDecisionService.can('evnt.view')` → `false` → redirect `/` (GUARD-02)
- [ ] Para `eventoId`: sem verificação client-side de existência (GUARD-03 — BE é autoritativo)
- [ ] Redirect sem `eventoId` → `/eventos` (estado padrão)
- [ ] `libs/evnt/access/project.json` — `tags: ["scope:evnt","type:domain"]`
- [ ] `libs/evnt/access/src/index.ts` re-exporta `evntGuard`

**Tests**
- [ ] `nx test evnt-access` PASS
- [ ] Unit: `AccessDecisionService.can('evnt.view')` retorna `false` → guard retorna `UrlTree('/')`
- [ ] Unit: `AccessDecisionService.can('evnt.view')` retorna `true` → guard retorna `true`
- [ ] `nx lint evnt-access` PASS

**Milestone:**
Wave P5 / Access guards

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P5

**Acceptance criteria:**
- `npx nx test evnt-access` PASS
- `npx nx lint evnt-access` PASS
- Cuidadora sem `evnt.view` → bloqueada antes de montar a route (GUARD-02)
- `eventoId` de outro tenant → guard deixa passar; BE retorna 403; error handler redireciona (GUARD-03)

**Dependencies:**
- FE-EVNT-002 (evnt.routes.ts — onde este guard é referenciado)
- INT-FE-EVNT-004 (AccessDecisionService integration)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum diretamente (guard client-side; o BE valida no 403)

**Testing / Evidence required:**
- Unit: mock `AccessDecisionService` com `can('evnt.view') = false` → URL de redirect é `/`

**Risks / Notes:**
- GUARD-03 (M3.5): não tentar carregar o evento no guard para checar tenant — isso seria uma chamada extra + information disclosure via timing. A separação guard-client / handler-403 é intencional.
