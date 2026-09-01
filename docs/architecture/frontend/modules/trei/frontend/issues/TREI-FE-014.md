## [TREI-FE-014] ChatConversationPanelComponent + ChatBubbleComponent + AssistantThinkingIndicatorComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
Três componentes presentacionais em `libs/trei/feature-training-session`: `ChatConversationPanelComponent` (orquestra os bubbles + indica `assistant-thinking`), `ChatBubbleComponent` (renderiza um turno caregiver ou assistente), e `AssistantThinkingIndicatorComponent` (exibido WHILE `status = 'assistant-thinking'`).

**Context / Problem statement:**
- `ChatConversationPanelComponent` recebe `turns: ConversationTurnVM[]`, `status: SessionStatus`, `aiDisclosed: boolean` como `input()` e emite `caregiverTurnSubmit: EventEmitter<string>` (SPEC-02 §2).
- `ChatBubbleComponent` recebe `turn: ConversationTurnVM` e renderiza a bolha com estilo diferente para `author: 'caregiver'` vs `author: 'assistant'`.
- `AssistantThinkingIndicatorComponent` é mostrado WHILE `status = 'assistant-thinking'` — um spinner/animação que indica que o LLM está a processar (SPEC-03 §2 `SessionStatus`).
- INV-2: o componente nunca adiciona um turno de assistente fabricado — `turns` é apenas o que chega via `input()`.
- Bloqueia: TREI-FE-013 (usa estes componentes).

**Approach (execution path):**
1. Criar `libs/trei/feature-training-session/src/lib/chat-conversation-panel.component.ts`:
   - `input()`: `turns: ConversationTurnVM[]`, `status: SessionStatus`, `aiDisclosed: boolean`.
   - `output()`: `caregiverTurnSubmit: EventEmitter<string>`.
   - Template: `@for (turn of turns) { <trei-chat-bubble [turn]="turn" /> }` + `@if (status === 'assistant-thinking') { <trei-assistant-thinking-indicator /> }`.
2. Criar `libs/trei/feature-training-session/src/lib/chat-bubble.component.ts`:
   - `input()`: `turn: ConversationTurnVM`.
   - Renderiza estilo CSS distinto para `caregiver` vs `assistant`.
   - Badge "gerado por IA" em bolhas de assistente se `aiDisclosed`.
3. Criar `libs/trei/feature-training-session/src/lib/assistant-thinking-indicator.component.ts`:
   - Sem inputs — é mostrado condicionalmente pelo pai.
   - Animação de loading (spinner ou dots) via tokens de `@neuroassist/shared/ui-feedback`.

**Non-goals:**
- Não gerir o estado da sessão (apenas `input()`/`output()`).
- Não implementar `ChatComposerComponent` aqui (TREI-FE-015).

**Layer(s):**
frontend / libs/trei/feature-training-session

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)

**Architecture alignment:**
- `standalone: true`, `OnPush` em todos os 3 componentes
- Nenhum `inject` de facade nos componentes presentacionais
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-training-session**
- [ ] `chat-conversation-panel.component.ts`
- [ ] `chat-bubble.component.ts`
- [ ] `assistant-thinking-indicator.component.ts`

**Tests**
- [ ] `nx test trei-feature-training-session` PASS
- [ ] `ChatConversationPanelComponent`: `@for` renderiza N bubbles para N turns
- [ ] `AssistantThinkingIndicatorComponent` visível quando `status = 'assistant-thinking'`, oculto caso contrário
- [ ] `ChatBubbleComponent`: classe CSS diferente para `caregiver` vs `assistant`
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
- Nenhum `inject()` em componentes presentacionais
- `AssistantThinkingIndicatorComponent` tem `aria-label` (a11y)

**Dependencies:**
- TREI-FE-009 (ConversationTurnVM, SessionStatus)
- TREI-FE-013 (usa estes componentes — dependência inversa para testes de integração)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (dados chegam via input())

**Testing / Evidence required:**
- Screenshot: painel com 2 bubbles (caregiver + assistant) + thinking indicator activo
- ARIA: `role="log"` no painel + `aria-busy="true"` durante thinking

**Risks / Notes:**
- O badge "gerado por IA" em bolhas de assistente: confirmar se `aiDisclosed` chega via `input()` do painel ou via propagação interna — per SPEC-02, chega via `input` ao painel que passa para cada bubble.
