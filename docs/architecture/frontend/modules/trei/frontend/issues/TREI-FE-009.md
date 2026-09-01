## [TREI-FE-009] View model interfaces + DTO types (TrainingSessionVM, ProposedNodeVM, ApprovalSummaryVM)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/models/` contém as interfaces de VM e os tipos primitivos compartilhados: `Locale`, `SessionStatus`, `NodeSubEstado`, `ConversationTurnVM`, `TrainingSessionVM`, `ProposedNodeVM`, `ApprovalSummaryVM`.

**Context / Problem statement:**
- Os VMs são os contratos de dados entre as facades e os componentes smart — componentes nunca vêem DTOs de HTTP (SPEC-03).
- `ProposedNodeVM.patientApproved` é READ-ONLY (sem setter) — a interface não deve ter método ou campo mutável para o lado do paciente (INV-1).
- `ProposedNodeVM.editable` deriva de `!caregiverApproved` — é um campo calculado que os componentes lêem directamente, não calculam (INV-5).
- `Locale = 'pt' | 'en'` é o tipo raiz que todas as outras interfaces usam — deve ser exportado do barrel da lib.
- Bloqueia: TREI-FE-007/008 (adapters usam o tipo `Locale`), TREI-FE-010/011 (facades usam os VMs), TREI-FE-003 (feature-flags usa `Locale`).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/models/` com os seguintes arquivos:
   - `locale.types.ts`: `export type Locale = 'pt' | 'en';`
   - `session-status.types.ts`: `SessionStatus` (idle/active/assistant-thinking/ended/error)
   - `node-sub-estado.types.ts`: `NodeSubEstado` (rascunho/aguardando_aprovacao/ativo/descartado)
   - `training-session.vm.ts`: `ConversationTurnVM`, `TrainingSessionVM`
   - `proposed-node.vm.ts`: `ProposedNodeVM` (com `patientApproved: boolean` somente leitura — usar `readonly`)
   - `approval-summary.vm.ts`: `ApprovalSummaryVM`
2. Exportar tudo do barrel `libs/trei/data-access/src/index.ts`.

**Non-goals:**
- Não implementar lógica de transformação aqui (os mappers ficam nos adapters/facades).
- Não adicionar métodos a interfaces (só dados).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§1..§4)

**Architecture alignment:**
- `readonly patientApproved: boolean` em `ProposedNodeVM` (INV-1)
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access/src/lib/models**
- [ ] `locale.types.ts` (`Locale`)
- [ ] `session-status.types.ts` (`SessionStatus`)
- [ ] `node-sub-estado.types.ts` (`NodeSubEstado`)
- [ ] `training-session.vm.ts` (`ConversationTurnVM`, `TrainingSessionVM`)
- [ ] `proposed-node.vm.ts` (`ProposedNodeVM` com `readonly patientApproved`)
- [ ] `approval-summary.vm.ts` (`ApprovalSummaryVM`)
- [ ] `libs/trei/data-access/src/index.ts` (re-exporta todos os tipos)

**Tests**
- [ ] Compilação TypeScript: `nx build trei-data-access` PASS
- [ ] Teste TypeScript type-check: tentar atribuir a `node.patientApproved` causa erro de compilação (readonly)

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P2

**Acceptance criteria:**
- `npx nx build trei-data-access` PASS
- `ProposedNodeVM.patientApproved` é `readonly` (TypeScript erro ao tentar atribuir)
- `Locale` está no barrel e é importável como `import { Locale } from '@neuroassist/trei/data-access'`
- `TrainingSessionVM.status` é do tipo `SessionStatus` (não `string`)

**Dependencies:**
- Nenhuma (tipos base)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (interfaces apenas)

**Testing / Evidence required:**
- `tsc --noEmit` sem erros sobre atribuição a `readonly`

**Risks / Notes:**
- Garantir que `NodeSubEstado` inclui todos os valores de `api-contract §2.6 subEstado` — actualmente: `rascunho`, `aguardando_aprovacao`, `ativo`, `descartado`.
