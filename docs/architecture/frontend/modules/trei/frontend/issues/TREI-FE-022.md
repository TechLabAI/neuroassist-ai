## [TREI-FE-022] caregiverSessionGuard (libs/trei/access)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/access/src/lib/caregiver-session.guard.ts` implementa `caregiverSessionGuard` — verifica a existência de uma sessão de cuidadora activa (JWT ACS realm) e redireciona para o login da cuidadora se ausente.

**Context / Problem statement:**
- `caregiverSessionGuard` é o primeiro guard em todas as rotas TREI (SPEC-01 §3). Sem ele, a superfície de treino ficaria acessível sem autenticação.
- Verifica: existência de JWT de cuidadora válido via `@neuroassist/shared/access` (não implementa JWT verification directamente — delega ao shared access layer).
- On failure: redireciona para o URL de login da cuidadora (shared access sabe o destino) — nunca renderiza nenhuma superfície TREI sem sessão.
- É um `CanActivateFn` (functional guard, Angular 15+).
- Bloqueia: TREI-FE-004 (rotas usam este guard).

**Approach (execution path):**
1. Criar `libs/trei/access/src/lib/caregiver-session.guard.ts`.
2. Implementar como `CanActivateFn`:
   - `inject(CaregiverSessionService)` (de `@neuroassist/shared/access`).
   - Se `sessionService.hasActiveSession()` → `true`.
   - Se não → `router.createUrlTree([caregiverLoginPath])` (path do shared access).
3. Exportar do barrel `libs/trei/access/src/index.ts`.

**Non-goals:**
- Não verificar scopes JWT — isso pertence a `treiGuard` e `treiProporGuard` (TREI-FE-023).
- Não implementar o shared `CaregiverSessionService`.

**Layer(s):**
frontend / libs/trei/access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§3)

**Architecture alignment:**
- `CanActivateFn` (functional, Angular 15+)
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/access**
- [ ] `libs/trei/access/src/lib/caregiver-session.guard.ts`
- [ ] `libs/trei/access/src/index.ts`

**Tests**
- [ ] `nx test trei-access` PASS
- [ ] Sem sessão → redireciona (não retorna `true`)
- [ ] Com sessão activa → retorna `true`
- [ ] `nx lint trei-access` PASS

**Milestone:**
Wave 5 / Access Guards

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P5

**Acceptance criteria:**
- `npx nx test trei-access` PASS
- `npx nx lint trei-access` PASS
- Guard não verifica scopes (apenas sessão existe/não existe)

**Dependencies:**
- INT-FE-TREI-001 (CaregiverSessionService de @neuroassist/shared/access)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP (lê JWT local)

**Testing / Evidence required:**
- Teste: guard com sessão nula → `UrlTree` para login
- Teste: guard com sessão activa → `true`

**Risks / Notes:**
- Confirmar o nome da função/serviço do shared access que verifica a existência de sessão (`hasActiveSession()` ou similar).
