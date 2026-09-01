## [TREI-FE-005] trei.bootstrap.ts init/destroy lifecycle

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/shell-entry/src/lib/trei.bootstrap.ts` implementa `treiBootstrap.init()` e `treiBootstrap.destroy()` — o `init` regista o breadcrumb/nav no shell, semeia o `defaultLocale` do JWT, faz um best-effort GET /health, e asserta a ausência de clientes Anthropic/ElevenLabs/ES; a `destroy` encerra a sessão activa e limpa os turns sem descartar rascunhos.

**Context / Problem statement:**
- O ciclo de vida do bootstrap é chamado pelo shell quando a lazy chunk de TREI é carregada / descarregada (SPEC-06 §3).
- `init` deve ser idempotente e não-bloqueante: uma falha no health check nunca impede a activação das rotas (doc 06 risk G-2).
- `destroy` garante INV-4 (no-loss): `pending drafts` vivem no `trei-service`, nunca no browser — `destroy` só limpa working state.
- O assert INV-2 em dev (sem cliente Anthropic/ElevenLabs/ES no grafo) é um guard de compilação, não runtime — verificar com `isDevMode()`.
- Bloqueia: TREI-FE-006 (breadcrumb registado em `init`), TREI-FE-010 (session clearada em `destroy`).

**Approach (execution path):**
1. Criar `libs/trei/shell-entry/src/lib/trei.bootstrap.ts`.
2. `init(ctx: ToolBootstrapContext)`:
   a. Chamar `TreiShellIntegrationService.registerNav(ctx)` para breadcrumb + nav (TREI-FE-006).
   b. Chamar `TrainingSessionFacade.seedLocale(ctx.session.locale)` restrito a `{pt,en}` (ADR-018).
   c. Chamar `TrainingSessionFacade.loadHealth()` best-effort (swallow errors).
   d. `if (isDevMode()) assertNoBannedClients()` — verifica que nem `@anthropic-ai/*`, `ElevenLabsClient`, nem `Client` (ES) estão no grafo (INV-2).
3. `destroy()`:
   a. Chamar `TrainingSessionFacade.endSession()` se sessão activa.
   b. Limpar `turns` signal (`TrainingSessionFacade.clearTurns()`).
   c. Chamar `TreiShellIntegrationService.deregisterNav()`.
4. Exportar `treiBootstrap` como const.

**Non-goals:**
- Não bloquear route activation em falha de health.
- Não criar lógica de sessão aqui (pertence à facade TREI-FE-010).

**Layer(s):**
frontend / libs/trei/shell-entry

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3 — `defaultLocale`)

**Architecture alignment:**
- `isDevMode()` para guard dev-only (sem penalidade de produção)
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/shell-entry**
- [ ] `libs/trei/shell-entry/src/lib/trei.bootstrap.ts`
- [ ] `init`: seedLocale + registerNav + loadHealth best-effort + INV-2 assert dev-only
- [ ] `destroy`: endSession + clearTurns + deregisterNav

**Tests**
- [ ] `nx test trei-shell-entry` PASS — teste de init: `seedLocale` chamado com `'pt'` quando JWT locale = `'pt-BR'` (normaliza para `{pt,en}`)
- [ ] Teste de destroy: `endSession` chamado; `hasPendingProposals` não é alterado
- [ ] `nx lint trei-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx test trei-shell-entry` PASS
- `npx nx lint trei-shell-entry` PASS
- `init` é idempotente (chamado duas vezes não duplica nav entries)
- `destroy` não descarta rascunhos (verificável em spy de `ProposalReviewFacade.discardDraft`)
- `destroy` chama `TrainingSessionFacade.endSession()` se `status !== 'idle'`

**Dependencies:**
- TREI-FE-006 (TreiShellIntegrationService.registerNav)
- TREI-FE-010 (TrainingSessionFacade.seedLocale, endSession)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /health` chamado em `init` best-effort

**Testing / Evidence required:**
- Spy em `TreiShellIntegrationService.registerNav` chamado 1x em `init`
- Spy em `TrainingSessionFacade.loadHealth` — swallows error, não re-throws

**Risks / Notes:**
- `ToolBootstrapContext` deve estar tipado em `@neuroassist/shared/access`; confirmar que o campo `session.locale` existe antes de implementar.
