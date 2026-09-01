<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Busca semântica retrieval-only sobre Elasticsearch, reusando SearchPort/IndexPort

## Status
aceita

> Escopo: local (`conv-service`). Implementa a decisão de produto já fechada em **ADR-015 (accepted)** —
> "Elasticsearch como motor de busca semântica (retrieval puro) para CONV". Este ADR registra como o
> serviço realiza essa decisão reusando a integração já materializada; não reabre o retrieval-vs-generativo.

## Contexto
O runtime do CONV precisa de busca assistida: dado um gesto/entrada, aproximar a frase mais próxima da
intenção sem exigir navegação completa da árvore. O ADR-015 fechou **retrieval puro** (Elasticsearch/kNN
sobre `dense_vector`), rejeitando RAG generativo em runtime — em saúde assistiva, uma frase alucinada é
risco inaceitável. A integração técnica com Elasticsearch já foi materializada no projeto em
`integrations/elastic-stack/` (lib `elastic-stack-client-lib`): portas hexagonais `SearchPort`/`IndexPort`,
`ElasticsearchAdapter` (único detentor de `co.elastic.clients`), `ElasticsearchClientConfig` e
`ElasticsearchErrorDecoder`. O índice `arvore-fala` (classe *dimension*) já está declarado em
`project-context.json`.

## Decisão
CONV realiza a busca assistida **exclusivamente por retrieval** sobre o índice `arvore-fala`, e o faz
**reusando `SearchPort`** da `elastic-stack-client-lib` — CONV importa a lib como dependência e **não**
instancia um cliente ES próprio nem importa `co.elastic.clients` fora do adapter existente. O runtime nunca
chama LLM. A escrita no índice (upsert incremental por nó, embeddings) é do **TREI** via `IndexPort`, não do
CONV. Elasticsearch é uma **dependência soft**: se indisponível ou sem a metade vetorial, CONV cai para
**navegação estática** sobre PostgreSQL (fonte autoritativa) e sinaliza `degraded: true` (OPS-003) — nunca
falha o caminho de navegação.

**Ponto em aberto herdado para specs:** `SearchPort.search(indexPattern, Map query)` passa Query DSL sob a
chave `query`; kNN em ES usa um bloco `knn` de topo. Specs devem confirmar se o mapa acomoda `knn` ou se
`SearchPort` precisa de um método kNN-aware — extensão **aditiva** da lib compartilhada, jamais um cliente
paralelo.

## Consequências
**Positivas:** resposta sempre vinda do catálogo curado (sem alucinação); latência local baixa e previsível;
sem custo de API de LLM em runtime (FIN-001); zero reinvenção — reuso das portas/adapters já testados;
tradução de erros (`ElasticException`) e retry já resolvidos na lib. **Negativas / trade-offs:** só encontra
o que o TREI indexou (não generaliza para frases nunca cadastradas); qualidade depende dos embeddings
(responsabilidade do TREI); divergência índice↔PostgreSQL pode fazer a busca "não encontrar" algo que
existe — mitigado por PostgreSQL autoritativo + fallback estático; possível necessidade de estender
`SearchPort` para kNN (custo baixo, aditivo). Reverter para RAG generativo seria aditivo (a infra de
indexação já estaria pronta), mas contraria o ADR-015 e não é objetivo.
