# CONV-FE-008 -- NodeSelectionPort + Http/MockAdapter (POST /select, clientEventId idempotency, retry-safe, SPEC-05 §4)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`NodeSelectionPort` e seus adapters implementam `POST /api/v1/conv/nodes/{id}/select` com a convenção de idempotência `clientEventId` (UUID v4 mintado uma vez por intenção de ativação e reutilizado na retry), garantindo que uma retry de rede produza no máximo um fato de auditoria no HIST.

## Approach (execution path)
1. Definir `NodeSelectionPort` com operação `select(nodeId, via, locale, clientEventId): Observable<SelectResponseDto>`.
2. Implementar `NodeSelectionHttpAdapter`: `POST /api/v1/conv/nodes/{nodeId}/select` com body `SelectRequestDto`; em falha persistente, emitir o erro para que o facade possa usar Web Speech local (voice-never-silent).
3. Implementar `NodeSelectionMockAdapter`: simular 200 com `playbackMode: 'audio-cacheado'` e `playbackMode: 'web-speech'` para testes de fallback.
4. No `SpeechTreeFacade.activateNode`: mintar `clientEventId = uuid()` em cada nova ativação; reutilizar o mesmo `clientEventId` na retry (nunca gerar novo UUID para a retry do mesmo intent).
5. Apenas nós `frase` chegam ao `NodeSelectionPort` — `categoria` ativa `listLevel`, nunca `/select`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/node-selection.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/node-selection.http-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/node-selection.mock-adapter.ts`
- [ ] teste: retry reutiliza o mesmo `clientEventId` (não gera novo UUID)
- [ ] teste: `categoria` ativada via `SpeechTreeFacade.activateNode` não chama `NodeSelectionPort.select`

## Acceptance criteria
- [ ] Retry de `POST /select` usa o mesmo `clientEventId` da tentativa original
- [ ] Nó de tipo `categoria` nunca gera chamada a `/select`
- [ ] Falha persistente de `/select` não silencia a voz — fallback Web Speech é disparado
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- CONV-FE-012 (SelectRequestDto, SelectResponseDto, DATA_PROVIDERS)
- CONV-FE-010 (TtsPlaybackFacade — recebe SelectResponseDto)
- INT-FE-CONV-001 (patient JWT)

## Empresa-context impact
Nenhum.

## Prioridade
P2
