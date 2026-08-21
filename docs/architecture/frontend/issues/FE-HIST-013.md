## [FE-HIST-013] hist ui: FeedbackActionComponent (4 estados, non-optimistic)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FeedbackActionComponent` em `libs/hist/ui` — controle de veredicto com 4 estados (`idle`, `marking`, `marked`, `mark-error`) implementando a garantia non-optimistic e o reconcile de 409.

**Context / Problem statement:**
- SPEC-02 §4: inputs `marking: MarkingState`, `verdict: Verdict | null`; output `mark: EventEmitter<Verdict>`.
- SPEC-05 §4: `idle` → botão "Foi real?"; `marking` → spinner por item + controle desabilitado; `marked` → chip "✓ Marcado real"/"✓ Falso positivo" (texto+ícone); `mark-error` → "Não foi possível registrar. Tentar de novo."
- SPEC-06 §4: veredicto NUNCA exibido otimisticamente antes do backend confirmar.
- FE-GAP-15/16 (WCAG AA): hit targets ≥ 44×44px; texto+ícone (não cor só); focus ring visível.
- Teclado: escolha de veredicto navegável por teclado; `Esc` cancela; foco retorna ao botão trigger.

**Approach (execution path):**
1. `FeedbackActionComponent` standalone, OnPush.
2. 4 estados renderizados via `@switch(marking)`.
3. Estado `idle`: `<button (click)="openChoice()">Foi real?</button>` → mostra grupo de botões `real`/`falso_positivo`.
4. Estado `marking`: spinner per-item + `[disabled]`.
5. Estado `marked`: chip com `[verdict]`; text + ícone.
6. Estado `mark-error`: mensagem + botão de retry (re-emite `mark`).
7. Hit target CSS: `min-width: 44px; min-height: 44px`.
8. `Esc` listener fecha o grupo de escolha e retorna foco.

**Non-goals:**
- Não implementar a chamada HTTP — responsabilidade da Facade (FE-HIST-008).

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-05-ui-ux-spec.md` (§4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-06-error-states.md` (§1, §4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§3, §5)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/feedback-action/feedback-action.component.ts`
- 🔲 `libs/hist/ui/src/lib/feedback-action/feedback-action.component.html`
- 🔲 `libs/hist/ui/src/lib/feedback-action/feedback-action.component.scss`
- 🔲 `libs/hist/ui/src/lib/feedback-action/feedback-action.component.spec.ts`
- 🔲 Atualizar `libs/hist/ui/src/index.ts`
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- `marking=marking` → botão desabilitado; verdict não exibido.
- `marked` → chip com texto e ícone (não só cor).
- `mark-error` → item permanece `nao_avaliado`; retry re-emite `mark`.
- Hit targets ≥ 44×44px verificados em unit test.
- Foco retorna ao trigger após `Esc`.

**Dependencies:**
- FE-HIST-009, FE-HIST-020, FE-HIST-021

**Risks / Notes:**
- Garantia non-optimistic é safety-critical para a trilha de auditoria — testar explicitamente.
