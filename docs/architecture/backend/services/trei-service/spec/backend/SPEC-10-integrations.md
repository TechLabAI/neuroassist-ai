# SPEC-10 — Integrations

> EARS acceptance criteria (D-057). Related contracts:
> [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) (external),
> [`../../contracts/hub-integration.md`](../../contracts/hub-integration.md) (intra-mesh).
> All external access is through hexagonal ports (`../../../adr/ADR-001-hexagonal-architecture.md`,
> `hexagonal_java`). TechLab AI hub: **none** (`ai.hasAi = false`).

## Objetivo

Especificar as integrações de `trei-service` — externas (Anthropic, embedding sidecar, ElevenLabs,
Elasticsearch, audio filesystem) e intra-mesh (ACS, PAC, HIST, GWY/REG) — as portas hexagonais que as
mediam, suas posturas de falha e os invariantes que atravessam integrações (G5, PHI/SEC-002, atomicidade).

## Escopo

- **In:** contrato de porta de cada integração, autenticação/propagação de `correlationId`, postura de
  falha (hard/soft/outbox), fronteiras (o que TREI **não** integra).
- **Out:** o mecanismo do outbox (SPEC-12), a paridade G5 detalhada (SPEC-11), o modelo de segurança
  detalhado (SPEC-06), a mensageria (SPEC-09).

## Decisões técnicas

- **Toda integração externa é mediada por uma porta hexagonal** — nenhum client de terceiro vaza para
  `application`/`domain`. Portas: `LlmAuthoringPort` (Anthropic), `EmbeddingPort` (sidecar),
  `TtsSynthesisPort` + `AudioStorePort` (ElevenLabs + FS), `IndexPort` (ES, via `elastic-stack-client-lib`),
  e as intra-mesh `AcsSessionPort`, `PacPatientPort`/`VocabularyPort`, `HistAuditPort`.
- **ES só via `IndexPort` compartilhado** — TREI importa a lib e **nunca** instancia `co.elastic.clients`;
  usa apenas operações de **escrita** (leitura `SearchPort` é do CONV). `_id` determinístico
  `<no_id>_<locale>`; toda escrita carrega o **bloco de 8 campos de rastreabilidade** (D-269), sob pena de
  DLQ pela adapter compartilhada.
- **Posturas de falha:**
  - **Hard/fail-closed:** ACS (identidade). Indisponível → request rejeitado / health `DEGRADED`.
  - **Hard para autoria (não para runtime):** Anthropic. Indisponível → proposta `503`, sem estado parcial;
    conteúdo já publicado e o CONV não são afetados.
  - **Soft/outbox (retry):** embedding sidecar, ElevenLabs, ES — o passo do outbox fica `pendente`+backoff;
    o locale permanece `publicando`, nunca `ativo` (atomicidade, ADR-004).
  - **Soft/buffered:** HIST — fato de auditoria bufferizado; nunca bloqueia o request.
- **TechLab AI hub = none** (`ai.hasAi = false`): a dependência de LLM é a API **direta** da Anthropic, não
  o hub de orquestração; nenhuma capability de knowledge/document_composition/analytics é consumida.
- **Sem broker, sem object storage** no MVP: publicação in-process/outbox; áudio no filesystem local
  (ADR-017), no mesmo root que o CONV lê (`TREI_AUDIO_ROOT == CONV_AUDIO_ROOT`).

## Implementação

- Clients de terceiros vivem em `infrastructure`; `application` só conhece as interfaces de porta.
- `correlationId` propagado em **todas** as chamadas downstream (headers), inclusive Anthropic/ElevenLabs/ES.
- S2S para PAC via OAuth2 `client_credentials` (leitura de paciente/vocabulário); `engine_feature_ref`
  tratado como token **opaco** (gap G4, nunca parseado).
- Superfície de configuração (env) em [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md)
  §7 e [`../../setup-local.md`](../../setup-local.md).

## Casos de borda

- Anthropic fora → `503 llm-unavailable`, nenhuma `trei_proposta_llm` parcial.
- Sidecar reporta model-id runtime ≠ `TREI_EMBED_MODEL_ID` → passo `embed` = `falha` (G5 fail-closed,
  SPEC-11); nenhuma escrita no ES.
- ElevenLabs fora no publish → passo `tts` = `pendente`+retry; locale não vira `ativo`.
- `IndexPort` levanta `ElasticException` → passo `index` = `pendente`+retry; ES pode lagar o PostgreSQL
  (autoritativo), e o CONV degrada tratando um miss como "não encontrado" (OPS-003).
- Escrita ES sem os 8 campos → rejeitada (DLQ) pela adapter compartilhada; locale permanece não-`ativo`.
- Body com imagem/frame/landmark → `400 no-biometric-ingress` (TREI autora texto, não biometria).

## Critérios de aceite (EARS)

- THE SYSTEM SHALL access every external and intra-mesh dependency only through its hexagonal port and SHALL
  NOT leak a third-party client into the `application`/`domain` layers.
- THE SYSTEM SHALL write to Elasticsearch only through the shared `IndexPort`, using a deterministic
  `_id = <no_id>_<locale>` and the mandatory 8-field traceability block; an incomplete write SHALL be
  rejected (DLQ) and SHALL leave the locale non-`ativo`.
- IF the Anthropic API is unreachable, THEN THE SYSTEM SHALL fail the proposal turn with `503` and SHALL
  leave no partial `trei_proposta_llm` row (hard for authoring, no effect on published content or CONV).
- IF the embedding sidecar, ElevenLabs, or ES is unreachable at publish time, THEN THE SYSTEM SHALL keep the
  corresponding outbox step `pendente` with backoff and SHALL NOT mark the locale `ativo`.
- IF ACS is unreachable, THEN THE SYSTEM SHALL fail closed on identity (reject / health `DEGRADED`).
- IF HIST is unreachable, THEN THE SYSTEM SHALL buffer the audit fact and retry without failing the caller.
- THE SYSTEM SHALL consume no TechLab AI hub capability (`ai.hasAi = false`) and SHALL treat
  `engine_feature_ref` as an opaque identifier it never parses.
- THE SYSTEM SHALL write cached audio to the same root CONV reads (`TREI_AUDIO_ROOT == CONV_AUDIO_ROOT`) and
  SHALL NOT use any async broker or object storage in the MVP.
