## [TREI-FE-019] ProposalListComponent + ProposedNodeCardComponent + DualConfirmationIndicatorComponent (patient read-only INV-1)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
Três componentes presentacionais em `libs/trei/feature-proposal-review`: lista de propostas, card individual com acções (Aprovar/Editar/Descartar), e indicador duplo de confirmação (cuidadora + paciente) — sem nenhum controlo de escrita no lado do paciente (INV-1 estrutural).

**Context / Problem statement:**
- `ProposedNodeCardComponent` expõe `output: approve`, `output: edit`, `output: discard` — mas **nenhum output** para o lado do paciente (INV-1, SPEC-02 §3).
- `DualConfirmationIndicatorComponent` renderiza "✅/⏳ cuidadora" e "✅/⏳ paciente" — o lado do paciente é **display-only**: sem toggle, sem output, sem controlo de edição (INV-1).
- O botão "Aprovar" é desactivado `WHILE clinicalFlag === true` (SEC-002 — conteúdo clínico bloqueado até edição) (SPEC-03 §3).
- O botão "Editar" é desactivado `WHILE caregiverApproved === true` (INV-5 — aprovado é imutável para esse locale).
- `pending: boolean` no VM activa um spinner por card durante operações em voo (SPEC-04 §4, TREI-FE-024).

**Approach (execution path):**
1. `proposal-list.component.ts`: `input()`: `proposals: ProposedNodeVM[]`, `activeLocale: Locale`. `@for` sobre proposals → `<trei-proposed-node-card>`. Forward outputs (`approve`, `edit`, `discard`) para o smart component.
2. `proposed-node-card.component.ts`: `input()`: `node: ProposedNodeVM`. Outputs: `approve: {nodeId,locale}`, `edit: {nodeId,locale}`, `discard: {nodeId,locale}`. Botão "Aprovar" `[disabled]="node.clinicalFlag || node.pending"`. Botão "Editar" `[disabled]="node.caregiverApproved || node.pending"`. Spinner visible quando `node.pending`. Nenhum output referente ao paciente.
3. `dual-confirmation-indicator.component.ts`: `input()`: `caregiverApproved: boolean`, `patientApproved: boolean`, `locale: Locale`. Template: ícone ✅/⏳ para cada lado. **Sem qualquer output** (display-only, INV-1).

**Non-goals:**
- Não implementar a lógica de aprovação (pertence à facade TREI-FE-011).
- Não adicionar toggle ou botão para `patientApproved`.

**Layer(s):**
frontend / libs/trei/feature-proposal-review

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§3 — INV-1/INV-5)

**Architecture alignment:**
- `standalone: true`, `OnPush` em todos
- Nenhum `inject` em componentes presentacionais
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-proposal-review**
- [ ] `proposal-list.component.ts`
- [ ] `proposed-node-card.component.ts` (sem output para paciente, INV-1)
- [ ] `dual-confirmation-indicator.component.ts` (display-only, sem output)
- [ ] Botão "Aprovar" `disabled` quando `clinicalFlag === true`
- [ ] Botão "Editar" `disabled` quando `caregiverApproved === true`
- [ ] Spinner visible quando `node.pending === true`

**Tests**
- [ ] `nx test trei-feature-proposal-review` PASS
- [ ] `ProposedNodeCardComponent`: nenhum `output()` referencia `patientApproved` (INV-1)
- [ ] Botão "Aprovar" desactivado quando `clinicalFlag = true`
- [ ] `DualConfirmationIndicatorComponent`: nenhum `output()` definido
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
- `grep -n 'paciente\|patientApproved' libs/trei/feature-proposal-review/src/lib/dual-confirmation-indicator.component.ts` → apenas atributo de display, nunca em `output()`
- `DualConfirmationIndicatorComponent` tem `aria-label` para cada indicador

**Dependencies:**
- TREI-FE-009 (ProposedNodeVM)
- TREI-FE-018 (smart component usa estes)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: card com "✅ cuidadora / ⏳ paciente" + botão "Aprovar" activo
- Screenshot: card com `clinicalFlag = true` → "Aprovar" cinzento com tooltip

**Risks / Notes:**
- ARIA para o `DualConfirmationIndicatorComponent`: usar `aria-label="Aprovação da cuidadora: aprovada"` / `"aguardando"` para que leitores de ecrã descrevam o estado sem depender da cor do ícone.
