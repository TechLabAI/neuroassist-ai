## [TREI-FE-015] ChatComposerComponent (disabled while assistant-thinking/error)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/feature-training-session/src/lib/chat-composer.component.ts` implementa o campo de composição de mensagem da cuidadora: `input()` `disabled: boolean`, `output()` `submit: EventEmitter<string>` — desactivado enquanto `status ∈ {assistant-thinking, error}` para evitar que um turno caia silenciosamente.

**Context / Problem statement:**
- A cuidadora nunca deve conseguir submeter um novo turno enquanto o anterior está a ser processado (`assistant-thinking`) ou em estado de erro não-tratado (`error`) — a SPEC diz "never lets a turn silently drop" (SPEC-02 §2).
- O `disabled` é calculado pelo componente pai (`ChatConversationPanelComponent`) e passado via `input()` — o composer não lê `SessionStatus` directamente.
- A composição de texto do utilizador é state local do componente (não vai para a facade até `submit`).
- O campo deve limpar-se após `submit` bem-sucedido.

**Approach (execution path):**
1. Criar `chat-composer.component.ts`: `standalone: true`, `OnPush`.
2. `input()`: `disabled: boolean`.
3. `output()`: `submit: OutputEmitterRef<string>`.
4. Template: `<textarea>` + botão "Enviar" — ambos `[disabled]="disabled"`.
5. Ao `submit`: emite o texto e limpa o textarea.
6. Ctrl+Enter atalho de teclado para enviar (a11y).

**Non-goals:**
- Não chamar a facade directamente.
- Não implementar SSE streaming (deferred via `streamingConversation: false`).

**Layer(s):**
frontend / libs/trei/feature-training-session

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)

**Architecture alignment:**
- `standalone: true`, `OnPush`
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-training-session**
- [ ] `chat-composer.component.ts`
- [ ] `disabled` via `input()`, `submit` via `output()`
- [ ] Auto-clear após submit

**Tests**
- [ ] `nx test trei-feature-training-session` PASS
- [ ] `submit` não emite quando `disabled = true`
- [ ] Textarea limpa após `submit` bem-sucedido
- [ ] Ctrl+Enter emite `submit`
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
- Botão "Enviar" `disabled` quando `disabled === true`
- `aria-disabled` presente no campo quando desactivado

**Dependencies:**
- TREI-FE-014 (ChatConversationPanelComponent usa este componente)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Teste: `disabled = true` → click no botão → `submit` não emitido
- Screenshot: campo cinzento quando `status = 'assistant-thinking'`

**Risks / Notes:**
- Limpar textarea após submit: usar `FormsModule` + `[(ngModel)]` ou `viewChild<ElementRef>()` para reset. Preferir `signal`-based form control se disponível.
