## [INT-FE-DASH-005] Write-through confirm event → EVNT (POST /panel/events/{eventId}/confirm)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
O DASH FE faz proxy de confirmação de evento para EVNT via `DashApiService.confirmEvent()`, sem armazenar o feedback localmente. Em falha, propaga o erro do upstream via toast.

**Context / Problem statement:**
- EVNT é o dono da confirmação; DASH apenas roteia via BFF (`dash-service`).
- O cuidador confirma (real/falso_positivo) no contexto da notificação ou do painel.
- Fonte: SPEC-04 §2 (EventConfirm row); SPEC-07 §2 (`#upstream-rejected`).

**Approach (execution path):**
1. `DashApiService.confirmEvent(eventId, req: ConfirmRequest)` já definido em FE-DASH-005.
2. No componente de notificação (`NotificationListItemComponent` ou um dialog) adicionar botão "Confirmar" com select de feedback.
3. Em sucesso: atualizar UI localmente (marcar como confirmado); nenhum persist local.
4. Em falha (`#upstream-rejected`): toast com mensagem relayed; não armazenar o feedback.
5. Sem auto-retry.

**Non-goals:**
- Não modificar EVNT service.
- Não armazenar feedback localmente de forma durável.

**Layer(s):**
frontend / libs/dash/feature-notifications (type:feature)

**Bounded context:**
dash ↔ evnt (write-through via dash-service BFF)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§2 EventConfirm row)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2)

**Architecture alignment:**
- Write-through pattern; sem estado local durável
- Tag Nx: `scope:dash`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/dash/feature-notifications**
- 🔲 Componente/dialog de confirmação de evento (feedback: real | falso_positivo)
- 🔲 Tratamento de erro: toast para #upstream-rejected

**Tests**
- 🔲 `npx nx test dash-feature-notifications` PASS (confirmEvent chamado; upstream-rejected → toast; sem persist local)
- 🔲 `npx nx lint dash-feature-notifications` PASS

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:dash
priority:P4

**Acceptance criteria:**
- `npx nx test dash-feature-notifications` PASS
- `npx nx lint dash-feature-notifications` PASS
- `npx nx build dash-feature-notifications` PASS
- Sucesso: feedback não armazenado localmente de forma durável
- `#upstream-rejected` → toast com mensagem relayed
- Sem re-submit automático

**Dependencies:**
- FE-DASH-005 (DashApiService.confirmEvent)
- FE-DASH-013 (NotificationCenterPageComponent)

**Data impact:**
- Nenhum (DASH não persiste feedback)

**API / Contract impact:**
- `POST /panel/events/{eventId}/confirm`

**Testing / Evidence required:**
- Test com DashApiService mockado

**Risks / Notes:**
- Definir UX do confirm (inline no item de notificação ou dialog) antes de implementar o componente.
