## [TREI-FE-025] patient-not-found start-surface banner (G4) + health-degraded authoring affordance

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`TrainingSessionPageComponent` exibe um banner de erro de arranque quando `POST /sessoes` responde `patient-not-found 404` (G4) — mostrando "Paciente não encontrado nesta empresa." na superfície de início, sem criar sessão; e exibe um aviso de capacidades degradadas quando `GET /health` indica `degradedCapabilities`.

**Context / Problem statement:**
- **G4 (gap do specs-validate):** a SPEC-07 §2 define `patient-not-found 404` → "show start-surface banner, NOT create session" — mas nenhum cenário e2e cobria este caminho de erro de início de sessão. Esta issue garante que o `TrainingSessionFacade.startSession()` trata este erro sem transitar o `status` para `'active'`.
- **Health degraded:** quando `GET /health` retorna `degradedCapabilities` (ex.: LLM indisponível), a `TrainingSessionPageComponent` deve exibir um aviso "Autoria pode não estar disponível" — nunca bloqueia a superfície (best-effort, SPEC-06 §3).
- O `patient-not-found` **não é retriable** (SPEC-07 §2) — o banner não tem botão "Tentar novamente".
- O `correlationId` do erro é armazenado no `AppError.metadata` para logging mas **nunca** exibido na UI (G5).

**Approach (execution path):**
1. Em `TrainingSessionFacade.startSession()` (TREI-FE-010 — extensão):
   - Em caso de `patient-not-found (404)`: manter `status = 'idle'`, emitir `AppError { code: 'PATIENT_NOT_FOUND', retriable: false }` no campo `error` do VM.
   - NÃO transitar para `status = 'active'`.
2. Em `TrainingSessionPageComponent` (TREI-FE-013 — extensão):
   - `@if (session().status === 'idle' && session().error?.code === 'PATIENT_NOT_FOUND') { <trei-start-error-banner [error]="session().error" /> }`.
   - `@if (healthDegraded()) { <trei-health-degraded-notice /> }`.
3. Criar `start-error-banner.component.ts` em `feature-training-session`: `input()` `error: AppError`, template com mensagem sem `correlationId` (G5).
4. Criar `health-degraded-notice.component.ts`: aviso informacional sem bloquear acesso.

**Non-goals:**
- Não implementar retry para `patient-not-found` (não é retriable).
- Não exibir `correlationId` em nenhuma mensagem de UI.

**Layer(s):**
frontend / libs/trei/feature-training-session · libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§2 — patient-not-found)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3 — health best-effort)

**Architecture alignment:**
- Signals-only; status permanece `'idle'` em `patient-not-found`
- Tag Nx: `scope:trei`, `type:feature` / `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `training-session.facade.ts` — `startSession` tratando `PATIENT_NOT_FOUND` sem mudar status

**libs/trei/feature-training-session**
- [ ] `start-error-banner.component.ts` (mensagem sem correlationId, G4/G5)
- [ ] `health-degraded-notice.component.ts` (aviso informacional)
- [ ] `training-session-page.component.ts` — condicional para os dois banners

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `startSession()` com `patient-not-found 404` → `status` permanece `'idle'`, `error.code === 'PATIENT_NOT_FOUND'` (G4)
- [ ] Banner de erro de início visível quando `PATIENT_NOT_FOUND`
- [ ] Nenhum elemento no template contém `correlationId` raw (G5)
- [ ] `nx lint` PASS

**Milestone:**
Wave 7 / UX Polish

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P7

**Acceptance criteria:**
- `npx nx test` PASS
- `startSession` com 404 `patient-not-found` → `session().status === 'idle'`, banner visível (G4)
- `session().error.message` não contém `correlationId` (G5)
- `health-degraded-notice` visível e não bloqueia acesso à superfície

**Dependencies:**
- TREI-FE-010 (TrainingSessionFacade.startSession)
- TREI-FE-012 (TreiErrorMapper — mapeia patient-not-found)
- TREI-FE-013 (TrainingSessionPageComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /sessoes` retorna `404 patient-not-found`
- `GET /health` retorna `degradedCapabilities`

**Testing / Evidence required:**
- Screenshot: banner "Paciente não encontrado nesta empresa." na superfície de início (G4)
- e2e coberto em TREI-FE-030 (`erros-e-degradacao.feature`)

**Risks / Notes:**
- G5: confirmar que `start-error-banner.component.ts` não exibe `error.metadata.correlationId` — o `metadata` é para logging apenas.
