# CONV-FE-006 -- SpeechTreePort + Http/MockAdapter + SpeechTreeFacade (getTree, listLevel, in-memory locale cache, ADR-005 single writer)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
`libs/conv/data-access` fornece `SpeechTreePort` (abstract), `SpeechTreeHttpAdapter` (produção, `GET /api/v1/conv/tree`), `SpeechTreeMockAdapter` (Estágio 0) e `SpeechTreeFacade` que expõe `Signal<SpeechTreeVM>` com cache em memória por locale por sessão — único writer do `selectedNodeId` (ADR-005).

## Approach (execution path)
1. Definir `SpeechTreePort` com operações `getTree(rootOnly: boolean, locale: Locale): Observable<TreeResponseDto>` e `listLevel(parentId: string, locale: Locale): Observable<TreeResponseDto>`.
2. Implementar `SpeechTreeHttpAdapter`: `GET /api/v1/conv/tree` com parâmetros `rootOnly` e `parentId`; injetar `HttpClient` via `@neuroassist/shared/access` (patient-session token automático).
3. Implementar `SpeechTreeMockAdapter`: retornar árvore fixada com nós em PT e EN, incluindo pelo menos um nó `pending_locale` e um `categoria`.
4. Implementar `SpeechTreeFacade`:
   - `Signal<SpeechTreeVM>` como estado principal (`status`, `level`, `path`, `selectedNodeId`, `activeChannel`, `locale`).
   - `selectNode(id, channel)` — único writer de `selectedNodeId` (toque e gesto chamam o mesmo método).
   - `activateNode(id)`: `categoria` → `listLevel(id)`; `frase` → delega para `NodeSelectionPort` (via integração).
   - Cache in-memory: mapa `locale → parentId → TreeNodeDto[]`; invalidar em `locale switch`.
5. Registrar no `DATA_PROVIDERS` (CONV-FE-012).

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/speech-tree.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/speech-tree.http-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/speech-tree.mock-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/speech-tree.facade.ts`
- [ ] teste: `selectNode` de ambos os canais (gesto e toque) resulta no mesmo `selectedNodeId`
- [ ] teste: `activateNode` de `categoria` chama `listLevel` e não chama `NodeSelectionPort.select`

## Acceptance criteria
- [ ] `selectedNodeId` é atualizado por um único método independentemente do canal — invariante ADR-005
- [ ] Cache serve o nível já carregado sem chamar o HTTP quando locale não mudou
- [ ] `listLevel` invalida apenas o nível afetado; `locale switch` invalida todo o cache
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- CONV-FE-012 (DTOs + DATA_PROVIDERS)
- INT-FE-CONV-001 (patient-session JWT no HttpClient)

## Empresa-context impact
Nenhum — a árvore é isolada por `pacienteId` no `conv-service`; o FE não gerencia isolamento.

## Prioridade
P2
