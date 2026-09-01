# TREI-013 -- IndexPort adapter — ElasticsearchIndexAdapter (elastic-stack-client-lib)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Implementa `IndexPort` usando a `elastic-stack-client-lib` compartilhada (único holder do
`co.elastic.clients`). TREI nunca instancia seu próprio ES client. O documento indexado
tem `_id = <no_id>_<locale>` (idempotente — retry é upsert). Deve conter os **8 campos
de traceability obrigatórios** (D-269); o shared adapter rejeita (DLQ) escrita incompleta.
Dimensão do vetor: exatamente 768 (cosine, mapping `dynamic:strict` rejeita dim errada).

## Spec de referencia

- SPEC-12 (publication-pipeline-outbox — step index: upsert, 8-field traceability)
- integration-contracts.md §4 (IndexPort: _id=no_id_locale, 8 campos, dims=768 strict)
- event-contracts.md §3 (publication pipeline — index step)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/elasticsearch/ElasticsearchIndexAdapter.java` — implementa `IndexPort`; usa `elastic-stack-client-lib` para upsert; constrói documento com 8 campos traceability + `texto` + `embedding` float[] + `audioRef` + `status='ativo'`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/elasticsearch/IndexDocumentMapper.java` — mapeia `TreiConteudoAutoria` + `ConvNoConteudo` + embedding vector para `IndexDocument`; preenche os 8 campos: `source_file_id=sessaoId`, `register_code=tipo`, `block="CONV"`, `parse_status="ok"`, `synthetic_dataset=false`, mais `locale`, `paciente_id`, `empresa`
- [ ] `_id` gerado deterministicamente: `String.format("%s_%s", noId.toString(), locale)` → idempotente em replay
- [ ] se `IndexPort` falha com `ElasticException` → propagar como `IndexException` (mapeada para outbox step `falha` + retry pelo drainer)
- [ ] NÃO mutar o mapping ES (é `dynamic:strict` e owned jointly com CONV — qualquer mudança é coordenada)
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/elasticsearch/ElasticsearchIndexAdapterTest.java` — mock da lib; verificar _id correto, 8 campos presentes, ElasticException → IndexException

## Resultado esperado

Upsert ES com `_id=no_id_locale`; todos os 8 campos traceability; falha ES → outbox retry.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*ElasticsearchIndexAdapterTest*"` sai 0
- [ ] Teste verifica que `_id` é `noId.toString() + "_" + locale` (sem variação)
- [ ] Teste verifica que documento com menos de 8 campos traceability é rejeitado (validação antes de enviar ao ES)
- [ ] TREI NÃO importa `co.elastic.clients` diretamente — apenas via `elastic-stack-client-lib`

## Dependencias

- TREI-003 (IndexPort interface)
- TREI-011 (EmbeddingPort — vetor float[] vem do step embed antes do index)

## Estimativa

M

## Prioridade

P4
