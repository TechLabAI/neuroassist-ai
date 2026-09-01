<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy

Schema-per-tenant (`empresa`), aligned with `ADR-003-multi-tenancy` and the project
`multiTenancy.isolationStrategy = schema-per-tenant` (`t_<empresa>` tenant schemas +
`neuroassist_global` for cross-tenant reference data). TREI's authoring tables live in the tenant schema;
every row is scoped by `empresa` + `paciente_id`. There are **three persistence surfaces**, only the
first owned as relational state by TREI:

1. **PostgreSQL (tenant schema)** — authoring state: sessions, draft nodes, per-locale content,
   approvals, and the outbox. **Source of truth for authoring.**
2. **Elasticsearch `arvore-fala` (write side)** — the published projection (text + embedding +
   audio_ref) that CONV reads. TREI is the writer; this is a *derived* store, re-buildable from
   PostgreSQL + re-embedding.
3. **Audio filesystem store** — the cached synthesized files CONV plays; also derived (re-synthesizable),
   at `${AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`.

## Initial Entities

Draft relational model (specs confirm columns/types; coordinate the node shape with CONV's
`conv_no_conteudo`):

- **`trei_sessao_treinamento`** — `id` (PK), `empresa`, `paciente_id`, `locale_autoria`,
  `cuidadora_id` (ACS ref), `estado`, `iniciada_em`, `encerrada_em`, `llm_transcript_ref`.
- **`trei_no_arvore_fala`** — `no_id` (PK), `empresa`, `paciente_id`, `parent_id` (nullable, tree
  position), `tipo` (`categoria|frase`), `estado` (`rascunho|publicando|ativo|descartado`),
  `confirmado_cuidadora` (bool), `confirmado_paciente` (bool), `criado_em`, `atualizado_em`. FKs:
  `paciente_id → PAC.Paciente` (by reference), `sessao_id → trei_sessao_treinamento`.
- **`trei_no_conteudo_locale`** — `id` (PK), `no_id` (FK), `locale` (`pt-BR|en-US`), `texto`,
  `embedding_model_id`, `embedding_ref`, `audio_ref`, `estado_publicacao`
  (`rascunho|publicando|ativo`), `publicado_em`. Unique (`no_id`, `locale`). *(If approvals prove to be
  per-locale rather than per-node, the two approval flags move here — open item, doc 06.)*
- **`trei_proposta_llm`** — `id` (PK), `no_id` (FK), `sessao_id`, `prompt_ref`, `response_ref`,
  `criado_em`. Traceability of what the LLM proposed (SEC-002).
- **`trei_publicacao_outbox`** — `id` (PK), `no_id`, `locale`, `passo` (`embed|tts|index`), `estado`
  (`pendente|ok|falha`), `tentativas`, `ultimo_erro`, `atualizado_em`. Drives the retryable publication
  pipeline with no broker.

No audio binaries and no embedding vectors are stored in PostgreSQL — only references (`audio_ref`,
`embedding_ref` + `embedding_model_id`). The vector itself lives in Elasticsearch; the audio in the
filesystem.

## Migration Strategy

Java → **centralized Flyway** in the `neuroassist-ai-migrations` (MIG) service, which owns migrations for
all schemas (per project `modules.registry` MIG entry). TREI does **not** run Flyway locally; it
contributes its migration scripts to the MIG service. The `arvore-fala` Elasticsearch mapping is a shared
asset owned jointly with CONV (declared in `project-context.json stack.elastic.indices`, slug
`arvore-fala`); TREI, as the writer, is responsible for keeping the index mapping (dense_vector
dimensions, `model_id`) coherent with CONV's query expectations (G5). Mapping changes are coordinated,
not unilateral.

## Data Lifecycle

- **Drafts (`rascunho`)** — retained **indefinitely** with no automatic expiry (ORG-001); discarded
  manually by the caregiver (`descartado`), which is a soft state, retained for audit.
- **Published nodes (`ativo`)** — long-lived; editing a phrase opens a new approval cycle for that locale
  while the prior published content stays live until re-publication (no CONV availability gap).
- **Derived stores** — the ES document and audio file are re-buildable from PostgreSQL authoring state
  (re-embed with the current `model_id`, re-synthesize via ElevenLabs). A full re-index is the recovery
  path and also the migration path if the embedding model id ever changes (must re-embed the whole corpus
  and cut CONV over, preserving G5).
- **Sensitivity / retention (SEC-002, LGPD).** Authored text, LLM transcripts, cloned-voice audio are
  sensitive; retention and erasure follow the project LGPD posture (`_extensions.compliance.lgpd = true`)
  — patient erasure must cascade across PostgreSQL, the ES documents, and the audio filesystem for that
  `paciente_id`. Exact retention windows are an open item for the compliance owner (doc 06).
