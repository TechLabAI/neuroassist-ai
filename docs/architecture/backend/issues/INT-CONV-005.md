# INT-CONV-005 -- Contrato operacional SearchPort model_id (CONV query_vector_builder DEVE usar o mesmo model_id que TREI usou no index-time — risco R5)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

O risco R5 da arquitetura CONV: o `model_id` usado pelo CONV no `query_vector_builder` do ES
DEVE ser idêntico ao `model_id` que o TREI usou ao indexar os embeddings. Um mismatch não gera
erro — gera **degradação silenciosa de recall** (kNN retorna resultados piores sem nenhum aviso).
Esta issue formaliza o contrato operacional e cria o mecanismo de detecção de mismatch.
Ref: `integration-contracts.md` §1.1 — "the platform SHALL treat a model_id mismatch as a
configuration error, not a silent runtime state."

## Spec de referencia

- SPEC-03 (assisted-semantic-search — model_id alignment, risco R5)
- `contracts/integration-contracts.md` §1.1

## Escopo

- [ ] Definir `CONV_EMBED_MODEL_ID` como variável de ambiente obrigatória em `application.yml` (sem default — falha na startup se ausente, forçando configuração explícita)
- [ ] Adicionar verificação de startup `ConvEmbedModelCheck.java` (`@EventListener(ApplicationReadyEvent.class)`) que loga `WARN` se `CONV_EMBED_MODEL_ID` diverge do valor registrado em um documento de configuração compartilhado (ex.: `neuroassist-ai_arvore-fala` index settings via ES GET `/_ml/trained_models`)
- [ ] Documentar o procedimento de verificação em `setup-local.md`: como confirmar que `CONV_EMBED_MODEL_ID` == model usado pelo TREI (cross-check via `GET /_ml/trained_models/<model_id>`)
- [ ] Atualizar `integration-contracts.md` §1.1 com referência a INT-CONV-005 como o mecanismo de enforcement

## Resultado esperado

`CONV_EMBED_MODEL_ID` obrigatório; startup loga WARN se detecta mismatch; procedimento documentado.

## Criterios de aceite

- [ ] Startup sem `CONV_EMBED_MODEL_ID` → `BeanCreationException` (não sobe silenciosamente sem o model)
- [ ] `setup-local.md` contém seção "Verificação de model_id" com o comando de cross-check
- [ ] `integration-contracts.md` §1.1 referencia INT-CONV-005 como mecanismo de enforcement

## Dependencias

- CONV-014 (SemanticSearchService — usa `CONV_EMBED_MODEL_ID`)

## Estimativa

S

## Prioridade

P4
