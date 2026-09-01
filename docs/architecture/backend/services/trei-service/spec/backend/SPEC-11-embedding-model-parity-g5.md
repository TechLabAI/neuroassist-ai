# SPEC-11 — Embedding model-id parity (G5)

> EARS acceptance criteria (D-057). **Canonical statement:**
> [`../../contracts/data-model.md`](../../contracts/data-model.md) §3. Related:
> [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) §2,
> [`../../contracts/api-contract.md`](../../contracts/api-contract.md) §2.9 (health).
> Executable BDD: [`../../tests/cucumber/embedding-model-parity-g5.feature`](../../tests/cucumber/embedding-model-parity-g5.feature).
> Decision: ADR-015 (index-time side of semantic search). **Invariant co-owned CONV↔TREI.**

## Objetivo

Especificar o invariante operacional **G5**: o `model_id` do embedding usado por TREI em **index-time**
DEVE ser igual ao usado por CONV em **query-time**. Um mismatch não gera erro — degrada silenciosamente o
recall kNN do CONV, o que num contexto assistivo significa o paciente não achar a frase de que precisa. G5
é, portanto, um invariante **hard**, não um parâmetro de tuning.

## Escopo

- **In:** o binding dos dois model-ids (`TREI_EMBED_MODEL_ID` ⇔ `CONV_EMBED_MODEL_ID` ⇔ model-id do endpoint
  de inferência ES), as três camadas de enforcement (dimensão, model-id gravado, self-check de config), a
  visibilidade no health, e o protocolo de troca de modelo (re-embed + cutover coordenado).
- **Out:** o query-time kNN do CONV (contrato do CONV), o mecanismo geral do outbox (SPEC-12), a escolha do
  modelo multilingual em si (ADR-015).

## Decisões técnicas

- **Anchor único:** uma só string identifica o modelo de embedding em ambos os lados e no endpoint de
  inferência ES. `TREI_EMBED_MODEL_ID` **MUST equal** `CONV_EMBED_MODEL_ID` (data-model §3.2).
- **Três camadas de enforcement (MVP):**
  1. **Guarda de dimensão (mapping):** o mapping compartilhado fixa `embedding: dense_vector, dims: 768,
     similarity: cosine`, `dynamic: strict`. TREI produz vetores de **exatamente** 768 dims, L2-normalizados;
     dimensão errada é rejeitada pelo ES.
  2. **Model-id gravado (PostgreSQL):** TREI grava `trei_conteudo_autoria.embedding_model_id`; o CHECK do
     DDL (data-model §2.3) proíbe `sub_estado = 'ativo'` sem ele — drift auditável por (nó, locale).
  3. **Self-check de config (boot + publish):** TREI lê `TREI_EMBED_MODEL_ID` e **recusa** o passo `embed`
     (fail-closed, outbox `falha`) se o sidecar reportar um model-id de runtime **diferente**. O deploy
     assevera `TREI_EMBED_MODEL_ID == CONV_EMBED_MODEL_ID`; um smoke compara os dois no boot.
- **Health surfaced:** `embedModelId` (= `TREI_EMBED_MODEL_ID`) aparece em `GET /health`, tornando um
  mismatch com o CONV operacionalmente visível (SPEC-07).
- **Troca de modelo = re-embed total + cutover coordenado:** trocar `TREI_EMBED_MODEL_ID` implica re-embedar
  o corpus `arvore-fala` inteiro numa nova geração de índice/alias; o CONV só muda `CONV_EMBED_MODEL_ID`
  **depois** que o reindex completa — nunca um estado de corpus com modelos misturados.
- **Follow-up coordenado (não unilateral):** adicionar um campo `embedding_model_id` (keyword) ao documento
  ES tornaria a paridade checável em query-time também; como o mapping é `dynamic: strict` e **co-owned**
  com o CONV, isso é proposto como mudança coordenada (backlog/index-design), não escrita unilateralmente.

## Implementação

- `EmbeddingPort` retorna vetor 768-d normalizado + o model-id de runtime do sidecar; o `application` compara
  com `TREI_EMBED_MODEL_ID` antes de gravar/indexar.
- Gravação de `embedding_model_id` na mesma fronteira transacional que fecha o passo `embed` do outbox.
- Smoke de boot: assert `TREI_EMBED_MODEL_ID` set e (quando alcançável) == model-id do sidecar; o manifesto
  de deploy assert `== CONV_EMBED_MODEL_ID` (setup-local §G5 boot check).

## Casos de borda

- `sub_estado='ativo'` tentado com `embedding_model_id` NULL → barrado pelo CHECK do DDL (data-model §2.3).
- Sidecar runtime `multilingual-e5-v2` vs config `multilingual-e5-v1` → `embed` = `falha`, sem escrita ES.
- Vetor com 512 dims → escrita ES rejeitada (dimensão), locale não vira `ativo`.
- Troca de modelo → re-embed do corpus inteiro; sem estado misto servido.

## Critérios de aceite (EARS)

- WHEN the `embed` step runs, THE SYSTEM SHALL request a 768-d L2-normalized vector and SHALL record
  `embedding_model_id = TREI_EMBED_MODEL_ID` on the `trei_conteudo_autoria` row.
- IF `embedding_model_id` is null for a `(no_id, locale)`, THEN THE SYSTEM SHALL NOT allow `sub_estado =
  'ativo'` (DDL CHECK, data-model §2.3).
- IF the embedding sidecar reports a runtime model id different from `TREI_EMBED_MODEL_ID`, THEN THE SYSTEM
  SHALL mark the `embed` step `falha` (fail-closed) and SHALL NOT index.
- IF a produced vector does not have exactly 768 dimensions, THEN THE SYSTEM SHALL let Elasticsearch reject
  the write and SHALL NOT mark the locale `ativo`.
- IF `TREI_EMBED_MODEL_ID` changes, THEN THE SYSTEM SHALL re-embed the entire `arvore-fala` corpus into a
  new index generation, and CONV SHALL switch `CONV_EMBED_MODEL_ID` only after the reindex completes — no
  mixed-model corpus is ever served.
- THE SYSTEM SHALL surface `embedModelId` (= `TREI_EMBED_MODEL_ID`) on `GET /health` and SHALL treat a
  configured mismatch as a deploy/boot error, never a silent runtime state.
- THE SYSTEM SHALL record `embedding_model_id` on 100% of `ativo` locales (doc 01 model-id coherence signal).
