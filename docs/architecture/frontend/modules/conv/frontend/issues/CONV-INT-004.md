# CONV-INT-004 -- conv-service Elasticsearch kNN degradation contract (OPS-003: degraded:true = soft 200, never error, silent fallback)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
O `SemanticSearchHttpAdapter` (CONV-FE-007) está alinhado com o contrato de degradação do Elasticsearch do `conv-service`: `POST /search` com `degraded: true` é um `200 OK` soft — nunca um `5xx`; o FE trata ambos (200 degraded e qualquer 5xx não esperado) como degradação silenciosa sem nenhuma superfície de erro para o paciente.

## Approach (execution path)
1. Verificar no `backend-api-cross-link.md` (contracts/) que `degraded:true` na resposta de `/search` é um `200 OK` com `matches: []` — não um erro HTTP.
2. No `SemanticSearchHttpAdapter`:
   - `200 OK` com `degraded: true` → retornar `SearchResponseDto` com `degraded: true` para o facade (sem throw).
   - `5xx` inesperado → mapear para `{ degraded: true, matches: [], retrieval: 'static' }` e retornar normalmente (não propagar o erro RxJS).
3. Documentar em comentário no adapter: "Por OPS-003, qualquer falha de retrieval é degradação silenciosa — nunca propagar para o paciente."
4. Adicionar cenário de teste unitário: 503 do ES → `SemanticAssistFacade.vm.degraded === true`, `vm.status === 'empty'`.

## Implementation checklist (by layer/file)
- [ ] `libs/conv/data-access/src/lib/semantic-search.http-adapter.ts` trata 5xx como degradação
- [ ] teste: `503` do `/search` → `{ degraded: true, matches: [] }` no facade
- [ ] `npx nx test conv-data-access` PASS (cobertura do cenário 5xx)

## Acceptance criteria
- [ ] `503` de `/search` não lança exceção RxJS — retorna `degraded: true`
- [ ] `SemanticAssistFacade.vm.status === 'empty'` em qualquer cenário de degradação
- [ ] Nenhum toast/banner renderizado em degradação (verificado em teste de integração)

## Dependencies
- CONV-FE-007 (SemanticSearchHttpAdapter — implementação alvo)
- `docs/architecture/frontend/modules/conv/frontend/contracts/backend-api-cross-link.md` (fonte do contrato de degradação)

## Empresa-context impact
Nenhum.

## Prioridade
P4
