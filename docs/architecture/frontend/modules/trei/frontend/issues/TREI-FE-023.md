## [TREI-FE-023] treiGuard + treiProporGuard (review surface reachable without propor scope G1)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/access` implementa `treiGuard` (verifica scope `treinamento.sessao.conduzir`) e `treiProporGuard` (verifica scope `treinamento.no.propor`) — **G1:** `treiProporGuard` bloqueia a materialização/edição mas **não** bloqueia o acesso à superfície de revisão em `/treinamento/sessao/propostas`.

**Context / Problem statement:**
- **G1 (gap do specs-validate):** a SPEC-01 §3 diz "IF `treiProporGuard` finds the JWT lacks scope `treinamento.no.propor`, THEN keep the review surface reachable and SHALL NOT open the proposal materialize/edit route" — mas nenhum cenário e2e cobria este path. Esta issue garante que o guard redireciona para `/treinamento/sessao` (não para "forbidden") e que a superfície de revisão permanece acessível.
- `treiGuard`: scope `treinamento.sessao.conduzir` ausente → redirect para caregiver forbidden state (não renderiza superfície TREI).
- `treiProporGuard`: scope `treinamento.no.propor` ausente → redirect para `/treinamento/sessao` (a review surface da sessão, onde a cuidadora pode ver e aprovar propostas existentes sem propor novas — G1).
- Os scopes `treinamento.no.aprovar.cuidadora` e `treinamento.no.descartar` **não são route guards** — gatam acções dentro do card (SPEC-01 §3 notas).

**Approach (execution path):**
1. Criar `libs/trei/access/src/lib/trei.guard.ts`:
   - `CanActivateFn` — `inject(CaregiverSessionService).hasScope(TREI_PERMISSION_MAP.conduzir)`.
   - On failure: `router.createUrlTree(['/caregiver/forbidden'])`.
2. Criar `libs/trei/access/src/lib/trei-propor.guard.ts`:
   - `CanActivateFn` — `inject(CaregiverSessionService).hasScope(TREI_PERMISSION_MAP.propor)`.
   - On failure: `router.createUrlTree(['/treinamento/sessao'])` (**G1**: mantém na sessão, não bloqueia review).
3. Adicionar aos exports do `libs/trei/access/src/index.ts`.

**Non-goals:**
- Não verificar os scopes de aprovação/descarte aqui (gatam acções nos cards, não rotas).
- Não implementar o fallback forbidden page.

**Layer(s):**
frontend / libs/trei/access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2)

**Architecture alignment:**
- `CanActivateFn` funcional
- Importa `TREI_PERMISSION_MAP` de `@neuroassist/trei` (shell-entry barrel)
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/access**
- [ ] `libs/trei/access/src/lib/trei.guard.ts`
- [ ] `libs/trei/access/src/lib/trei-propor.guard.ts`
- [ ] `treiProporGuard` redireciona para `/treinamento/sessao` (não forbidden) — G1

**Tests**
- [ ] `nx test trei-access` PASS
- [ ] `treiGuard`: scope ausente → UrlTree para `/caregiver/forbidden`
- [ ] `treiGuard`: scope presente → `true`
- [ ] `treiProporGuard`: scope ausente → UrlTree para `/treinamento/sessao` (G1, não forbidden)
- [ ] `treiProporGuard`: scope presente → `true`
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
- `treiProporGuard` redireciona para `/treinamento/sessao`, **nunca** para forbidden (G1)
- Cuidadora sem scope `propor` consegue aceder a `/treinamento/sessao` (review surface) mas não a `/treinamento/sessao/propostas` para editar/propor

**Dependencies:**
- TREI-FE-002 (TREI_PERMISSION_MAP)
- TREI-FE-022 (caregiverSessionGuard — pré-condição)
- INT-FE-TREI-001 (CaregiverSessionService.hasScope)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP (lê scopes do JWT local)

**Testing / Evidence required:**
- e2e coberto em TREI-FE-029 (`apenas-lado-cuidadora.feature` + guard G1)
- Teste unitário: mock de JWT sem `treinamento.no.propor` → `treiProporGuard` retorna UrlTree `/treinamento/sessao`

**Risks / Notes:**
- **G1:** confirmar com a SPEC-01 que a review surface `/treinamento/sessao` é acessível com scope `conduzir` apenas (sem `propor`) — os componentes de aprovação/descarte ainda estão disponíveis porque se controlam por `RBAC` nos próprios botões, não por guards de rota.
