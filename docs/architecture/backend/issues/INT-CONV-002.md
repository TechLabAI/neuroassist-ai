# INT-CONV-002 -- GAP G5 — Contrato de dados CONV↔TREI (índice arvore-fala: mapping ES, model_id alignment, path de áudio) — dependência de produção cross-module

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O CONV **não pode ir para produção** sem que o TREI esteja operacional e o índice
`neuroassist-ai_arvore-fala` populado com embeddings. Esta issue não bloqueia o scaffold do CONV,
mas bloqueia a **fase de produção** e deve ser rastreada como dependência cross-module:
1. **Mapping ES:** o índice `arvore-fala` com `dense_vector embedding` (ADR-015) deve ser criado
   com o mapping definido em `design/index-design-arvore-fala.md` antes do CONV poder fazer kNN.
2. **model_id alignment (crítico, risco R5):** o `CONV_EMBED_MODEL_ID` que CONV usa no
   `query_vector_builder` DEVE ser o mesmo que TREI usou ao indexar. Mismatch → recall silenciosamente
   degradado.
3. **Path de áudio:** TREI escreve arquivos com convenção `<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`;
   CONV lê com a mesma convenção (`AudioRefPortAdapter`). Divergência → áudio ausente para todo nó.

## Spec de referencia

- SPEC-03 (assisted-semantic-search — kNN, model_id)
- SPEC-10 (integrations — data dependency CONV↔TREI)
- `contracts/data-model.md` §3 (ES projection)
- `design/index-design-arvore-fala.md` (mapping canônico)

## Escopo

- [ ] Formalizar contrato `CONV↔TREI` em `contracts/hub-integration.md` §5 com: (a) mapping ES exato (referência a `index-design-arvore-fala.md`); (b) `model_id` que TREI usará (a ser confirmado com o time de TREI antes do rollout); (c) convenção de path de áudio; (d) garantia de consistência eventual (ES pode lag PostgreSQL)
- [ ] Criar checklist de pré-condição de produção em `docs/architecture/backend/services/conv-service/setup-local.md` §Production-Readiness: "ES index criado com mapping correto", "TREI indexou ao menos 1 nó com embedding", "model_id alinhado"
- [ ] Documentar o sinal de degradação: CONV sem embeddings no ES → `degraded: true` em /search (não erro); logar `conv.retrieval.total{retrieval="static"}` como sinal de que o TREI ainda não indexou

## Resultado esperado

Contrato CONV↔TREI formalizado; requisitos de pré-produção documentados; time ciente do bloqueio.

## Criterios de aceite

- [ ] `contracts/hub-integration.md` §5 contém: mapping ES, model_id placeholder, convenção de path, postura de consistência eventual
- [ ] `setup-local.md` tem seção "Production-Readiness Checklist" com 3 itens de pré-condição
- [ ] A issue é vinculada ao backlog do TREI como dependência externa antes do sprint de rollout

## Dependencias

Nenhuma (issue de documentação e alinhamento cross-module).

## Estimativa

M

## Prioridade

P4
