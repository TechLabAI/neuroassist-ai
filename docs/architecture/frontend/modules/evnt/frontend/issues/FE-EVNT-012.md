## [FE-EVNT-012] ConfirmAttendanceButton + fluxo de confirmação inline (caminho de segurança)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`ConfirmAttendanceButton` (apresentacional) expõe `input: disabled` e `output: confirm`. O `AlarmOverlayComponent` lida com o fluxo completo: loading spinner enquanto `confirming=true`, mensagem inline de erro em falha (não toast), retry manual e tratamento de 409 como sucesso-equivalente.

**Context / Problem statement:**
- SPEC-07 §2: qualquer falha de `POST /confirmation` mantém o alarme ativo e exibe mensagem **inline** com retry — nunca toast, nunca clear silencioso.
- SPEC-07 §2: `409 feedback-terminal` → "Já confirmado por {nome} às {hora}" inline → limpar overlay (meta de segurança alcançada).
- ERR-02 (gap M3.5): `403 /errors/forbidden` → mostrar "Você não é responsável por este paciente" e NÃO limpar alarme ativo.
- CONCUR-01 (gap M3.5): confirmação dupla → botão desabilitado durante voo.
- Este é o caminho de segurança crítico do sistema.

**Approach (execution path):**
1) Criar `libs/evnt/feature-alarm/src/lib/confirm-attendance-button/confirm-attendance-button.component.ts` (standalone, OnPush, apresentacional).
2) `input: disabled: boolean` — quando `true`, botão `[disabled]` + spinner.
3) `output: confirm = new OutputEmitter<void>()` — dispara ao clique.
4) No `AlarmOverlayComponent` (FE-EVNT-011): tratar o fluxo de confirmação:
   - Sucesso 200: facade limpa `activeEvent` (já coberto no FE-EVNT-008).
   - Erro 409: exibir "Já confirmado por {nome} às {hora}" inline — limpar overlay.
   - Erro 403 forbidden: exibir "Você não é responsável por este paciente" inline — NÃO limpar overlay (ERR-02).
   - Erro 502/504/network: exibir "Não foi possível registrar agora. Tentar de novo." + botão de retry inline (SPEC-07 §4).
5) Botão retry na UI inline (não toast) para erros de rede.

**Non-goals:**
- Lógica de confirm no Facade (FE-EVNT-008).
- Overlay de fundo full-bleed (FE-EVNT-011).

**Layer(s):**
frontend / libs/evnt/feature-alarm

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` (§2, §4, §5)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§2)

**Architecture alignment:**
- Apresentacional: sem DI do Facade — só `input`/`output`
- Inline errors: NUNCA toast no caminho de segurança (SPEC-07 §5)
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-alarm**
- [ ] `libs/evnt/feature-alarm/src/lib/confirm-attendance-button/confirm-attendance-button.component.ts` — `input: disabled`, `output: confirm`
- [ ] Botão renderiza spinner quando `disabled=true` (CONCUR-01)
- [ ] Inline error region no `AlarmOverlayComponent` (não toast): para 403, 502, network
- [ ] 409 → mensagem inline "Já confirmado por {nome} às {hora}" → facade limpa overlay
- [ ] 403 forbidden → "Você não é responsável por este paciente" → overlay permanece (ERR-02)
- [ ] 502/network → "Não foi possível registrar agora. Tentar de novo." + botão retry inline
- [ ] Retry chama `facade.confirm()` novamente (manual, não auto-retry)

**Tests**
- [ ] `nx test evnt-feature-alarm` PASS
- [ ] Unit: `disabled=true` → botão `[disabled]`, spinner visível
- [ ] Unit: erro 403 → overlay não fechado, mensagem inline visível
- [ ] Unit: erro 502 → mensagem retry inline visível, overlay permanece ativo
- [ ] `nx lint evnt-feature-alarm` PASS

**Milestone:**
Wave P3 / Feature libs

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P3

**Acceptance criteria:**
- `npx nx test evnt-feature-alarm` PASS
- `npx nx lint evnt-feature-alarm` PASS
- Screenshot: erro de rede → mensagem inline "Tentar de novo" visível, alarme não fechado
- Screenshot: 403 → mensagem inline, alarme não fechado

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `confirmError` signal)
- FE-EVNT-011 (AlarmOverlayComponent — host do fluxo inline)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /api/v1/events/{id}/confirmation` — respostas 200, 409, 403, 502

**Testing / Evidence required:**
- Unit: mock de `confirmError` com `AppError.kind === 'Forbidden'` → "Você não é responsável" visível

**Risks / Notes:**
- ERR-02 gap (M3.5): 403 NÃO fecha o alarme — isso é safety-critical. Verificar que o Facade não limpa `activeEvent` em 403.
- O "retry" é **manual** (caregiver clica) — nunca auto-retry silencioso no caminho de confirmação de emergência.
