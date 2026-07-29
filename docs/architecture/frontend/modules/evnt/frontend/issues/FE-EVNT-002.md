## [FE-EVNT-002] evnt.routes.ts — route table com guards (authGuard + evntGuard)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/shell-entry/src/lib/evnt.routes.ts` define as duas rotas (`/eventos`, `/eventos/:eventoId`) com `canActivate: [authGuard, evntGuard]`, títulos e breadcrumbs, prontas para o `loadChildren` do Shell.

**Context / Problem statement:**
- SPEC-01 define dois paths roteados: `/eventos` → `EventFeedPageComponent` e `/eventos/:eventoId` → `EventDetailPageComponent`, ambos com duplo guard.
- GUARD-01 gap (M3.5): `authGuard` falha → redirecionar para login (sem montar sub-tree do EVNT).
- GUARD-02 gap (M3.5): `evntGuard` falha → redirecionar para `/eventos` bloqueado ou Home.
- `AlarmOutlet` é deliberadamente **não** uma rota (ADR-005); não deve aparecer aqui.
- Bloqueia: FE-EVNT-003 (EvntModule), FE-EVNT-013 (EventFeedPageComponent), FE-EVNT-016 (EventDetailPageComponent).

**Approach (execution path):**
1) Criar `libs/evnt/shell-entry/src/lib/evnt.routes.ts` com `Routes` array exportado.
2) Rota `/eventos`: `component: EventFeedPageComponent`, `canActivate: [authGuard, evntGuard]`, `title: 'Eventos'`.
3) Rota `/eventos/:eventoId`: `component: EventDetailPageComponent`, `canActivate: [authGuard, evntGuard]`, `title: 'Evento'` (resolvido dinâmicamente pelo componente).
4) Garantir que `authGuard` (shared) redireciona para o Shell login path ao falhar (GUARD-01).
5) Garantir que `evntGuard` retorna `UrlTree` para `/eventos` (bloqueado) ao falhar verificação de vínculo (GUARD-02).
6) Importar componentes lazy (sem eager import) para manter bundle splitting.

**Non-goals:**
- Implementação interna do `evntGuard` (FE-EVNT-020).
- Registro da rota no Shell (FE-EVNT-003 — via `loadChildren`).

**Layer(s):**
frontend / shell-entry (routing)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` (§2, §4)

**Architecture alignment:**
- `canActivate` com array `[authGuard, evntGuard]` — ordem importa (auth primeiro)
- Tag Nx: `scope:evnt`, `type:domain`

**Implementation checklist (by file):**

**Lib shell-entry**
- [ ] `libs/evnt/shell-entry/src/lib/evnt.routes.ts` — `Routes` exportado
- [ ] Rota `/eventos` com `canActivate: [authGuard, evntGuard]` e `title: 'Eventos'`
- [ ] Rota `/eventos/:eventoId` com `canActivate: [authGuard, evntGuard]`
- [ ] `authGuard` redireciona para login (GUARD-01 — verificar config shared)
- [ ] `libs/evnt/shell-entry/src/index.ts` re-exporta `EVNT_ROUTES`

**Tests**
- [ ] `nx test evnt-shell-entry` PASS (unit: guards aplicados às rotas corretas)
- [ ] `nx lint evnt-shell-entry` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test evnt-shell-entry` PASS
- `npx nx lint evnt-shell-entry` PASS
- `npx nx build evnt-shell-entry` PASS
- Navegação para `/eventos` sem sessão → redirect login (GUARD-01)
- Navegação para `/eventos` sem vínculo tenant → redirect /eventos blocked (GUARD-02)

**Dependencies:**
- FE-EVNT-001 (contrato do módulo)
- FE-EVNT-019 (authGuard wiring)
- FE-EVNT-020 (evntGuard implementação)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum diretamente

**Testing / Evidence required:**
- Teste de rota: RouterTestingHarness navega para `/eventos` com mock de guard retornando `false` → verifica redirect

**Risks / Notes:**
- GUARD-01 gap (M3.5): se o `authGuard` shared já existe, apenas validar que está configurado com o redirect certo para este módulo.
- GUARD-02 gap (M3.5): `evntGuard` não existe ainda — implementado em FE-EVNT-020, mas a rota já deve referenciá-lo.
