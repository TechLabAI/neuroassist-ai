# CONV-INT-006 -- TREI cross-module ProposalPort dependency (G-2: confirmar-proposta.feature @fixme — endpoint ABSENT from conv-service v1, belongs to TREI authoring flow)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
A dependência cross-module entre CONV (consumidor de Proposals) e TREI (autoria/aprovação) está documentada no `backend-api-cross-link.md`, com o status `@fixme` do `confirmar-proposta.feature` formalmente registrado; nenhuma issue de implementação de backend é criada aqui (responsabilidade do backlog de TREI).

## Approach (execution path)
1. Atualizar `docs/architecture/frontend/modules/conv/frontend/contracts/backend-api-cross-link.md` com seção `## Proposals (G-2 — Cross-Module Dependency)`:
   - Endpoint `/api/v1/conv/proposals` (ou equivalente TREI) **ausente do api-contract v1 do conv-service**.
   - Provavelmente pertence ao módulo TREI (autoria — `confirmadoCuidadora: true, confirmadoPaciente: false`) e não ao CONV runtime.
   - CONV-FE: `ProposalPort → ProposalMockAdapter` até o BE adicionar o endpoint (CONV-FE-011).
   - `confirmar-proposta.feature` mantém `@fixme` com a mensagem: "Aguarda endpoint de proposals do TREI-BE — ver backend-api-cross-link §Proposals (G-2)".
   - Decisão: **não criar issue de BE aqui** — isso é responsabilidade do backlog do TREI quando for gerado.
2. Garantir que a mensagem `@fixme` no feature file seja rastreável: mencionar CONV-INT-006 e o módulo TREI.

## Implementation checklist (by layer/file)
- [ ] atualizar `docs/architecture/frontend/modules/conv/frontend/contracts/backend-api-cross-link.md` (seção Proposals G-2)
- [ ] verificar que `confirmar-proposta.feature` tem tag `@fixme` com mensagem explícita de rastreamento
- [ ] nenhuma issue de implementação de BE de proposals criada no backlog CONV

## Acceptance criteria
- [ ] `backend-api-cross-link.md` contém seção G-2 com status `ausente do api-contract v1` e referência ao TREI
- [ ] `@fixme` de `confirmar-proposta.feature` referencia CONV-INT-006 e o módulo TREI
- [ ] Revisão: quando o backlog TREI for gerado, criar issue TREI correspondente para o endpoint de proposals

## Dependencies
- `docs/architecture/frontend/modules/conv/frontend/contracts/backend-api-cross-link.md`
- CONV-FE-011 (ProposalMockAdapter — FE-side da dependência)
- Backlog TREI (quando gerado — criar issue de BE correspondente)

## Empresa-context impact
Nenhum.

## Prioridade
P4
