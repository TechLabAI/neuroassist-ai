## [FE-EVNT-011] AlarmOverlayComponent — overlay full-bleed de emergência

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/feature-alarm/src/lib/alarm-overlay/alarm-overlay.component.ts` renderiza o overlay full-bleed de emergência quando `activeEvent.urgencia === 'emergencia'`, com `role="alertdialog"`, foco no botão de confirmação, cor `--color-alarm` e sub-componentes apresentacionais.

**Context / Problem statement:**
- SPEC-02 §1 define a árvore de componentes do `AlarmOverlayComponent`: smart, lê signals do `EventDeliveryFacade`, contém `UrgencyBadgeComponent`, `EventTimestampComponent`, `AlarmMessageComponent`, `ConfirmAttendanceButton`.
- O AlarmOutlet é renderd pelo Shell acima do router-outlet — este componente é o que o outlet carrega.
- Não há rota: é montado/desmontado pelo Shell baseado em `alarmVm.visible` (SPEC-06 §2).
- Acessibilidade: `role="alertdialog"`, focus trap no botão de confirmação enquanto visível.

**Approach (execution path):**
1) Criar `libs/evnt/feature-alarm/src/lib/alarm-overlay/alarm-overlay.component.ts` (standalone, OnPush).
2) Injetar `EventDeliveryFacade`; ler `alarmVm` signal.
3) Renderizar template com `--color-alarm` (token de SPEC-05 §2 / `@neuroassist/shared/ui-tokens`).
4) Incluir `UrgencyBadgeComponent`, `EventTimestampComponent`, `AlarmMessageComponent` (apresentacional inline), `ConfirmAttendanceButton`.
5) `ConfirmAttendanceButton` emite `(confirm)` → `AlarmOverlayComponent.onConfirm()` → `facade.confirm(eventoId, 'real')`.
6) `[disabled]="alarmVm().confirming"` no botão de confirmação (CONCUR-01 — fica desabilitado enquanto POST em voo).
7) `role="alertdialog"` no host element; `cdkTrapFocus` no botão.
8) Exportar `AlarmOverlayComponent` via `libs/evnt/feature-alarm/src/index.ts`.

**Non-goals:**
- Lógica de roteamento (não há rota).
- Registro do outlet no Shell (FE-EVNT-021).
- Lógica de confirmação (no Facade FE-EVNT-008).

**Layer(s):**
frontend / libs/evnt/feature-alarm

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§2)

**Architecture alignment:**
- Standalone + OnPush + Signals
- `role="alertdialog"` obrigatório (WCAG 2.2 — alarm interrupt)
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-alarm**
- [ ] `libs/evnt/feature-alarm/src/lib/alarm-overlay/alarm-overlay.component.ts` — standalone, OnPush
- [ ] `role="alertdialog"` no host (acessibilidade)
- [ ] Foco no `ConfirmAttendanceButton` ao montar (`cdkTrapFocus` ou `autofocus`)
- [ ] `[disabled]="alarmVm().confirming"` no botão (CONCUR-01)
- [ ] `--color-alarm` token aplicado (full-bleed background)
- [ ] `UrgencyBadgeComponent` com `input: urgencia`
- [ ] `EventTimestampComponent` com `input: recognizedAt`
- [ ] `AlarmMessageComponent` (inline) com `input: commandLabel, patientName`
- [ ] `ConfirmAttendanceButton` com `output: confirm`
- [ ] `onConfirm()` → `facade.confirm(alarmVm().event!.eventoId, 'real')`
- [ ] `libs/evnt/feature-alarm/src/index.ts` re-exporta `AlarmOverlayComponent`

**Tests**
- [ ] `nx test evnt-feature-alarm` PASS
- [ ] Unit: `alarmVm.confirming=true` → botão desabilitado
- [ ] Unit: `(confirm)` emitido → `facade.confirm` chamado com `'real'`
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
- `npx nx build evnt-feature-alarm` PASS
- Cobertura visual: overlay full-bleed vermelho, botão focado, badge de urgência visível

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `alarmVm` signal)
- FE-EVNT-018 (UrgencyBadgeComponent + EventTimestampComponent)
- FE-EVNT-012 (ConfirmAttendanceButton — pode ser na mesma issue ou separado)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /api/v1/events/{id}/confirmation` via Facade

**Testing / Evidence required:**
- Screenshot: overlay visível com badge "EMERGÊNCIA", timestamp, nome do paciente e botão habilitado
- Screenshot: overlay com botão desabilitado + spinner (estado `confirming=true`)

**Risks / Notes:**
- `cdkTrapFocus` exige `@angular/cdk` instalado — confirmar dependência no `package.json`.
- A safety invariant (alarme não fecha sem confirmação) é enforced pelo Facade — o componente apenas desabilita o botão durante o voo, nunca fecha o overlay autonomamente.
