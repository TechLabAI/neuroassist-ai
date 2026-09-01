# TREI-011 -- EmbeddingPort adapter — EmbeddingSidecarAdapter + G5ModelIdGuard

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Implementa `EmbeddingPort` chamando o sidecar self-hosted (SPEC-11, integration-contracts §2).
O G5ModelIdGuard é o componente crítico de segurança operacional: compara
`TREI_EMBED_MODEL_ID` (env, configurado identicamente ao `CONV_EMBED_MODEL_ID`) com o
`model_id` reportado pelo sidecar em runtime. Mismatch → step `embed` marca `falha` no
outbox (fail-closed) — NUNCA indexa um vetor gerado pelo modelo errado (recall silencioso
seria catastrófico para o paciente). Vetor deve ter exatamente 768 dimensões, L2-normalizado.

## Spec de referencia

- SPEC-11 (embedding-model-parity-g5 — fail-closed, G5 invariant)
- integration-contracts.md §2 (EmbeddingPort: 768-d cosine-normalized, model_id check)
- data-model.md §3.2-3.3 (G5 anchor, três camadas de enforcement)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/embedding/EmbeddingSidecarAdapter.java` — implementa `EmbeddingPort`; faz POST para `TREI_EMBED_SIDECAR_URL`; parseia resposta como vetor float[] + `modelId` string
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/embedding/G5ModelIdGuard.java` — compara `sidecar.reportedModelId` com `TREI_EMBED_MODEL_ID`; se diferente → lança `G5ModelMismatchException` (mapeada para outbox step `falha`, fail-closed)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/embedding/EmbeddingConfig.java` — lê `TREI_EMBED_SIDECAR_URL` e `TREI_EMBED_MODEL_ID` do env
- [ ] `G5BootSmoke` (parcial): validar `TREI_EMBED_MODEL_ID` está definido no startup (a smoke completa fica em TREI-018)
- [ ] vetor retornado: `float[] embedding` com `length == 768` — validar antes de retornar (rejeitar se dims erradas)
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/embedding/EmbeddingSidecarAdapterTest.java` — mock do HTTP client; testar G5ModelIdGuard lança exceção quando model_id difere; testar vetor de dim errada rejeitado

## Resultado esperado

`EmbeddingPort.embed` retorna `EmbeddingResult{float[] vector, String modelId}`;
G5ModelIdGuard verifica model_id a cada chamada; mismatch → falha limpa.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*EmbeddingSidecarAdapterTest*"` sai 0
- [ ] Teste verifica que model_id diferente de `TREI_EMBED_MODEL_ID` lança `G5ModelMismatchException`
- [ ] Teste verifica que vetor de 512 dimensões (errado) é rejeitado com exceção antes de retornar
- [ ] `G5ModelIdGuard` NÃO depende de classes de `domain.*` (fica em infrastructure.embedding)

## Dependencias

- TREI-003 (EmbeddingPort interface)

## Estimativa

M

## Prioridade

P4
