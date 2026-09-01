# SPEC-08 — BDD Test Scenarios

> EARS acceptance criteria (D-057). The executable Gherkin lives in
> [`../../tests/cucumber/`](../../tests/cucumber/); this SPEC is the **narrative index**, the reusable-step
> catalogue, and the example-data register that back those `.feature` files.
> Learning D-492: `@EmbeddedKafka` is **not** used (`messaging.enabled = false`) and
> `application-test.yml` sets `jwk-set-uri` (static test JWKS), never `issuer-uri`.

## Objetivo

Consolidar os cenários BDD que cobrem os invariantes de TREI, mapear cada `.feature` à SPEC/ADR de origem,
e documentar os **steps reutilizáveis** e os **dados de exemplo** — que, por convenção do
`be-bdd-generator.py`, não vivem nos arquivos `.feature` (Gherkin puro), mas aqui.

## Escopo

- **In:** índice das features Gherkin, cobertura por invariante, catálogo de step definitions reutilizáveis,
  dados de exemplo (fixtures), configuração de teste (perfil `test`, JWKS estático, sem broker).
- **Out:** cenários de runtime do CONV (outro serviço), testes de carga/perf, E2E cross-module (módulo E2E).

## Feature files (Gherkin puro) e origem

| Feature (`tests/cucumber/`) | Cobre | SPEC/ADR origem |
|---|---|---|
| `llm-authoring-proposal.feature` | sessão de autoria + proposta LLM não publica; disclosure de IA; `503` se Anthropic fora | SPEC-02, ADR-002/ADR-016 |
| `dual-authorization-gate.feature` | gate cuidadora+paciente assíncrono; ator errado `403`; revogação `401`; idempotência | SPEC-03, ADR-003 |
| `publication-pipeline.feature` | outbox `embed→tts→index`; ativação atômica; falha nunca `ativo`; TTS uma vez; 8 campos de trace | SPEC-12, ADR-004 |
| `embedding-model-parity-g5.feature` | G5: model-id gravado; sem model-id não ativa; sidecar divergente fail-closed; re-embed em troca | SPEC-11, ADR-015, data-model §3 |
| `bilingual-locale-independence.feature` | EN/PT autorados/aprovados/publicados por locale; estrutura invariante | SPEC-02, ADR-005/ADR-018 |

## Cobertura por invariante

- **Gate de dupla autorização (ADR-003):** nenhuma publicação com um flag `false`; disparo único ao fechar;
  ator errado rejeitado; revogação fail-closed. — `dual-authorization-gate.feature`.
- **Publicação atômica (ADR-004):** locale só vira `ativo` após `embed`+`tts`+`index` = `ok`; passo em falha
  mantém `publicando`. — `publication-pipeline.feature`.
- **G5 (data-model §3):** `embedding_model_id` gravado; CHECK do DDL barra `ativo` sem model-id; sidecar
  divergente fail-closed; dimensão errada rejeitada pelo ES; troca de modelo = re-embed coordenado.
  — `embedding-model-parity-g5.feature`.
- **Autoria bilíngue independente (ADR-018):** cada locale é um documento/áudio próprio; estrutura do nó é
  locale-invariante; sem vazamento de `texto` entre locales. — `bilingual-locale-independence.feature`.
- **Autoria off-runtime (ADR-002):** LLM só propõe; nada publica/embeda/sintetiza no passo de proposta;
  disclosure de IA. — `llm-authoring-proposal.feature`.

## Step definitions reutilizáveis (catálogo)

Steps compartilhados entre features (implementados uma vez, em Java, no diretório de glue do serviço):

- `Given a patient <pacienteId> in tenant <empresa>` — provisiona escopo de tenant/paciente (schema-per-empresa).
- `Given an authoring session started by the caregiver in locale <locale>` — cria `trei_sessao_treinamento`.
- `Given a draft node <noId> with locale <locale> in sub-state <subEstado>` — materializa `trei_conteudo_autoria`
  + `conv_no_conteudo(status='pendente')`.
- `Given the caregiver approves node <noId> locale <locale>` / `Given the patient approves node <noId> locale <locale>`
  — setam os flags via os endpoints de aprovação (tokens de ator distintos).
- `Given the <actor> token is on the ACS revocation blacklist` — arma o `AcsSessionPort` de teste (fail-closed).
- `When the <step> step succeeds|fails` — dirige o drenador do outbox (`embed|tts|index`).
- `Then the sub-state is|stays|becomes <subEstado>` / `Then the content status ... is|stays|becomes <status>`
  — asserts sobre `trei_conteudo_autoria.sub_estado` e `conv_no_conteudo.status`.
- `Then the ProblemDetail type suffix is <suffix>` — assert RFC 7807 (api §3).

**Cucumber Expression (Learning D-492):** placeholders são `{string}`/`{int}`/`{word}` — nunca regex cru com
`/`, `()` sem escape; parâmetros como `<locale>`, `<noId>` mapeiam a `{word}`/`{string}`. Evitar dois steps
que colidam na mesma expressão (ambiguidade).

## Dados de exemplo (fixtures)

- Tenant `clinica-acme`; paciente `p-1024`; cuidadora com escopos de autoria; paciente-com-sessão (ADR-012).
- Nó `n-90`, locales `en`/`pt`; `TREI_EMBED_MODEL_ID = multilingual-e5-v1` (fixture); dims 768, cosine.
- ES `_id` determinístico `n-90_en`; audio ref no layout `${TREI_AUDIO_ROOT}/<empresa>/<paciente>/<locale>/<no_id>.<ext>`.

## Configuração de teste

- Perfil `test`: `application-test.yml` com `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (JWKS
  estático) — **não** `issuer-uri` (Learning D-492, evita OIDC discovery no boot).
- **Sem** `@EmbeddedKafka` (`messaging.enabled = false`); o pipeline é in-process/outbox.
- Dependências externas (Anthropic, ElevenLabs, embedding sidecar, ES) são portas mockadas via os
  `Port` hexagonais; nenhum teste chama serviço de terceiros real.

## Casos de borda cobertos

- Aprovação simultânea cuidadora/paciente → outbox criado uma única vez (`ON CONFLICT`, SPEC-05).
- `tts` retried com arquivo em cache → nenhuma nova síntese ElevenLabs.
- `index` reenviado → upsert idempotente `_id = <no_id>_<locale>`.
- Locale sem `embedding_model_id` → ativação barrada pelo CHECK do DDL (G5).

## Critérios de aceite (EARS)

- THE SYSTEM SHALL keep every executable scenario as pure Gherkin under `tests/cucumber/*.feature`; prose,
  reusable steps, and example data SHALL live in this SPEC (be-bdd-generator ignores `.md`).
- WHEN the BDD suite runs, THE SYSTEM SHALL execute against mocked hexagonal ports for all third-party
  dependencies and SHALL NOT call Anthropic, ElevenLabs, the embedding sidecar, or ES over the network.
- THE SYSTEM SHALL configure the test profile with `jwk-set-uri` (static JWKS) and SHALL NOT configure
  `issuer-uri` (Learning D-492), and SHALL NOT declare `@EmbeddedKafka` (`messaging.enabled = false`).
- THE SYSTEM SHALL cover each domain invariant (dual-authorization gate, atomic publication, G5 parity,
  bilingual independence, off-runtime authoring) by at least one scenario mapped in the table above.
