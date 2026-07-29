## [FE-EVNT-019] authGuard — wiring para rotas EVNT (GUARD-01)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `authGuard` compartilhado (`@neuroassist/shared/access`) está corretamente configurado para as rotas EVNT: falha de autenticação redireciona para o Shell login route e não monta o sub-tree EVNT.

**Context / Problem statement:**
- GUARD-01 gap (M3.5): `authGuard` falha → redirect para login (SPEC-01 §4).
- O `authGuard` é compartilhado (não criado aqui), mas precisa ser validado que:
  1. Está incluído em `canActivate` da rota EVNT (FE-EVNT-002).
  2. O redirect target para login está correto para este projeto.
  3. O sub-tree EVNT não monta quando `authGuard` retorna `false`/`UrlTree`.
- A verificação é simples se o guard já existe no shared; a issue é sobre o gap de cobertura de teste.

**Approach (execution path):**
1) Verificar que `@neuroassist/shared/access` exporta `authGuard` como `CanActivateFn`.
2) Confirmar que o redirect path de `authGuard` é o Shell login route (ex: `/login`).
3) Em `evnt.routes.ts` (FE-EVNT-002): `canActivate: [authGuard, evntGuard]` — `authGuard` primeiro.
4) Escrever teste de rota que simula `authGuard` retornando `UrlTree('/login')` → verificar redirect.
5) Se o `authGuard` não existir no shared, criar stub mínimo (mas escalar para o time).

**Non-goals:**
- Implementação do `authGuard` em si (é shared — fora do scope EVNT).
- `evntGuard` (FE-EVNT-020).

**Layer(s):**
frontend / libs/evnt/access (validação) + libs/evnt/shell-entry (routing)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` (§2, §4 — Guard column)

**Architecture alignment:**
- `authGuard`: `CanActivateFn` de `@neuroassist/shared/access`
- Tag Nx: `scope:evnt`, `type:domain`

**Implementation checklist (by file):**

**Lib shell-entry / access**
- [ ] `evnt.routes.ts`: `canActivate: [authGuard, evntGuard]` com `authGuard` primeiro
- [ ] Confirmar redirect target de `authGuard` → `/login` (ou Shell login route)
- [ ] Teste: navegar para `/eventos` sem sessão → redirect para `/login`
- [ ] Teste: navegar para `/eventos/:eventoId` sem sessão → redirect para `/login`

**Tests**
- [ ] `nx test evnt-shell-entry` PASS (coverage GUARD-01)
- [ ] `nx lint evnt-shell-entry` PASS

**Milestone:**
Wave P5 / Access guards

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P5

**Acceptance criteria:**
- `npx nx test evnt-shell-entry` PASS
- Navegação para `/eventos` sem token → redirect `/login` (GUARD-01)
- Sub-tree EVNT não montado sem autenticação

**Dependencies:**
- FE-EVNT-002 (evnt.routes.ts)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (guard client-side)

**Testing / Evidence required:**
- Unit: `RouterTestingHarness` → mock `authGuard` retorna `false` → URL final é `/login`

**Risks / Notes:**
- GUARD-01 gap (M3.5): se o `authGuard` já existe e está configurado globalmente, esta issue pode ser apenas um teste de regressão — verificar antes de reimplementar.
