# CONV-FE-017 -- ProposalConfirmPageComponent + PendingConfirmationListComponent + ProposalCardComponent + VariantPickerComponent (Tela 09)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Outcome
`libs/conv/feature-proposal-confirm` fornece Tela 09 completa (Confirmar Proposta) com `ProposalConfirmPageComponent` (smart), `PendingConfirmationListComponent`, `ProposalCardComponent` e `VariantPickerComponent` — sem nenhum campo de texto livre (invariante de controle motor), com escrita otimista e rollback em falha.

## Approach (execution path)
1. Criar `ProposalConfirmPageComponent` (smart, standalone, OnPush): injetar `ProposalConfirmFacade`; ler signal `vm: ProposalConfirmVM`.
2. Criar `PendingConfirmationListComponent` (presentacional): input `pending: ProposalVM[]`, `locale: Locale`, `status: ProposalConfirmStatus`; outputs `confirmed`, `adjusted`, `rejected`.
3. Criar `ProposalCardComponent` (presentacional): input `proposal: ProposalVM`; outputs `proposalConfirmed(id)`, `proposalAdjusted(id, variant)`, `proposalRejected(id)`.
4. Criar `VariantPickerComponent` (presentacional): input `variants: string[]`; output `variantChosen: EventEmitter<string>` — **nenhum `<input type="text">`** no template (invariante motor-control, doc 03 §5).
5. Criar `proposal-confirm.routes.ts` com `{ path: 'confirmar', component: ProposalConfirmPageComponent }`.
6. Renderizar estados first-class: `loading`, `empty` (sem propostas pendentes), `saving` (write em andamento), `error` (toast + rollback).

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/proposal-confirm-page.component.ts`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/proposal-confirm-page.component.html`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/pending-confirmation-list.component.ts`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/proposal-card.component.ts`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/variant-picker.component.ts`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/lib/proposal-confirm.routes.ts`
- [ ] implementar `libs/conv/feature-proposal-confirm/src/index.ts`

## Acceptance criteria
- [ ] Nenhum `<input type="text">` em `VariantPickerComponent` — apenas lista de variantes clicáveis
- [ ] Write failure: nó re-inserido em `pending` + toast não-bloqueante (sem modal)
- [ ] Estado `saving` renderizado enquanto write em andamento
- [ ] `npx nx test conv-feature-proposal-confirm` PASS
- [ ] `npx nx lint conv-feature-proposal-confirm` PASS
- [ ] `npx nx build conv-feature-proposal-confirm` PASS

## Dependencies
- CONV-FE-011 (ProposalConfirmFacade → ProposalConfirmVM)
- CONV-FE-018 (patientSessionGuard na rota `confirmar`)
- CONV-FE-019 (convNavegarGuard na rota `confirmar`)
- INT-FE-CONV-006 (dependência TREI documentada — MockAdapter only)

## Empresa-context impact
Nenhum.

## Prioridade
P3
