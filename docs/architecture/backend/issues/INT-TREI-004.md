# INT-TREI-004 -- INT: TREI -> ES IndexPort (embed + index, G5 traceability, 8-field block)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

TREI é o único escritor do índice ES `neuroassist-ai_arvore-fala`. O contrato de
integração com ES tem duas faces: (1) step `embed` — vetor 768-d com `embedding_model_id`
gravado em `trei_conteudo_autoria` (G5 anchor); (2) step `index` — upsert ES com
`_id=no_id_locale`, os 8 campos de traceability D-269, `texto`, `embedding`, `audioRef`,
`status='ativo'`. O mapping é `dynamic:strict` — TREI não pode mutá-lo unilateralmente.
Integração usa `elastic-stack-client-lib` (TREI não tem seu próprio ES client).

## Spec de referencia

- SPEC-11 (embedding-model-parity-g5 — G5 hard gate, model_id recorded)
- SPEC-12 (publication-pipeline-outbox — index step, 8 campos)
- integration-contracts.md §4 (IndexPort, 8-field traceability, dims=768 strict)

## Escopo

- [ ] validar com ES rodando localmente: `ElasticsearchIndexAdapter` consegue indexar doc no índice `neuroassist-ai_arvore-fala` (índice criado a partir do design em `conv-service/design/index-design-arvore-fala.md`)
- [ ] verificar que embedding sidecar local (`TREI_EMBED_SIDECAR_URL`) produz vetor de 768 dims compatível com o mapping (cosine similarity)
- [ ] testar G5 fail-closed: sidecar com model_id diferente de `TREI_EMBED_MODEL_ID` → step `embed` marca `falha` no outbox; documento NÃO é indexado
- [ ] verificar os 8 campos de traceability (D-269) estão presentes em todos os documentos indexados: `source_file_id`, `register_code`, `block`, `parse_status`, `synthetic_dataset`, `locale`, `paciente_id`, `empresa`
- [ ] criar `src/test/java/com/neuroassist/trei/integration/ElasticsearchIndexIntegrationTest.java` — usa Testcontainers ou WireMock para ES; verifica document _id, 8 campos, dims do vetor
- [ ] NÃO alterar o mapping ES (`dynamic:strict` — qualquer mudança é coordenada com CONV owner)

## Resultado esperado

Step `embed` e `index` funcionando end-to-end com ES real (ou Testcontainers);
G5 mismatch → falha limpa sem indexar.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*ElasticsearchIndexIntegrationTest*"` sai 0
- [ ] Teste verifica que `_id` do documento indexado é `<noId>_<locale>` (sem variação)
- [ ] Teste G5: sidecar mock com model_id errado → documento NÃO encontrado no ES após step embed
- [ ] Todos os 8 campos D-269 estão no documento indexado (verificação por chave JSON)

## Dependencias

- TREI-011 (EmbeddingPort adapter — G5ModelIdGuard)
- TREI-013 (ElasticsearchIndexAdapter)

## Estimativa

M

## Prioridade

P4
