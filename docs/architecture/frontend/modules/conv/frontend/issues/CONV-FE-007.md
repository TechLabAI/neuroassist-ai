# CONV-FE-007 -- SemanticSearchPort + Http/MockAdapter + SemanticAssistFacade (POST /search, degraded-first OPS-003, no error on failure)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`SemanticAssistFacade` expõe `Signal<SemanticAssistResultVM>` que reflete o estado do assist (idle, loading, ready, empty); `degraded: true` ou qualquer falha do endpoint `/search` são tratados como degradação silenciosa — nunca como erro visível (OPS-003).

## Approach (execution path)
1. Definir `SemanticSearchPort` com operação `search(intent: string, k: number, locale: Locale): Observable<SearchResponseDto>`.
2. Implementar `SemanticSearchHttpAdapter`: `POST /api/v1/conv/search`; em 5xx, emitir `{ degraded: true, matches: [] }` em vez de propagar o erro.
3. Implementar `SemanticSearchMockAdapter`: retornar matches fixados em PT e EN, incluindo um cenário `degraded: true` para testes.
4. Implementar `SemanticAssistFacade`:
   - Signal `vm: SemanticAssistResultVM` com campos `matches`, `retrieval`, `degraded`, `status`.
   - Método `assist(intent: string)`: invoca `SemanticSearchPort.search`; `degraded: true` ou `matches: []` → `status: 'empty'`, sem toast.
   - Nenhum scoring/ranking no FE — renderiza `conv-service` matches na ordem recebida (ADR-015).
5. Registrar no `DATA_PROVIDERS`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/semantic-search.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/semantic-search.http-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/semantic-search.mock-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/semantic-assist.facade.ts`
- [ ] teste: `degraded: true` na resposta resulta em `status: 'empty'` sem nenhum efeito visual de erro

## Acceptance criteria
- [ ] 5xx do endpoint `/search` não propaga erro — `degraded: true` é retornado ao facade
- [ ] `SemanticAssistResultVM.degraded === true` não dispara toast nem banner
- [ ] O FE não reordena `matches` — renderiza na ordem da resposta
- [ ] `npx nx test conv-data-access` PASS (cobertura do cenário degradado)
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- CONV-FE-012 (SearchRequestDto, SearchResponseDto, DATA_PROVIDERS)
- INT-FE-CONV-004 (contrato de degradação Elasticsearch OPS-003)

## Empresa-context impact
Nenhum.

## Prioridade
P2
