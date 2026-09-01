# SPEC-12 — Publication pipeline (embed → tts → index, broker-less outbox)

> EARS acceptance criteria (D-057). Related:
> [`../../contracts/event-contracts.md`](../../contracts/event-contracts.md) §3,
> [`../../contracts/data-model.md`](../../contracts/data-model.md) §2.4/§4,
> [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) §2/§3/§4/§5,
> SPEC-05 (idempotency), SPEC-11 (G5).
> Executable BDD: [`../../tests/cucumber/publication-pipeline.feature`](../../tests/cucumber/publication-pipeline.feature).
> Decision: ADR-004. `stack.messaging.enabled = false`.

## Objetivo

Especificar o mecanismo do pipeline de publicação **sem broker**: como o disparo do gate cria os passos de
outbox, como o drenador os avança (`embed → tts → index`), como cada passo é idempotente e retryable, e como
a ativação é **atômica** — o locale só vira `ativo` (e o CONV só passa a servi-lo) quando os três passos são
`ok`.

## Escopo

- **In:** criação dos passos ao fechar o gate, ordem/dependência dos passos, drenador (backoff, concorrência),
  idempotência por passo, transição atômica para `ativo`, tratamento de esgotamento de retry, edição de nó
  `ativo` sem gap de disponibilidade.
- **Out:** o gate de dupla autorização em si (SPEC-03), o invariante G5 (SPEC-11), o contrato de cada
  integração externa (SPEC-10 / integration-contracts).

## Decisões técnicas

- **Trigger:** `DualAuthorizationGateCleared` (ambos os flags `true` para `(no_id, locale)`) cria **exatamente
  um** conjunto de passos `embed`, `tts`, `index` em `trei_publicacao_outbox` — `UNIQUE (no_id, locale, passo)`
  garante criação idempotente sob replay (`INSERT ... ON CONFLICT DO NOTHING`).
- **Ordem e dependência:** `embed` → `tts` → `index`. Um passo só avança quando o anterior é `ok`; enquanto
  qualquer passo não é `ok`, o locale fica `publicando` e **nunca** `ativo` (atomicidade, ADR-004).
- **Idempotência por passo (SPEC-05):**
  - `embed` — regrava o mesmo vetor determinístico; grava `embedding_model_id` (G5, SPEC-11).
  - `tts` — sintetiza **uma vez** por `(no_id, locale)`; retry sobre arquivo em cache é no-op (custo,
    FIN-002).
  - `index` — upsert em `_id = <no_id>_<locale>` com os 8 campos de rastreabilidade + `texto`, `embedding`,
    `audio_ref`, `status='ativo'`.
- **Drenador broker-less:** `@Scheduled` seleciona passos elegíveis por `proxima_em` (backoff) com
  `SELECT ... FOR UPDATE SKIP LOCKED` (nenhum passo processado em paralelo por dois workers).
- **Ativação atômica:** quando `embed`+`tts`+`index` = `ok`, na mesma fronteira transacional que fecha o
  outbox: `trei_conteudo_autoria.sub_estado='ativo'`, `publicado_em=now()`, `conv_no_conteudo.status='ativo'`
  — só então o CONV recupera/serve o locale.
- **Esgotamento de retry:** se um passo excede o budget → `estado='falha'`, locale permanece `publicando`
  (nunca `ativo`), surfaced em `GET /nos/{id}/publicacao` e nas métricas/health (SPEC-07).
- **Edição de `ativo`:** editar uma frase `ativa` abre um **novo** ciclo de aprovação para aquele
  `(no_id, locale)` enquanto o `conv_no_conteudo` anterior permanece `ativo` (sem gap de disponibilidade do
  CONV); só na re-publicação o novo conteúdo+embedding substitui o anterior (re-embed com o
  `TREI_EMBED_MODEL_ID` atual, preservando G5).

## Implementação

- Handler de `DualAuthorizationGateCleared` na `application` cria os 3 passos (`ON CONFLICT DO NOTHING`).
- Drenador agendado invoca `EmbeddingPort`, `TtsSynthesisPort`+`AudioStorePort`, `IndexPort` na ordem;
  falha → `estado='pendente'`, incrementa `tentativas`, agenda `proxima_em` (backoff exponencial).
- Transição para `ativo` como operação transacional única (PostgreSQL autoritativo); o ES/áudio podem lagar,
  e o CONV degrada tratando um miss como "não encontrado" (OPS-003) — TREI nunca bloqueia autoria por ES.

## Casos de borda

- Aprovação cuidadora/paciente quase simultânea → um único conjunto de passos criado (`ON CONFLICT`).
- `embed` falha → passo `pendente`+backoff; `sub_estado` fica `publicando`; `conv_no_conteudo.status` fica
  `pendente`.
- `tts` retried com arquivo em cache → nenhuma nova síntese ElevenLabs; arquivo reusado.
- `index` reenviado após `ok` → upsert idempotente, sem documento duplicado.
- Budget de retry esgotado no `index` → `falha`; endpoint de status reporta o passo; locale nunca `ativo`.

## Critérios de aceite (EARS)

- WHEN the dual-authorization gate clears for a `(no_id, locale)`, THE SYSTEM SHALL create exactly one set of
  outbox steps (`embed`, `tts`, `index`) and start the pipeline once, idempotently under replay.
- THE SYSTEM SHALL advance the steps in the order `embed → tts → index`, advancing a step only when the prior
  step is `ok`.
- WHILE any step for a `(no_id, locale)` is not `ok`, THE SYSTEM SHALL keep the locale `publicando` and SHALL
  NOT mark it `ativo` nor flip `conv_no_conteudo.status`.
- WHEN all three steps are `ok`, THE SYSTEM SHALL set `sub_estado='ativo'`, `publicado_em`, and
  `conv_no_conteudo.status='ativo'` in one transactional boundary, and only then SHALL CONV be able to serve
  the locale.
- WHEN a `tts` step is retried and the cached file already exists, THE SYSTEM SHALL NOT request a new
  ElevenLabs synthesis; WHEN an `index` step is retried, THE SYSTEM SHALL upsert the same deterministic
  `_id = <no_id>_<locale>` with no duplicate document.
- WHILE the outbox drainer processes a step, THE SYSTEM SHALL prevent a second worker from processing the same
  `(no_id, locale, passo)` concurrently.
- IF a step exhausts its retry budget, THEN THE SYSTEM SHALL set `estado='falha'`, keep the locale
  `publicando`, and surface it via the publication status endpoint and health — never silently `ativo`.
- WHERE an `ativo` phrase is edited, THE SYSTEM SHALL open a new approval cycle while keeping the prior
  `conv_no_conteudo` `ativo`, replacing it only on re-publication (no CONV availability gap).
