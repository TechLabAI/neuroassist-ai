# TREI-021 -- Cucumber BDD: publication-pipeline + bilingual-locale-independence + embedding-model-parity-g5

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Step definitions para as três features restantes do M3: pipeline de publicação
broker-less (`publication-pipeline.feature`), independência de locale bilíngue
(`bilingual-locale-independence.feature`) e paridade G5 do embedding model
(`embedding-model-parity-g5.feature`). Features com maior complexidade: pipeline
testa retries de outbox, G5 testa o fail-closed de model mismatch.

## Spec de referencia

- SPEC-08 (bdd-test-scenarios — features de BDD)
- SPEC-11 (embedding-model-parity-g5 — feature G5)
- tests/cucumber/publication-pipeline.feature
- tests/cucumber/bilingual-locale-independence.feature
- tests/cucumber/embedding-model-parity-g5.feature

## Escopo

- [ ] copiar features para `src/test/resources/features/` (a partir dos canônicos em `tests/cucumber/`)
- [ ] criar `src/test/java/com/neuroassist/trei/bdd/steps/PublicationPipelineSteps.java` — steps para: gate fires, outbox created, embed step executes (WireMock sidecar), tts step executes (WireMock ElevenLabs — idempotent), index step executes (WireMock ES), locale ativo após 3 ok, step falha → locale stays publicando, retry após backoff
- [ ] criar `src/test/java/com/neuroassist/trei/bdd/steps/BilingualLocaleSteps.java` — steps para: materialize node in PT, materialize SAME node in EN (reutiliza noId, structure locale-invariante), approve PT independently of EN, PT ativo não implica EN ativo, discard EN não afeta PT
- [ ] criar `src/test/java/com/neuroassist/trei/bdd/steps/EmbeddingModelParitySteps.java` — steps para: sidecar reporta model_id correto → embed step ok; sidecar reporta model_id ERRADO → embed step falha (G5ModelMismatchException), locale NÃO fica ativo, outbox step marcado falha
- [ ] WireMock stubs adicionais: sidecar de embedding (retorna vetor 768-d + modelId), ElevenLabs TTS (retorna bytes de áudio), ES IndexPort (retorna ok)
- [ ] steps de retry: `OutboxDrainer` rodado manualmente nos testes (não `@Scheduled`) — chamar `outboxDrainer.processPendingSteps()` diretamente

## Resultado esperado

Todos os cenários das 3 features passando; G5 mismatch → locale não fica ativo
(testado em cenário explícito).

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*CucumberRunnerTest*"` sai 0 com todos os 31 cenários (M3) passando
- [ ] Cenário G5: sidecar retorna model_id diferente → step embed marcado `falha`, `trei_conteudo_autoria.embedding_model_id` permanece null, `sub_estado` permanece `publicando`
- [ ] Cenário bilingual: materializar EN para noId existente em PT → NÃO cria nova linha em `conv_no_arvore_fala` (UNIQUE(noId) para o nó)
- [ ] Cenário retry: step `tts` com arquivo já existente → `TtsSynthesisPort` NÃO é chamado (idempotente)

## Dependencias

- TREI-016 (PublicacaoService + OutboxDrainer — testado pelos steps)
- TREI-020 (CucumberRunnerTest — estende o mesmo runner)

## Estimativa

L

## Prioridade

P8
