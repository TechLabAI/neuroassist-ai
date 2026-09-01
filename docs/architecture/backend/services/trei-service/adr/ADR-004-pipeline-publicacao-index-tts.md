<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — Pipeline de publicacao: TREI escreve embedding, TTS cacheado e index arvore-fala

## Status

aceita — materializa o lado de escrita do ADR-015 (índice/embedding) e o ADR-017 (TTS cacheado),
ambos BA/projeto `accepted`.

## Contexto

Quando um nó passa na dupla autorização (ADR-003), o conteúdo precisa virar recuperável pelo CONV: texto +
embedding no Elasticsearch e áudio na voz clonada do paciente. O ADR-015 fixou busca semântica retrieval
puro (embedding gerado por modelo self-hosted multilingual, sidecar) e o ADR-017 fixou síntese ElevenLabs
**uma vez**, cacheada em filesystem, com o índice guardando **referência** ao áudio (não o binário).
`messaging.enabled = false` — não há broker. Precisamos definir quem executa esse pipeline e como ele é
resiliente sem broker.

## Decisão

`trei-service` é o **executor e único dono** do pipeline de publicação, disparado de forma síncrona no
instante em que a segunda aprovação satisfaz o gate: (a) **embedding** do texto com o modelo multilingual
self-hosted; (b) **síntese** de áudio uma única vez via ElevenLabs (MP3/Opus 64–96 kbps), gravando o
arquivo cacheado em `${AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` (mesma convenção que o
CONV lê); (c) **index** de `texto + embedding + audio_ref` no índice `arvore-fala`; (d) marca do nó como
`ativo`. Os passos externos (embedding, TTS, ES) são retentados por um **outbox local** (sem broker); o nó
permanece em sub-estado `publicando` até todos concluírem — só então vira `ativo`, garantindo que o CONV
**nunca** observe uma publicação parcial. Isto concretiza o TODO[arquiteto] do ADR-016 ("disparar
embedding+TTS só quando ambos os flags viram true, possivelmente via evento/outbox") como um **outbox sem
broker**.

**Invariante G5 (coerência com CONV):** o `model_id` do embedding gravado por TREE no índice **DEVE** ser
igual ao `model_id` que o CONV usa no `query_vector_builder.text_embedding` em runtime; caso contrário o
recall do CONV degrada silenciosamente. O `model_id` é gravado explicitamente no documento e a mudança de
modelo obriga reindexação de todo o corpus + cutover coordenado com o CONV.

## Consequências

- **Positivas:** custo de ElevenLabs vira por-síntese e não por-uso (FIN-002); Elasticsearch guarda só
  texto+embedding+referência (não blobs); atomicidade de publicação protege o runtime do CONV; um único
  escritor do índice/áudio.
- **Negativas / trade-offs:** TREI carrega a complexidade do outbox/retry e do sub-estado `publicando`;
  os stores derivados (ES, áudio) precisam ser reconstruíveis a partir do PostgreSQL; a coerência de
  `model_id` é um acoplamento operacional forte com o CONV.
- **Fallback downstream:** se, apesar do gate, um áudio faltar em runtime, o CONV cai para Web Speech API
  (OPS-004) — mas o design de `ativo` só após pipeline completo torna isso a exceção, não a regra.
