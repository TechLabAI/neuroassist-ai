# CONV-FE-012 -- DTOs barrel (TreeNodeDto, SearchResponseDto, SelectRequestDto, BindingResponseDto, ProposalDto) + DATA_PROVIDERS binding

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/10 itens

## Outcome
`libs/conv/data-access` expõe todos os DTOs de contrato BE como tipos TypeScript `readonly` (espelhos exatos do api-contract — nunca redefinições), e `DATA_PROVIDERS` como `Provider[]` que liga Port→Adapter (MockAdapter por padrão em Estágio 0; HttpAdapter em produção).

## Approach (execution path)
1. Criar `dto/tree-response.dto.ts` com `TreeResponseDto` e `TreeNodeDto` (mirrors de api-contract §2.1).
2. Criar `dto/search-response.dto.ts` com `SearchRequestDto` e `SearchResponseDto` (mirrors de api-contract §2.2).
3. Criar `dto/select-request.dto.ts` com `SelectRequestDto` e `SelectResponseDto` (mirrors de api-contract §2.3).
4. Criar `dto/binding-response.dto.ts` com `BindingRequestDto` e `BindingResponseDto` (mirrors de api-contract §2.4).
5. Criar `dto/proposal.dto.ts` com `ProposalDto` (contrato FE — gap G-2, aguardando api-contract BE).
6. Criar `data.providers.ts` como `InjectionToken`-based `Provider[]`:
   - `SpeechTreePort` → `SpeechTreeMockAdapter` (swap para `SpeechTreeHttpAdapter` em produção).
   - `SemanticSearchPort` → `SemanticSearchMockAdapter` / `SemanticSearchHttpAdapter`.
   - `NodeSelectionPort` → `NodeSelectionMockAdapter` / `NodeSelectionHttpAdapter`.
   - `BindingPort` → `BindingMockAdapter` / `BindingHttpAdapter`.
   - `AudioPort` → `AudioHttpAdapter`.
   - `ProposalPort` → `ProposalMockAdapter` (sem Http até BE add endpoint).
7. Exportar tudo pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/dto/tree-response.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/dto/search-response.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/dto/select-request.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/dto/select-response.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/dto/binding-response.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/dto/proposal.dto.ts`
- [ ] implementar `libs/conv/data-access/src/lib/data.providers.ts`
- [ ] implementar `libs/conv/data-access/src/index.ts`
- [ ] DTOs são `readonly` — nenhum campo mutável
- [ ] `ProposalPort` aponta para `ProposalMockAdapter` (nunca HttpAdapter nesta issue)

## Acceptance criteria
- [ ] Nenhum DTO redefine campos do api-contract — são mirrors 1:1 da spec BE
- [ ] `DATA_PROVIDERS` fornece `ProposalPort` → `ProposalMockAdapter`
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS
- [ ] `npx nx build conv-data-access` PASS

## Dependencies
- CONV-FE-006 a CONV-FE-011 (todas as Ports e Adapters que os providers ligam)

## Empresa-context impact
Nenhum.

## Prioridade
P2
