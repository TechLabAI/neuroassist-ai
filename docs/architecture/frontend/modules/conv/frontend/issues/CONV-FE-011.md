# CONV-FE-011 -- ProposalPort + MockAdapter + ProposalConfirmFacade (G-2: MockAdapter-only until TREI-BE endpoint, @fixme e2e tracked)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`ProposalPort` e `ProposalConfirmFacade` implementam o contrato de Proposals (Tela 09) exclusivamente com `ProposalMockAdapter` enquanto o endpoint `/api/v1/conv/proposals` (ou equivalente em `trei-service`) não existir no contrato BE — conforme gap G-2 documentado. O `confirmar-proposta.feature` fica com `@fixme` ativo.

## Approach (execution path)
1. Definir `ProposalPort` com operações `listPending(locale)`, `confirm(nodeId, locale, clientEventId)`, `adjust(nodeId, variant, locale)`, `reject(nodeId, locale)`.
2. Implementar `ProposalMockAdapter`: `listPending` retorna lista fixada de `ProposalDto`; `confirm/adjust/reject` retornam success imediato (simular optimistic write).
3. **Não implementar** `ProposalHttpAdapter` nesta issue — é bloqueado pelo gap G-2 (endpoint ausente do api-contract v1). Quando o BE adicionar o endpoint, criar `ProposalHttpAdapter` em issue separada.
4. Implementar `ProposalConfirmFacade`:
   - Signal `vm: ProposalConfirmVM` (`pending`, `locale`, `status`).
   - `confirm(id)`: optimistic removal de `pending`; em falha mock → re-inserir e toast não-bloqueante (SPEC-07 §5).
   - `adjust(id, variant)`: sem texto livre (invariante motor-control).
   - `reject(id)`: optimistic removal.
5. Documentar a dependência de TREI na issue INT-FE-CONV-006.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/proposal.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/proposal.mock-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/proposal-confirm.facade.ts`
- [ ] `VariantPickerComponent` (CONV-FE-017) não renderiza nenhum campo de texto livre
- [ ] `confirmar-proposta.feature` em `apps/conv-e2e/` mantém tag `@fixme` com mensagem explícita

## Acceptance criteria
- [ ] Nenhum campo de texto livre em `adjust` — apenas seleção de `variants[]`
- [ ] Write failure re-insere o nó em `pending` e mostra toast não-bloqueante (sem modal)
- [ ] `DATA_PROVIDERS` aponta `ProposalPort` → `ProposalMockAdapter` (não http)
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- CONV-FE-012 (ProposalDto, DATA_PROVIDERS)
- INT-FE-CONV-006 (dependência cross-module TREI documentada)

## Empresa-context impact
Nenhum.

## Prioridade
P2
