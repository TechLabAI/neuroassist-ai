# SPEC-07 — Observability + Health

> EARS acceptance criteria (D-057). Related:
> [`../../contracts/api-contract.md`](../../contracts/api-contract.md) §2.9/§5,
> [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) §2/§4,
> [`../../contracts/data-model.md`](../../contracts/data-model.md) §3 (G5).
> Hexagonal boundary: shared `../../../adr/ADR-001-hexagonal-architecture.md` (`hexagonal_java`).

## Objetivo

Definir a observabilidade de `trei-service`: readiness/liveness que distinguem **autoria degradada** de
**runtime do paciente** (TREI está fora do caminho de comunicação do CONV), correlação ponta-a-ponta por
`X-Correlation-Id`, métricas do pipeline de publicação (outbox) e a visibilidade operacional do **G5**
(paridade de `embedding_model_id` com o CONV).

## Escopo

- **In:** endpoint de health (`GET /api/v1/trei/health`), liveness/readiness Spring Actuator, propagação de
  `correlationId`, métricas do outbox (profundidade, passos em `falha`, latência de publicação), exposição
  de `embedModelId` para diagnóstico de G5, logging estruturado sem PHI.
- **Out:** dashboards/alerting concretos (operação/infra), agregação central de logs/ELK do runtime do CONV,
  o `SearchPort` de leitura do CONV.

## Decisões técnicas

- **Health é uma projeção de dependências**, não um simples `UP`: distingue dependências **hard/fail-closed**
  (ACS) de dependências **soft de autoria** (Anthropic, ElevenLabs, embedding sidecar, ES, audio store).
  Uma indisponibilidade soft degrada a **autoria/publicação**, nunca o conteúdo já publicado — porque o
  paciente é servido pelo CONV, não pelo TREI (doc 01, api §2.9).
- **`correlationId`** injetado pela GWY quando ausente, ecoado na resposta e propagado a **toda** chamada
  downstream (ACS, PAC, HIST, Anthropic, ElevenLabs, embedding sidecar, ES) — um único trace cobre a cadeia
  autoria→publicação (api §1.1).
- **`embedModelId`** (= `TREI_EMBED_MODEL_ID`) é surfaced no health para tornar um mismatch G5 com o CONV
  operacionalmente visível **antes** de degradar o recall do paciente (data-model §3.3).
- **Métricas do outbox** expõem passos `pendente`/`falha` e profundidade da fila para que uma publicação
  travada seja observável sem inspeção manual de tabela (event-contracts §3).
- **Logging sem PHI:** prompts/respostas do LLM e transcrições nunca entram no log; apenas ids
  (`sessaoId`, `noId`, `locale`, `correlationId`) e o registro cotidiano — nunca texto clínico (SEC-002).

## Implementação

- Spring Boot Actuator: `/actuator/health` (liveness + readiness groups) internos; o endpoint público
  `GET /api/v1/trei/health` é a projeção de negócio de §2.9 do api-contract (agrega o estado das dependências
  de autoria/publicação + `embedModelId` + `degradedCapabilities`).
- `HealthIndicator` customizados por porta (`AcsSessionPort`, `LlmAuthoringPort`, `TtsSynthesisPort`,
  `EmbeddingPort`, `IndexPort`, `AudioStorePort`) na camada `infrastructure`; a agregação vive em
  `application`.
- Filtro/interceptor de `correlationId` na borda (`api`), propagado via header em todos os clients
  (`infrastructure`).
- Métricas Micrometer: `trei_outbox_depth{estado}`, `trei_outbox_step_failures_total{passo}`,
  `trei_publication_latency_seconds`, `trei_llm_proposal_total`, `trei_tts_synthesis_total` (custo, FIN-002).
- Log estruturado (JSON) com campos `correlationId`, `sessaoId`, `noId`, `locale`, `passo`; um redator
  garante que nenhum campo de texto autoral/clínico seja emitido.

## Casos de borda

- ES/embedding sidecar/ElevenLabs indisponível → `status: UP`, capability listada em `degradedCapabilities`;
  conteúdo já `ativo` continua servível pelo CONV (TREI fora do runtime).
- ACS indisponível → `status: DEGRADED`, `acs: UNREACHABLE` (identidade fail-closed).
- `TREI_EMBED_MODEL_ID` ausente/vazio no boot → health reporta a lacuna e o passo `embed` é fail-closed
  (G5, SPEC-11); mismatch com o sidecar é reportado.
- Passo do outbox em `falha` → contador `trei_outbox_step_failures_total` incrementa e o passo aparece em
  `GET /nos/{id}/publicacao`, sem o locale jamais mostrar `ativo`.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose `GET /api/v1/trei/health` reporting each authoring dependency (Anthropic,
  ElevenLabs, embedding sidecar, ES, audio store, ACS), the configured `embedModelId`, and the list
  `degradedCapabilities`.
- WHILE a soft authoring dependency (Anthropic, ElevenLabs, embedding sidecar, ES, or audio store) is
  unreachable, THE SYSTEM SHALL keep `status: UP`, list the affected capability in `degradedCapabilities`,
  and SHALL NOT report an outage that would imply the patient's communication is affected.
- IF ACS is unreachable, THEN THE SYSTEM SHALL report `acs: UNREACHABLE` and `status: DEGRADED`
  (identity is fail-closed).
- WHEN a request arrives without `X-Correlation-Id`, THE SYSTEM SHALL adopt the value injected by GWY, echo
  it on the response, and propagate it to every downstream call.
- THE SYSTEM SHALL surface `embedModelId` (= `TREI_EMBED_MODEL_ID`) on health so a G5 mismatch with CONV is
  operationally visible.
- WHILE any outbox step for a `(no_id, locale)` is in `falha`, THE SYSTEM SHALL expose it through metrics and
  the publication status endpoint and SHALL NOT report that locale as `ativo`.
- THE SYSTEM SHALL NOT emit LLM prompts/responses, transcripts, or any clinical/diagnostic text to logs or
  metrics (SEC-002).
