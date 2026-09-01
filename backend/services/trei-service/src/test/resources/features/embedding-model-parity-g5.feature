# SPEC-11 — Embedding model-id parity (G5), the CONV<->TREI shared operational invariant.

Feature: Embedding model-id parity between TREI index-time and CONV query-time

Background:
  Given TREI_EMBED_MODEL_ID is set to multilingual-e5-v1
  And the shared index mapping fixes embedding dims 768 and similarity cosine

Scenario: Publishing records the index-time model id
  Given a node n-90 locale en whose gate has cleared
  When the embed step succeeds using model multilingual-e5-v1
  Then trei_conteudo_autoria embedding_model_id for node n-90 locale en is multilingual-e5-v1
  And the locale can transition to ativo

Scenario: A locale cannot become ativo without a recorded model id
  Given a node n-90 locale en with embedding_model_id null
  When activation is attempted
  Then activation is rejected by the data-model check
  And the sub-state stays publicando

Scenario: Sidecar runtime model differs from configuration — fail closed
  Given the embedding sidecar reports runtime model id multilingual-e5-v2
  And TREI_EMBED_MODEL_ID is multilingual-e5-v1
  When the embed step runs
  Then the embed step is marked falha
  And no Elasticsearch write occurs
  And the locale stays publicando

Scenario: Wrong vector dimension is rejected by Elasticsearch
  Given the embedding produced has 512 dimensions
  When the index step runs
  Then the Elasticsearch write is rejected
  And the locale does not become ativo

Scenario: Model change requires a full re-embed and coordinated cutover
  Given the corpus was indexed with model multilingual-e5-v1
  When TREI_EMBED_MODEL_ID changes to multilingual-e5-v2
  Then the entire arvore-fala corpus is re-embedded into a new index generation
  And CONV_EMBED_MODEL_ID is switched to multilingual-e5-v2 only after the reindex completes
  And no mixed-model corpus is ever served

Scenario: Health surfaces the configured model id
  When the health endpoint is queried
  Then the response field embedModelId is multilingual-e5-v1
