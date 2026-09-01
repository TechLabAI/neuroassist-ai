## [TREI-FE-016] ConversationErrorPanelComponent + EmptyStateComponent + EndSessionButtonComponent + SessionEndedNoticeComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
Quatro componentes presentacionais de estado de sessão em `libs/trei/feature-training-session`: painel de erro da conversa (retriable), estado vazio (CTA iniciar), botão de encerrar sessão, e aviso de sessão encerrada com rascunhos pendentes.

**Context / Problem statement:**
- `ConversationErrorPanelComponent`: exibido WHILE `status = 'error'`; mostra mensagem do `AppError` (nunca o `correlationId` raw) e botão "Tentar novamente" → `output: retry` (SPEC-07 §3, G5).
- `EmptyStateComponent`: exibido WHILE `status = 'idle'`; CTA "Iniciar treinamento" → `output: start` (SPEC-02 §2/SPEC-03 §2).
- `EndSessionButtonComponent`: sempre visível quando sessão activa; output: `end` → `facade.endSession()` (SPEC-02 §2).
- `SessionEndedNoticeComponent`: exibido WHILE `status = 'ended' && hasPendingProposals`; alerta que há rascunhos aguardando aprovação do paciente (INV-4 — rascunhos preservados) (SPEC-02 §2).

**Approach (execution path):**
1. `conversation-error-panel.component.ts`: `input(error: AppError | null)`, `output(retry)`. Template: mensagem product-level do `error.message`; `data-testid="correlation-id"` ausente (G5 — não renderizar id raw). Botão "Tentar novamente".
2. `empty-state.component.ts`: `output(start)`. Template: ilustração/ícone + CTA "Iniciar treinamento".
3. `end-session-button.component.ts`: `output(end)`. Template: botão "Encerrar sessão" com confirmação (dialog simples via `@neuroassist/shared/ui-feedback`).
4. `session-ended-notice.component.ts`: sem inputs além de `@if` controlado pelo pai. Exibe "Sessão encerrada. Há rascunhos aguardando aprovação." com link para `/treinamento/sessao/propostas`.

**Non-goals:**
- Não implementar lógica de retry (pertence à facade via `output`).
- Não mostrar `correlationId` ao utilizador (G5).

**Layer(s):**
frontend / libs/trei/feature-training-session

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§3/§4)

**Architecture alignment:**
- `standalone: true`, `OnPush` em todos
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-training-session**
- [ ] `conversation-error-panel.component.ts` (G5: sem correlationId no template)
- [ ] `empty-state.component.ts`
- [ ] `end-session-button.component.ts` (confirmação antes de encerrar)
- [ ] `session-ended-notice.component.ts` (link para /treinamento/sessao/propostas)

**Tests**
- [ ] `nx test trei-feature-training-session` PASS
- [ ] `ConversationErrorPanelComponent`: nenhum elemento no DOM contém `correlationId` raw (G5)
- [ ] `EmptyStateComponent`: botão CTA emite `start`
- [ ] `SessionEndedNoticeComponent`: link para `/treinamento/sessao/propostas` presente
- [ ] `nx lint trei-feature-training-session` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P3

**Acceptance criteria:**
- `npx nx test trei-feature-training-session` PASS
- `npx nx lint trei-feature-training-session` PASS
- `ConversationErrorPanelComponent` não contém texto que reproduza `correlationId` (G5)
- `EmptyStateComponent` tem `role="main"` ou equivalente a11y

**Dependencies:**
- TREI-FE-012 (AppError — input() do error panel)
- TREI-FE-013 (usa estes componentes)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot do estado `error` com painel + botão retry
- Screenshot do estado `idle` com empty state
- Teste DOM: `querySelector('[data-correlationid]')` retorna null

**Risks / Notes:**
- O botão "Encerrar sessão" pode precisar de confirmação (dialog) para evitar perdas acidentais — usar `@neuroassist/shared/ui-feedback` ConfirmDialog; detalhe de UX a confirmar no scaffold.
