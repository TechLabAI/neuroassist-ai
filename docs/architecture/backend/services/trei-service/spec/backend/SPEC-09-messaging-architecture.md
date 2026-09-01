# SPEC-09 — Messaging Architecture (broker-less publication pipeline)

> EARS acceptance criteria (D-057). Related:
> [`../../contracts/event-contracts.md`](../../contracts/event-contracts.md) (all),
> [`../../contracts/data-model.md`](../../contracts/data-model.md) §2.4/§4,
> [`../../events/event-catalog.md`](../../events/event-catalog.md).
> Decision: ADR-004 (publication pipeline). `stack.messaging.enabled = false`.

## Objetivo

Fixar a arquitetura de mensageria de TREI **sem broker**: eventos de domínio in-process, o pipeline de
publicação orientado a **outbox** (`embed→tts→index`) e os fatos de auditoria síncronos ao HIST. Descreve
onde SPEC-12 (o mecanismo do outbox) se encaixa e por que não há Kafka/RabbitMQ no MVP.

## Escopo

- **In:** postura de mensageria (sem broker), eventos de domínio in-process e seus handlers, o pipeline de
  publicação como máquina de estado outbox-driven, os fatos de auditoria ao HIST, versionamento de payload.
- **Out:** o mecanismo detalhado de retry/backoff/idempotência do outbox (SPEC-12 e SPEC-05), o contrato de
  índice ES (integration-contracts §4), reavaliação de broker assíncrono (fora do Estágio 0).

## Decisões técnicas

- **Sem broker assíncrono** (`messaging.enabled = false`, comentário de `project-context.json`): TREI não
  publica nem consome de Kafka/RabbitMQ. A sincronização edge→backend do projeto é REST/HTTPS síncrona com
  outbox local de retry — TREI herda essa postura.
- **Eventos de domínio são in-process** (não são eventos de fio): `SpeechNodeProposed`,
  `NodeApprovedByCaregiver`, `NodeApprovedByPatient`, `DualAuthorizationGateCleared`, `SpeechNodePublished`,
  `NodeDiscarded` — levantados nas camadas `domain`/`application` e tratados de forma síncrona
  (event-contracts §2).
- **Pipeline de publicação = outbox** (ADR-004): ao `DualAuthorizationGateCleared`, cria-se exatamente um
  conjunto de passos `embed/tts/index` em `trei_publicacao_outbox`; um drenador agendado avança os passos.
  O locale só vira `ativo` quando os três passos são `ok` (publicação atômica). Mecânica detalhada em
  SPEC-12.
- **Auditoria ao HIST é síncrona por referência**, protegida pela mesma postura de outbox local — uma queda
  do HIST é bufferizada e nunca bloqueia a autoria (event-contracts §4).
- **CONV observa estado, não evento:** `SpeechNodePublished` não é evento de fio; o CONV lê
  `conv_no_conteudo.status='ativo'` + o documento ES (acoplamento por estado, hub-integration §5).

## Implementação

- Publicação de eventos de domínio via `ApplicationEventPublisher` do Spring (in-process, síncrono);
  handlers na camada `application`.
- `trei_publicacao_outbox` como tabela de outbox (data-model §2.4); drenador agendado
  (`@Scheduled`) usa `proxima_em` (backoff) + `SELECT ... FOR UPDATE SKIP LOCKED` (SPEC-05).
- Auditoria ao HIST via `HistAuditPort`; falha → buffer no outbox local + retry.
- **Sem** `@EnableKafka`, sem topics, sem schema registry; qualquer versionamento de payload de fato de
  auditoria é aditivo e coordenado diretamente com o HIST (não há registry).

## Casos de borda

- Broker "acidental": nenhuma dependência de Kafka/RabbitMQ deve entrar no scaffold (guard de manifesto);
  `@EmbeddedKafka` não é usado em testes (Learning D-492).
- HIST fora do ar → fato de auditoria bufferizado; request de autoria/aprovação **não** falha.
- Gate disparado sob replay de aprovação → um único conjunto de passos criado (`ON CONFLICT`, SPEC-05).
- Evolução do documento ES (contrato compartilhado, `dynamic:strict`) → mudança coordenada com CONV, nunca
  unilateral.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL NOT publish to or consume from any async broker (`messaging.enabled = false`); all domain
  events SHALL be in-process and handled synchronously.
- WHEN `DualAuthorizationGateCleared` fires for a `(no_id, locale)`, THE SYSTEM SHALL create exactly one set
  of outbox steps (`embed`, `tts`, `index`) and start the pipeline once, idempotently under replay.
- THE SYSTEM SHALL raise `SpeechNodePublished` only after `embed`, `tts`, and `index` are all `ok`, so no
  observer ever sees a partially-published locale.
- THE SYSTEM SHALL couple with CONV only through shared state (PostgreSQL tree tables + ES index + audio
  store) and SHALL NOT emit a wire event or expose/consume a synchronous CONV endpoint.
- IF HIST is unreachable, THEN THE SYSTEM SHALL buffer the audit fact in the local outbox and retry, and
  SHALL NOT fail the authoring/approval request.
- THE SYSTEM SHALL version audit-fact payloads additively and SHALL coordinate any breaking change directly
  with HIST (no broker schema registry exists).
