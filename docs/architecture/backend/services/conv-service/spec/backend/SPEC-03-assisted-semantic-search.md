# SPEC-03 — Assisted Semantic Search (kNN, retrieval-only)

## Objetivo
Especificar a busca assistida: dada uma intenção capturada, retornar o(s) nó(s) `frase` mais próximos já
autorados, no locale ativo, via Elasticsearch kNN — **retrieval puro**, nunca generativo
(`ADR-002`/ADR-015). Resolve os gaps **G1** (assinatura kNN da porta) e integra **G5** (contrato do índice).

## Escopo
**In:** `POST /search`; construção do corpo kNN; reuso de `SearchPort`; fail-open para estático.
**Out:** navegação (SPEC-02); escrita/embedding do índice (TREI); regra de locale (SPEC-11).

## Decisões técnicas
- **G1 RESOLVIDO sem alterar a porta.** `ElasticsearchAdapter.search` usa `SearchRequest.withJson(map)`
  — o `Map` é o **corpo `_search` inteiro**, não o conteúdo de `query`. Um bloco `knn` de topo (irmão de
  `query`/`filter`) é enviado literalmente. Logo `SearchPort.search` já acomoda kNN; **nenhuma extensão
  aditiva é necessária** (confirma o ponto aberto do `ADR-002`).
- **Vetor de consulta sem LLM.** O CONV não computa embeddings: usa `query_vector_builder.text_embedding`
  com o **mesmo `model_id` multilíngue** que o TREI usou ao indexar (ADR-015). O ES gera o vetor
  internamente; o CONV só passa o texto da intenção. Runtime segue sem chamar LLM/serviço de embedding.
- **Elasticsearch é dependência soft (fail-open).** Erro/indisponibilidade → navegação estática +
  `degraded: true` (OPS-003).

## Implementação
- `AssistedSearchService` monta o corpo (ver [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) §1.1):
  `knn.field=embedding`, `k`, `num_candidates≈10k`, `query_vector_builder.text_embedding.model_id`,
  `filter` por `paciente_id` + `locale` + `status:ativo` + `tipo:frase`; `_source` mínimo.
- Chama `SearchPort.search("neuroassist-ai_arvore-fala", body)`; mapeia hits → `matches`.
- `try/catch ElasticException` → `DegradationPolicy` seta `retrieval:static`, `degraded:true`, `matches:[]`.
- `k` default 5; clamp em 50. `intent` não-vazio, ≤ 512 chars.
- Emite `PhraseMatched` (com `intentHash`, nunca o texto cru — LGPD).

## Casos de borda
- ES up mas sem metade vetorial (sem `model_id`/inferência) → `degraded:true` explícito (nunca rótulo
  "semantic" silencioso), fallback estático.
- `k=0` → 5; `k>50` → 50. `intent` vazio → `400 invalid-request`.
- Divergência índice↔PostgreSQL (R2): "não encontrou" cai para navegação; PostgreSQL é autoritativo.

## Critérios de aceite (EARS)
- WHEN `POST /search` runs with Elasticsearch reachable, THE SYSTEM SHALL call `SearchPort.search` with a top-level `knn` body filtered by `paciente_id`, active `locale`, `status:ativo`, `tipo:frase`, and return up to `k` matches with `retrieval: semantic`.
- THE SYSTEM SHALL use a `query_vector_builder.text_embedding` `model_id` equal to TREI's index-time model and SHALL NOT compute embeddings itself.
- THE SYSTEM SHALL return only `ativo` nodes and SHALL NEVER return text absent from the curated tree.
- IF Elasticsearch is unreachable or the vector half is unavailable, THEN THE SYSTEM SHALL return `200` with `degraded: true`, `retrieval: static`, empty `matches`, and header `X-Degraded: true` (OPS-003).
- IF `intent` is blank or exceeds 512 characters, THEN THE SYSTEM SHALL reject with `400`.
- WHERE `k` is absent THE SYSTEM SHALL default it to 5; IF `k > 50` THEN THE SYSTEM SHALL clamp it to 50.
