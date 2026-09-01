# TREI-016 -- PublicacaoService + OutboxDrainer (embed→tts→index, G5 gate)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/9 itens

## Contexto

O `OutboxDrainer` é o motor do pipeline de publicação broker-less (ADR-004). Roda em
`@Scheduled`, busca rows `TreiPublicacaoOutbox(estado=PENDENTE)` ordenadas por
`proxima_em`, e executa cada step sequencialmente: `embed` → grava vetor no ES doc +
`embedding_model_id` em `TreiConteudoAutoria` (G5); `tts` → sintetiza e grava no
filesystem; `index` → upsert ES doc completo. Apenas quando os 3 steps são `ok` para
`(no_id, locale)`, `PublicacaoService` faz o flip `sub_estado=ativo` e
`conv_no_conteudo.status=ativo` atomicamente. G5 gate no step `embed`: se
`G5ModelIdGuard` detectar mismatch → step `falha`, locale fica `publicando`, nunca `ativo`.

## Spec de referencia

- SPEC-12 (publication-pipeline-outbox — 3 steps, retry, G5 gate)
- event-contracts.md §3 (publication pipeline table)
- data-model.md §4 (state machine: publicando → ativo somente após todos os steps ok)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/application/service/PublicacaoService.java` — interface `flipAtivo(UUID noId, String locale)` + `getPublicacaoStatus(UUID noId, String locale)`
- [ ] criar `src/main/java/com/neuroassist/trei/application/service/PublicacaoServiceImpl.java` — `flipAtivo`: verifica TODOS os steps do `(no_id, locale)` são `ok` antes do flip; chama `ConvTreeWriteAdapter.flipStatusAtivo` na mesma transação
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/outbox/OutboxDrainer.java` — `@Scheduled(fixedDelay=5000)`; busca até 50 rows PENDENTE com `proxima_em <= now()`; para cada row: executa o step via port correspondente; em sucesso → `estado=ok`, `atualizado_em=now()`; em falha → `tentativas++`, `estado=pendente` (ou `falha` se budget excedido), `proxi_em = now() + backoff(tentativas)`
- [ ] step `embed`: chama `EmbeddingPort.embed(texto)` → verifica G5ModelIdGuard (lança `G5ModelMismatchException` → step `falha` imediato, sem retry); grava `embedding_model_id` em `TreiConteudoAutoria`
- [ ] step `tts`: chama `TtsSynthesisPort` (idempotente — se arquivo existe, retorna ref diretamente); grava `audio_ref` e `audio_formato`
- [ ] step `index`: chama `IndexPort.index` com todos os 8 campos + vetor + audioRef + status=ativo
- [ ] após todos 3 steps `ok`: chama `PublicacaoService.flipAtivo` e dispara evento in-process `SpeechNodePublished`; audita para HIST via `HistAuditPort`
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/SpeechNodePublished.java`
- [ ] criar `src/test/java/com/neuroassist/trei/application/service/PublicacaoServiceTest.java` — testar que `flipAtivo` falha se algum step não é `ok`; testar que G5 mismatch impede flip

## Resultado esperado

Pipeline retryable: cada step é idempotente; falha em qualquer step deixa locale em
`publicando`; sucesso dos 3 → flip atômico para `ativo`.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*PublicacaoServiceTest*"` sai 0
- [ ] Teste verifica que `flipAtivo` NÃO é chamado se step `tts` está `pendente`
- [ ] Teste verifica que `G5ModelMismatchException` → step `embed` marcado `falha` sem retry (fail-closed)
- [ ] Teste verifica que após 3 steps `ok`, `conv_no_conteudo.status` é alterado para `ativo` na mesma transação

## Dependencias

- TREI-006 (AutorizacaoService — cria outbox rows ao disparar o gate)
- TREI-008 (OutboxRepository — `findByEstadoAndProximaEmLessThanEqual`)
- TREI-009 (ConvTreeWriteAdapter — `flipStatusAtivo`)
- TREI-011 (EmbeddingPort adapter)
- TREI-012 (TtsSynthesisPort + AudioStorePort adapters)
- TREI-013 (IndexPort adapter)

## Estimativa

L

## Prioridade

P6
