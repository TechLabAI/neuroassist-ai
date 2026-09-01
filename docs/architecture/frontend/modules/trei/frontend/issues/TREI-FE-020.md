## [TREI-FE-020] ProposalEditDialogComponent + ApprovalSummaryBarComponent + ProposalEmptyStateComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
Três componentes presentacionais complementares na `libs/trei/feature-proposal-review`: diálogo de edição de proposta (pré-aprovação), barra de resumo de aprovações, e estado vazio da lista.

**Context / Problem statement:**
- `ProposalEditDialogComponent`: abre via `output: edit` do card — permite editar `texto` de um nó antes da aprovação caregiver (INV-5: só editável antes de aprovar). Emite `output: save(string)` → `facade.materializeOrEditProposal()`. Valida comprimento máximo e campo não-vazio.
- `ApprovalSummaryBarComponent`: exibe `awaitingCaregiver`, `awaitingPatient`, `readyBothSides` da `ApprovalSummaryVM` — um contador visual do progresso de aprovação dual para o locale activo (SPEC-03 §4).
- `ProposalEmptyStateComponent`: exibido quando `proposals().length === 0` para o locale activo — mensagem "Nenhum rascunho para PT/EN" (SPEC-02 §3).

**Approach (execution path):**
1. `proposal-edit-dialog.component.ts`: `input()`: `draftText: string`, `maxLen: number`. `output()`: `save: OutputEmitterRef<string>`, `cancel: OutputEmitterRef<void>`. Valida não-vazio e `<= maxLen` antes de emitir `save`. Usa `@neuroassist/shared/ui-feedback` Dialog wrapper.
2. `approval-summary-bar.component.ts`: `input()`: `summary: ApprovalSummaryVM`. Template: 3 contadores com labels "A aguardar cuidadora", "A aguardar paciente", "Ambas aprovadas". Display-only.
3. `proposal-empty-state.component.ts`: `input()`: `activeLocale: Locale`. Template: "Nenhum rascunho para {PT|EN}. Comece uma conversa de treino."

**Non-goals:**
- Não chamar a facade directamente nos componentes presentacionais.
- Não validar conteúdo clínico (a validação de `clinicalFlag` é feita pelo BE e reflectida no VM).

**Layer(s):**
frontend / libs/trei/feature-proposal-review

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§4)

**Architecture alignment:**
- `standalone: true`, `OnPush` em todos
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-proposal-review**
- [ ] `proposal-edit-dialog.component.ts` (valida maxLen e não-vazio)
- [ ] `approval-summary-bar.component.ts` (3 contadores display-only)
- [ ] `proposal-empty-state.component.ts` (label com locale activo)

**Tests**
- [ ] `nx test trei-feature-proposal-review` PASS
- [ ] `ProposalEditDialogComponent`: `save` não emitido com texto vazio
- [ ] `ProposalEditDialogComponent`: `save` não emitido quando `texto.length > maxLen`
- [ ] `ApprovalSummaryBarComponent`: 3 contadores corretos para `summary` dado
- [ ] `nx lint trei-feature-proposal-review` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P3

**Acceptance criteria:**
- `npx nx test trei-feature-proposal-review` PASS
- `npx nx lint trei-feature-proposal-review` PASS
- `ProposalEditDialogComponent`: campo inválido exibe mensagem inline (não toast)
- `ApprovalSummaryBarComponent`: `aria-label` descritivo para cada contador

**Dependencies:**
- TREI-FE-009 (ApprovalSummaryVM, Locale)
- TREI-FE-018 (smart component usa estes)
- TREI-FE-019 (`ProposedNodeCardComponent` abre o diálogo via output)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum directo

**Testing / Evidence required:**
- Screenshot do diálogo de edição com campo de texto e botões Guardar/Cancelar
- Screenshot da barra de resumo com 3 categorias de aprovação

**Risks / Notes:**
- O `ProposalEditDialogComponent` pode usar o CDK Dialog do Angular Material em vez de um dialog custom — verificar se `@neuroassist/shared/ui-feedback` já wrapa o CDK.
