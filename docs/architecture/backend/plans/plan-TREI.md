# Plano — TREI (trei-service)

> **NeuroAssist AI** — Treinamento Assistido por IA (Autoria da Árvore de Falas)
> Serviço: `trei-service` | Stack: Java 21 / Spring Boot | Porta: 8095
> Gerado em: 2026-09-01 | Backlog: 22 issues funcionais + 5 de integração

---

## Status das prioridades

| P | Título | Status |
|---|---|---|
| P1 | Fundação — entidades, migrations, ports, controllers | pendente |
| P2 | Orquestração do domínio (golden path) | pendente |
| P3 | Persistência — adapters JPA (TREI + CONV write) | pendente |
| P4 | Integrações externas (LLM, Embedding, TTS, ES, PAC, HIST) | pendente |
| P5 | Segurança + multi-tenancy | pendente |
| P6 | Pipeline de publicação broker-less (outbox + idempotência) | pendente |
| P7 | Observabilidade + health | pendente |
| P8 | BDD end-to-end (5 features, 31 cenários) | pendente |
| P9 | Hardening (retry backoff, circuit-breakers, alertas) | pendente |

---

## 0. Leitura executiva

### Papel do serviço (extraído de 01-architectural-vision.md)

TREI é o **plano de autoria** da árvore de falas do NeuroAssist AI. É o único lugar
supervisionado onde o conteúdo que CONV serve em runtime é *criado e expandido*: um LLM
(Claude/Anthropic) conversa com a cuidadora e o paciente para propor novas categorias/frases,
e apenas conteúdo **aprovado por ambos os humanos** é vetorizado, vocalizado na voz clonada
do paciente e indexado para o CONV recuperar. TREI existe para que o runtime de comunicação
diária do paciente (CONV) possa permanecer retrieval-only e nunca alucinar.

### MVP scope (extraído de 01)

1. Sessão de autoria assistida por LLM (iniciada pela cuidadora) que propõe rascunhos de nós
   da árvore, scoped a um paciente + tenant + locale de autoria — LLM propõe, nunca publica.
2. Lifecycle de rascunho: `rascunho → aguardando_aprovacao → publicando → ativo` com dois gates
   de aprovação independentes (`confirmadoCuidadora`, `confirmadoPaciente`), sem expiração
   automática, descartável manualmente.
3. Gate de dupla autorização (ADR-003): publicação dispara apenas quando ambos os gates são
   `true`; aprovações são assíncronas e emitidas de shells diferentes sob identidades distintas.
4. Pipeline de publicação owned by TREI: embedding (self-hosted multilingual model, G5 parity
   com CONV) + síntese de voz ElevenLabs (uma vez, cacheado) + indexação no ES
   `neuroassist-ai_arvore-fala`, então nó `ativo` disponível para CONV.
5. Autoria bilíngue EN/PT: cada locale proposto, aprovado e publicado independentemente;
   estrutura do nó locale-invariante (ADR-018).

### Diagnóstico inicial (gaps mapeados em 06)

- **G5 (resolvido em SPEC-11):** paridade de model-id de embedding entre TREI (index-time) e CONV (query-time) é um hard invariant operacional; mismatch degrada silenciosamente o recall kNN → G5ModelIdGuard fail-closed no step `embed`.
- **G6:** `TREI_AUDIO_ROOT` deve apontar para o mesmo volume que `CONV_AUDIO_ROOT` em produção (documentar em setup-local.md + INT-TREI-005).
- **R2 (resolvido em SPEC-12):** flip `conv_no_conteudo.status = 'ativo'` apenas após todos os 3 steps do outbox (`embed`, `tts`, `index`) serem `ok` — nunca estado parcial visível ao CONV.
- **Learning D-492:** `application-test.yml` usa `jwk-set-uri` (não `issuer-uri`) — capturado em TREI-014 e TREI-020.

---

## 1. Estratégia geral

### Objetivo macro

Entregar o `trei-service` funcional de ponta-a-ponta: da sessão de autoria assistida
até o nó `ativo` indexado no ES com embedding G5-consistente e áudio ElevenLabs cacheado,
com dupla autorização enforçada, bilinguismo independente por locale e rastreabilidade
completa de cada proposta LLM.

### Ordem das prioridades

| P | Título | Motivo |
|---|---|---|
| P1 | Fundação (entidades, migrations, ports, controllers) | Nenhuma outra prioridade compila sem ela |
| P2 | Orquestração do domínio — golden path | Sessão + proposta + materialização + dupla aprovação é o núcleo de negócio |
| P3 | Persistência JPA (TREI + CONV write) | Application layer precisa de adapters para rodar testes integrados |
| P4 | Integrações externas | LLM, embedding sidecar, ElevenLabs, ES, PAC, HIST — podem ser paralelizados |
| P5 | Segurança + multi-tenancy | OAuth2 + schema routing + revogação — antes de qualquer teste end-to-end |
| P6 | Pipeline de publicação | Outbox broker-less, G5 gate, flip atômico |
| P7 | Observabilidade | Health endpoint, MDC, X-Correlation-Id |
| P8 | BDD end-to-end | 5 features validam contratos completos |
| P9 | Hardening | Retry backoff, circuit-breakers — produção-ready |

---

## 2. Plano por prioridade

### Prioridade 1 — Fundação

**Status:** pendente

#### Objetivo / Resultado esperado

Microserviço `trei-service` compilando com estrutura hexagonal completa: 4 entidades JPA
privadas do TREI, 3 scripts Flyway prontos para MIG, 9 ports declarados, 9 endpoints
mapeados com DTOs e RFC 7807 error model.

#### Issues vinculadas

- TREI-001 (domain entities + enums)
- TREI-002 (Flyway migrations V0201-V0203)
- TREI-003 (port interfaces)
- TREI-004 (controllers + DTOs)

#### Specs cobertas

- SPEC-04, SPEC-01

#### Sequência de execução

1. TREI-001 (entidades e enums — fundação)
2. TREI-003 (ports — dependem dos types do domínio)
3. TREI-004 (controllers + DTOs — dependem dos ports e enums)
4. TREI-002 (migrations — pode ser paralelo com TREI-004)

#### Criterios de aceite

- `./gradlew :trei-service:compileJava` sai 0
- Migrations V0201, V0202, V0203 contêm DDL exato conforme data-model §2.1-2.4
- V0202 contém o CHECK G5: `sub_estado <> 'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL)`
- `/tres/health` acessível sem auth (controllers compilados)

#### Riscos

- Dependência FK de `trei_proposta_llm` e `trei_conteudo_autoria` em `conv_no_arvore_fala` — a tabela CONV deve existir no schema antes de rodar as migrations TREI (ordering MIG garantido pela banda V0201+)

#### Instrução para implementador

Execute na ordem: TREI-001 → TREI-003 → TREI-004 + TREI-002. Use o DDL canônico de
`contracts/data-model.md §2` para os scripts Flyway. Não crie nenhum adapter aqui —
apenas interfaces em `domain.port`. Controllers mapeiam escopos conforme `api-contract.md §2`.

---

### Prioridade 2 — Orquestração do domínio (golden path)

**Status:** pendente

#### Objetivo / Resultado esperado

Golden path de autoria completo implementado na camada `application`: sessão iniciada →
proposta gerada (com LLM mock) → nó materializado → caregiver aprova → paciente aprova →
gate dispara → outbox criado. Testes unitários com Mockito confirmam sequência.

#### Issues vinculadas

- TREI-005 (SessaoTreinamentoService)
- TREI-006 (AutorizacaoService — dual gate)
- TREI-007 (NoAutoriaService — materialize + discard)

#### Specs cobertas

- SPEC-02, SPEC-03, SPEC-05

#### Sequência de execução

1. TREI-007 (materialização — o nó deve existir antes de ser aprovado)
2. TREI-005 (sessão + proposta — requer PAC mock no teste)
3. TREI-006 (gate de autorização — requer nó existente)

#### Criterios de aceite

- `./gradlew :trei-service:test --tests "*ServiceTest*"` sai 0 para os 3 services
- Gate dispara exatamente uma vez quando ambos os flags são `true`
- Re-aprovação do mesmo ator não cria outbox duplicado
- Segundo locale para mesmo noId reutiliza `conv_no_arvore_fala` (locale-invariant, ADR-018)

#### Riscos

- Dependência em `ConvTreeWriteAdapter` (TREI-009 — P3): usar mocks nos testes de P2 para desacoplar a sequência de implementação

#### Instrução para implementador

Implemente com Mockito para todos os ports (LlmAuthoringPort, HistAuditPort, repos). O
`AutorizacaoServiceImpl` deve verificar a UNIQUE constraint de `TreiPublicacaoOutbox` via
try/catch `DataIntegrityViolationException` para absorver replay (SPEC-05).

---

### Prioridade 3 — Persistência JPA (TREI + CONV write)

**Status:** pendente

#### Objetivo / Resultado esperado

Adapters JPA para as 4 tabelas privadas do TREI e adaptadores de escrita nas tabelas CONV
compartilhadas. `ConvTreeWriteAdapter.flipStatusAtivo` atômico e testado com `@DataJpaTest`.

#### Issues vinculadas

- TREI-008 (repos TREI-private)
- TREI-009 (ConvTreeWriteAdapter)

#### Specs cobertas

- SPEC-04

#### Sequência de execução

1. TREI-008 e TREI-009 podem ser paralelizados

#### Criterios de aceite

- `./gradlew :trei-service:test --tests "*RepositoryAdapter*"` sai 0
- `TreiPublicacaoOutboxRepository` tem método para outbox drainer (P6)
- `flipStatusAtivo` não altera `conv_no_conteudo.status` se outbox steps não estão todos `ok`

#### Riscos

- Tabelas CONV devem existir no schema H2 de teste — criar schema de setup para `@DataJpaTest`

#### Instrução para implementador

Use `@DataJpaTest` com um `schema.sql` de test que cria as tabelas CONV mínimas necessárias.
A UNIQUE constraint `(no_id, locale)` em `TreiConteudoAutoria` deve ser testada explicitamente.

---

### Prioridade 4 — Integrações externas

**Status:** pendente

#### Objetivo / Resultado esperado

6 adapters de integração implementados e testados com WireMock/mocks: Anthropic, embedding
sidecar (com G5ModelIdGuard), ElevenLabs + filesystem, ES IndexPort, PAC (patient resolution),
HIST (audit facts).

#### Issues vinculadas

- TREI-010 (AnthropicAdapter)
- TREI-011 (EmbeddingSidecarAdapter + G5ModelIdGuard)
- TREI-012 (ElevenLabsAdapter + FilesystemAudioStoreAdapter)
- TREI-013 (ElasticsearchIndexAdapter)
- INT-TREI-002 (PAC integration)
- INT-TREI-003 (HIST audit integration)
- INT-TREI-004 (ES IndexPort integration)

#### Specs cobertas

- SPEC-10, SPEC-11, SPEC-12, integration-contracts §1-5

#### Sequência de execução

- TREI-010, TREI-011, TREI-012, TREI-013 podem ser paralelizados
- INT-TREI-002, INT-TREI-003, INT-TREI-004 após os adapters respectivos

#### Criterios de aceite

- `./gradlew :trei-service:test --tests "*Adapter*"` sai 0
- G5ModelIdGuard lança exceção quando sidecar reporta model_id diferente de `TREI_EMBED_MODEL_ID`
- ElevenLabsAdapter não chama ElevenLabs se arquivo já existe (idempotente)
- `_id` ES é `<noId>_<locale>` sem variação

#### Riscos

- R1 (G5): model_id drift entre sidecar e `TREI_EMBED_MODEL_ID` → G5ModelIdGuard fail-closed mitiga
- R4 (ElevenLabs PHI): validar que nenhum campo clínico/diagnóstico entra no payload TTS

#### Instrução para implementador

TREI-011 é o adapter mais crítico — implemente o `G5ModelIdGuard` antes de integrar ao
OutboxDrainer. Use WireMock para simular Anthropic (503), sidecar (model_id errado), ElevenLabs
(retorna bytes) e ES (retorna ok). O Feign client para PAC e HIST deve usar `client_credentials`
OAuth2 (configurado em `application.yml` com `trei.pac.client-id` etc.).

---

### Prioridade 5 — Segurança + multi-tenancy

**Status:** pendente

#### Objetivo / Resultado esperado

Resource server OAuth2 configurado com `jwk-set-uri` (não `issuer-uri`); schema routing
por `empresa`; revogação ACS por request (fail-closed); dual-actor scope enforcement
(`/aprovacoes/cuidadora` ≠ `/aprovacoes/paciente`). `application-test.yml` correto.

#### Issues vinculadas

- TREI-014 (OAuth2 + TenantContextHolder)
- TREI-015 (ACS revocation + RevocationFilter)
- INT-TREI-001 (ACS integration)

#### Specs cobertas

- SPEC-06

#### Sequência de execução

1. TREI-014 (SecurityConfig base + application-test.yml — deve vir antes de TREI-015)
2. TREI-015 (RevocationFilter sobre a config do 014)
3. INT-TREI-001 (teste de integração com WireMock ACS)

#### Criterios de aceite

- `application-test.yml` contém `jwk-set-uri` e NÃO contém `issuer-uri` (D-492)
- Endpoint `/aprovacoes/paciente` com token de caregiver retorna 403
- JWT revogado retorna 401 antes de chegar ao controller
- ACS inacessível → request rejeitada (fail-closed)

#### Riscos

- Pitfall D-492: `issuer-uri` causa OIDC discovery failure em testes — mitigado pelo pitfall explícito em TREI-014

#### Instrução para implementador

Use JWKS estático para testes (arquivo JSON com RSA key pair gerado uma vez no `src/test/resources`).
O `TenantContextHolder` deve usar `ThreadLocal` e ser limpo em `finally` no filter.

---

### Prioridade 6 — Pipeline de publicação broker-less

**Status:** pendente

#### Objetivo / Resultado esperado

`OutboxDrainer` schedulado executando o pipeline `embed→tts→index` com retries idempotentes;
flip `ativo` atômico após todos os 3 steps; G5 gate no step `embed` (fail-closed); locale
nunca fica `ativo` parcialmente.

#### Issues vinculadas

- TREI-016 (PublicacaoService + OutboxDrainer)
- TREI-017 (IdempotencyGuard)
- INT-TREI-005 (CONV shared state integration)

#### Specs cobertas

- SPEC-05, SPEC-12, data-model §4

#### Sequência de execução

1. TREI-017 (IdempotencyGuard — reutilizado pelo TREI-016)
2. TREI-016 (OutboxDrainer — usa IdempotencyGuard + todos os adapters de P4)
3. INT-TREI-005 (validação end-to-end do flip de status)

#### Criterios de aceite

- `flipAtivo` NÃO é chamado se qualquer step está `pendente` ou `falha`
- G5ModelMismatchException → step `embed` marcado `falha` sem flip
- `conv_no_conteudo.status` flipa para `ativo` na mesma transação que fecha o outbox
- Step `tts` com arquivo existente → ElevenLabs não é chamado

#### Riscos

- R2 (publicação parcial): mitigado pelo gate de flip atômico — validar explicitamente em TREI-016 test
- Scheduling em testes: usar `outboxDrainer.processPendingSteps()` diretamente (sem `@Scheduled`)

#### Instrução para implementador

O `OutboxDrainer` deve buscar steps por `(estado=PENDENTE, proxima_em <= now())` com `Pageable`
(até 50 por ciclo). Para o step `embed`: chame `G5ModelIdGuard` antes de gravar o vetor; se
falhar, marque `estado=FALHA` sem retry (fail-closed, não exponential backoff para G5).

---

### Prioridade 7 — Observabilidade + health

**Status:** pendente

#### Objetivo / Resultado esperado

`GET /health` retorna status de todos os 6 subsistemas + `embedModelId`; `X-Correlation-Id`
propagado para todos os downstream calls; logs estruturados JSON com `correlationId` em
todos os campos.

#### Issues vinculadas

- TREI-018 (HealthController + TreiHealthIndicator + G5BootSmoke)
- TREI-019 (CorrelationIdFilter + logback-spring.xml)

#### Specs cobertas

- SPEC-07

#### Sequência de execução

1. TREI-019 (filter + logback — transversal a todos os outros)
2. TREI-018 (health endpoint — depende de G5BootSmoke que requer TREI-011 config)

#### Criterios de aceite

- `GET /health` retorna 200 sem `Authorization` header
- `embedModelId` no response é igual a `TREI_EMBED_MODEL_ID`
- `X-Correlation-Id` aparece em TODOS os outbound calls (Feign interceptor)
- `G5BootSmoke` loga WARNING se sidecar retorna model_id diferente do configurado

#### Riscos

- `CorrelationIdFilter` e `MDC` não devem vazar entre requests em ambiente com thread pool

#### Instrução para implementador

Implemente `CorrelationIdFilter` como `OncePerRequestFilter` com `MDC.remove("correlationId")`
no `finally`. O Feign `RequestInterceptor` deve ler `MDC.get("correlationId")` para propagar.

---

### Prioridade 8 — BDD end-to-end

**Status:** pendente

#### Objetivo / Resultado esperado

Os 31 cenários de 5 features gerados em M3 (SPEC-08) passando com WireMock; nenhum teste
requer Kafka, Keycloak real, ES real ou ElevenLabs real.

#### Issues vinculadas

- TREI-020 (dual-auth-gate + llm-authoring-proposal features)
- TREI-021 (publication-pipeline + bilingual-locale + G5-parity features)

#### Specs cobertas

- SPEC-08, SPEC-11

#### Sequência de execução

1. TREI-020 (runner + primeiras 2 features)
2. TREI-021 (demais 3 features — estende mesmo runner)

#### Criterios de aceite

- 31 cenários passando sem dependência de infra externa
- Cenário G5 mismatch → locale permanece `publicando`, `embedding_model_id` null
- Cenário bilingual → segundo locale reutiliza noId (sem nova linha em `conv_no_arvore_fala`)
- `application-test.yml` tem `jwk-set-uri` e NÃO tem `issuer-uri`

#### Riscos

- BDD gate Gradle/Cucumber está DIFERIDO até pós-scaffold (bddGate — M5): os testes compilarão mas só serão executados com `./gradlew :trei-service:test` após o scaffold

#### Instrução para implementador

Copie as features de `docs/architecture/backend/services/trei-service/tests/cucumber/`
para `src/test/resources/features/`. Configure WireMock com `@ClassRule` ou `WireMockExtension`
para cada subsistema externo. Use `SpringBootTest(webEnvironment=DEFINED_PORT)` para que
os controllers reais respondam.

---

### Prioridade 9 — Hardening

**Status:** pendente

#### Objetivo / Resultado esperado

Outbox retry com backoff exponencial (max 1h entre retries, max 5 tentativas); circuit-breakers
Resilience4j para LLM e TTS; steps em `falha` visíveis via health (`degradedCapabilities`)
e via `GET /nos/{noId}/publicacao`.

#### Issues vinculadas

- TREI-022 (OutboxRetryConfig + LlmCircuitBreaker + TtsCircuitBreaker)

#### Specs cobertas

- SPEC-07, SPEC-09

#### Sequência de execução

1. TREI-022 (após TREI-016 estar funcional)

#### Criterios de aceite

- `proxima_em` cresce exponencialmente para tentativas 1-5 (verificável via unit test)
- Circuit breaker aberto não marca step como `falha` permanente — apenas adia `proxima_em`
- `GET /health` inclui `degradedCapabilities: ["publication"]` quando step em `falha` > 30min

#### Riscos

- Resilience4j circuit breaker para LLM: ajustar window size e threshold para uso ocasional (não é chamado a todo segundo)

#### Instrução para implementador

Configure Resilience4j via `application.yml` (não hard-coded). O `OutboxDrainer` deve
distinguir entre falha normal (retry com backoff) e G5 mismatch (falha permanente, sem
retry). Use `@Scheduled(fixedDelay=5000)` para o drainer; desabilite nos testes com
`spring.task.scheduling.pool.size=0` e chame `processPendingSteps()` manualmente.

---

## 3. Dependências externas

| Dependência | Tipo | Impacto | Status |
|---|---|---|---|
| CONV — DDL das tabelas `conv_*` | Hard (FK) | Migrations TREI dependem de CONV V0001-V0003 rodarem antes | CONV backlog já gerado |
| ACS — `jwk-set-uri` + blacklist endpoint | Hard | Auth falhará sem ACS | ACS service declarado |
| PAC — resolução de `pacienteId` | Hard (session start) | 404 sem PAC | PAC backlog já gerado |
| HIST — audit facts endpoint | Soft (buffered) | Facts perdem mas autoria não bloqueia | HIST backlog já gerado |
| Embedding sidecar (self-hosted) | Hard (publication) | Step `embed` falha sem sidecar | Setup local doc |
| Anthropic API | Hard (authoring) | 503 sem API key/acesso | Env: `ANTHROPIC_API_KEY` |
| ElevenLabs API | Hard (publication TTS) | Step `tts` falha sem key | Env: `ELEVENLABS_API_KEY` |
| ES `neuroassist-ai_arvore-fala` index | Hard (publication) | Step `index` falha sem índice criado | Mapping owned by CONV |
| `elastic-stack-client-lib` | Hard | TREI usa a lib — não cria próprio client ES | Library interna |

---

## 4. Restrições arquiteturais (Java/Spring Boot)

- **ADR-001-hexagonal-architecture.md:** camadas `api / application / domain / infrastructure`; domain NÃO importa infrastructure; api NÃO importa infrastructure diretamente
- **ArchUnit RULE-03:** `@Transactional` somente em `application.service` (não em controllers, adapters ou domain)
- **ArchUnit RULE:** nenhum repositório Spring Data injetado diretamente em `application.*` ou `domain.*` — apenas via adapter
- **Pitfall D-492:** `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` em `application-test.yml`; NÃO usar `issuer-uri`; NÃO usar `@EmbeddedKafka` (`messaging.enabled=false`)
- **G5 hard gate:** `trei_conteudo_autoria.sub_estado = 'ativo'` proibido sem `embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL` — enforçado em DDL (V0202) e em runtime (G5ModelIdGuard)
- **Non-duplication (data-model §0):** TREI não re-declara DDL das tabelas `conv_*` — apenas mapeia JPA sobre o schema existente

---

## 5. Referências

- `docs/architecture/backend/services/trei-service/01-architectural-vision.md`
- `docs/architecture/backend/services/trei-service/contracts/api-contract.md`
- `docs/architecture/backend/services/trei-service/contracts/data-model.md`
- `docs/architecture/backend/services/trei-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/trei-service/contracts/hub-integration.md`
- `docs/architecture/backend/services/trei-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/trei-service/spec/backend/SPEC-01..SPEC-12`
- `docs/architecture/backend/services/trei-service/setup-local.md`
- `docs/architecture/backend/adr/ADR-001-hexagonal-architecture.md`
- `docs/architecture/backend/issues/issues_map-TREI.md`
