# CONV-014 -- Criar SemanticSearchService — searchAssisted (POST /search, kNN via SearchPort, clamp k, fail-open degraded)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Motor de busca semântica assistida do CONV. `SemanticSearchService.searchAssisted(intent, k, locale,
pacienteId)` constrói o corpo kNN e chama `SearchPort.search("neuroassist-ai_arvore-fala", body)`.
O vetor de query é computado pelo ES via `query_vector_builder.text_embedding` — CONV nunca chama
um modelo de embedding diretamente (ADR-002/ADR-015). Gap G1 resolvido: `SearchPort.search` aceita
o body completo com `knn` top-level (via `.withJson()`). Falha do ES → `degraded: true`,
`retrieval: static`, `matches: []`, `200` (OPS-003 — nunca 5xx na busca). Emite `PhraseMatched`
para HIST quando há resultado; `ConvServedDegraded` quando em modo degradado.

## Spec de referencia

- SPEC-03 (assisted-semantic-search — kNN, clamp, fail-open)
- SPEC-12 (degradation-resilience — OPS-003)
- `contracts/integration-contracts.md` §1.1 (SearchPort kNN body shape)

## Escopo

- [ ] Criar `application/service/SemanticSearchService.java` com operação `searchAssisted(String intent, int k, String locale, UUID pacienteId): SearchResponse`
- [ ] Clamp: IF `intent` em branco ou >512 chars → `400 invalid-request`; IF `k < 1` → default `5`; IF `k > 50` → clamp `50`
- [ ] Construir corpo kNN conforme `integration-contracts.md` §1.1: `{"knn": {"field": "embedding", "k": <k>, "num_candidates": <k*10>, "query_vector_builder": {"text_embedding": {"model_id": "${neuroassist.conv.embed-model-id}", "model_text": <intent>}}, "filter": [{"term": {"paciente_id": <pacienteId>}}, {"term": {"locale": <locale>}}, {"term": {"status": "ativo"}}, {"term": {"tipo": "frase"}}]}, "_source": ["no_id","texto","tipo","engine_feature_ref","audio_ref"]}`
- [ ] Chamar `SearchPort.search("neuroassist-ai_arvore-fala", knnBody)` — capturar `ElasticException` → retornar `SearchResponse{degraded:true, retrieval:static, matches:[]}` + emitir `ConvServedDegraded`
- [ ] Em sucesso: mapear hits para `MatchDto` (noId, texto, score, tipo, engineFeatureRef, audioRef); emitir `PhraseMatched` para HIST (intentHash = SHA-256 do intent, não o texto raw — LGPD, SPEC-06/SEC-002)
- [ ] Criar `api/controller/ConvSearchController.java` (`POST /conv/search`, scope `conv.arvore.navegar`)
- [ ] Testes unitários: busca bem-sucedida, ES indisponível (degraded), intent vazio (400), k>50 (clamp), k ausente (default 5)

## Resultado esperado

`POST /api/v1/conv/search` retorna resultados kNN ou `degraded: true` graciosamente; nunca 5xx por ES indisponível.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: `SearchPort` retorna 2 hits → `SearchResponse` com 2 `MatchDto`, `retrieval: semantic`, `degraded: false`
- [ ] Teste unitário: `SearchPort` lança `ElasticException` → `SearchResponse{degraded:true, matches:[], retrieval:static}` + `200`
- [ ] Teste unitário: `intent = ""` → `400 invalid-request`
- [ ] Teste unitário: `k = 75` → corpo kNN enviado com `k: 50` (clamp)
- [ ] `intentHash` é SHA-256 do intent (nunca intent raw nos fatos HIST/outbox)
- [ ] `ConvServedDegraded` emitido quando ES indisponível (verificável em teste com `HistAuditPort` mockado)

## Dependencias

- CONV-004 (ports: SearchPort, HistAuditPort)
- CONV-009 (persista do outbox se HIST falhar ao emitir PhraseMatched)

## Estimativa

L

## Prioridade

P4
