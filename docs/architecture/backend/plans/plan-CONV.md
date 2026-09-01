# Plano — CONV (conv-service)

> NeuroAssist AI · Módulo CONV — Conversação por Árvore de Falas
> Serviço: `conv-service` | Stack: Java 21 / Spring Boot 3.5.x | Porta local: 8090
> Plano gerado em: 2026-08-31 | Baseado em: SPEC-01..SPEC-12 + 5 feature files Gherkin (12/12 EARS PASS)

---

## Status das prioridades

| Prioridade | Título | Status |
|---|---|---|
| P1 | Scaffold + contratos mínimos | pendente |
| P2 | Golden path do domínio | pendente |
| P3 | Persistência + migrations | pendente |
| P4 | Integrações externas + busca semântica | pendente |
| P5 | Security + multi-tenancy + guards | pendente |
| P6 | Outbox sweep | pendente |
| P7 | Observabilidade + health | pendente |
| P8 | BDD end-to-end | pendente |
| P9 | Hardening (ADR + feature gap) | pendente |

---

## 0. Leitura executiva

### Papel do serviço

`conv-service` é o **runtime de comunicação assistiva** da plataforma NeuroAssist AI. Permite que
pacientes com doenças neurodegenerativas "falem" navegando por uma árvore de falas pré-curada, via
gesto (canal GEST), toque ou busca semântica por intenção de texto. O serviço é **retrieval-only**
em runtime: nenhum LLM, nenhuma chamada à ElevenLabs, nenhum embedding computado — o CONV consulta
o ES com `query_vector_builder` e reproduz áudio pré-sintetizado pelo TREI.

### MVP scope

- Navegação estática da árvore (PostgreSQL)
- Busca semântica assistida (ES kNN, fail-open)
- Seleção de nó (frase falada, áudio cacheado ou Web Speech fallback)
- Resolução de gesto para nó (VocabularyPort, soft dependency)
- Auditoria para HIST (best-effort + outbox local)
- Conteúdo bilíngue EN/PT (autoria independente por locale via TREI)
- Multi-tenant (schema-per-empresa)

### Diagnóstico inicial (gaps mapeados)

| Gap | Criticidade | Tratamento |
|---|---|---|
| G4 — engineFeatureRef formato real | Médio | INT-CONV-001 — canal de gesto degrada a `bound:false` se não resolvido |
| G5 — CONV↔TREI índice ES + model_id | **Bloqueia produção** | INT-CONV-002 — contrato a ser fechado com TREI |
| Proposals Tela 09 → TREI (FE gap) | Médio (FE) | INT-CONV-004 — CONV não adiciona endpoints; TREI os definirá |
| Risco R3 — blacklist ACS per-request | Baixo MVP | CONV-022 — ADR/spike recomenda sem cache para MVP |
| Risco R5 — model_id mismatch silencioso | Alto (produção) | INT-CONV-005 — env obrigatória + verificação startup |

---

## 1. Estratégia geral

### Objetivo macro

Implementar o `conv-service` de dentro para fora: primeiro a estrutura e o domínio (P1-P2),
depois persistência (P3), integrações (P4), segurança (P5), operação (P6-P7), e por fim cobertura
BDD completa (P8-P9). O serviço deve ser progressivamente funcional: ao final de P2, o golden path
(navegação + seleção de nó + outbox) funciona sem integrações reais (stubs). Ao final de P4, todas
as integrações reais estão conectadas. Ao final de P8, o BDD cobre todos os 5 arquivos `.feature`
existentes + `outbox-retry-sweep.feature` (criada em P9).

### Ordem das prioridades

| P | Título | Motivo |
|---|---|---|
| P1 | Scaffold + contratos mínimos | Base estrutural que tudo precisa; sem ela nada compila |
| P2 | Golden path do domínio | navigateTree + selectNode = núcleo de valor; devem funcionar com stubs |
| P3 | Persistência + migrations | DDL e repositórios necessários para integração real do golden path |
| P4 | Integrações + busca semântica | Conecta ACS, PAC/GEST, HIST e ES; habilita busca real e auditoria |
| P5 | Security + guards | OAuth2 + anti-biométrico + locale — sem isso o serviço não é deployável |
| P6 | Outbox sweep | Garante que fatos de auditoria não se percam sob outage do HIST |
| P7 | Observabilidade + health | Torna a degradação observável; crítico para operação com paciente |
| P8 | BDD end-to-end | Validação formal dos contratos; gate para scaffold |
| P9 | Hardening | ADR de cache blacklist + feature BDD do sweep — decisões e cobertura de gaps |

---

## 2. Plano por prioridade

### Prioridade 1 — Scaffold + contratos mínimos

**Status:** pendente

#### Objetivo / Resultado esperado

Módulo Gradle `conv-service` compilável com estrutura hexagonal (`api/application/domain/infrastructure`),
entidades JPA, DTOs e ports declarados. Nenhuma lógica de negócio ainda — apenas contratos.

#### Issues vinculadas

- CONV-001 — Estrutura Gradle + pacotes
- CONV-002 — Entidades JPA (ArvoreFala, NoArvoreFala, NoConteudo, AuditOutbox)
- CONV-003 — DTOs de API (todos os 4 endpoints)
- CONV-004 — Ports do domínio (5 ports + exceptions)

#### Specs cobertas

- SPEC-01, SPEC-04

#### Sequência de execução

1. CONV-001 (Gradle base) → 2. CONV-002 (entities) + CONV-003 (DTOs) em paralelo → 3. CONV-004 (ports, depende das entities)

#### Critérios de aceite

- `./gradlew :conv-service:compileJava` BUILD SUCCESS
- `application-test.yml` com `jwk-set-uri` (não `issuer-uri`)
- Ports em `domain/port/` sem dependência de Spring/infra

#### Riscos

- Conflito de versão entre `elastic-stack-client-lib` e outras deps Spring Boot: verificar BOM em P1

#### Instrução para implementador

Execute CONV-001 primeiro. Os demais (CONV-002, CONV-003) podem ser implementados em paralelo após
a estrutura Gradle existir. CONV-004 declara apenas interfaces — sem implementação. Learning D-492:
`application-test.yml` DEVE usar `jwk-set-uri` estático (nunca `issuer-uri`).

---

### Prioridade 2 — Golden path do domínio

**Status:** pendente

#### Objetivo / Resultado esperado

`GET /tree`, `POST /select` e `POST /bindings/resolve` funcionando com stubs de infraestrutura
(ports mockados via `@MockBean`). `navigateTree` aplica a regra de locale (pending_locale). `selectNode`
insere no outbox quando HIST falha. `resolveBinding` retorna `bound: false` para refs desconhecidos.

#### Issues vinculadas

- CONV-005 — ConvTreeService (navigateTree)
- CONV-006 — NodeSelectionService (selectNode + outbox)
- CONV-007 — BindingResolutionService (resolveBinding)
- INT-CONV-004 — Decisão: Proposals → TREI (documentação, não bloqueia)

#### Specs cobertas

- SPEC-02, SPEC-05, SPEC-09, SPEC-11

#### Sequência de execução

1. CONV-005 (navigateTree — base de resolução de nó) → 2. CONV-006 (selectNode, depende de resolução de nó) + CONV-007 (binding, independente) em paralelo → 3. INT-CONV-004 (decisão documental, qualquer momento)

#### Critérios de aceite

- `./gradlew :conv-service:test` com testes unitários de CONV-005/006/007 passando
- Regra de locale (`pending_locale`, `selecionavel: false`) coberta por teste unitário
- `selectNode` com HIST stub lançando `HistUnavailableException` → `200` retornado + outbox inserido

#### Riscos

- `NoConteudoRepository.findByNoIdInAndLocale` com N+1 query: verificar `@Query` em P2

#### Instrução para implementador

Use `@MockBean` para todos os ports em P2 — os adapters reais vêm em P3/P4. Foco em lógica de
domínio pura. Regra crítica de locale: **NUNCA** substituir locale ausente pelo outro locale —
retornar `pending_locale` e `texto: null`.

---

### Prioridade 3 — Persistência + migrations

**Status:** pendente

#### Objetivo / Resultado esperado

JPA repositories funcionais contra PostgreSQL real (test container); migrations Flyway V0001-V0003
entregues para MIG; `AudioRefPortAdapter` operacional (filesystem).

#### Issues vinculadas

- CONV-008 — Flyway migrations (V0001-V0003, shipped para MIG)
- CONV-009 — JPA repositories + adapters de persistência
- CONV-010 — AudioRefPortAdapter (filesystem, CONV_AUDIO_ROOT)

#### Specs cobertas

- SPEC-04, SPEC-10, SPEC-12

#### Sequência de execução

1. CONV-008 (migrations — schema deve existir para os repos) → 2. CONV-009 (repositories, depende do schema) + CONV-010 (AudioRef, independente) em paralelo

#### Critérios de aceite

- `./gradlew :conv-service:flywayValidate` com test container PostgreSQL: SUCCESS
- `NoConteudoRepository.findByNoIdInAndLocale` carrega árvore inteira em 1 query
- `AudioRefPortAdapter` retorna `Optional.empty()` sem NPE quando `CONV_AUDIO_ROOT` não configurado

#### Riscos

- Índice parcial `uq_conv_outbox_client_event WHERE client_event_id IS NOT NULL` pode não ser suportado por H2 — usar PostgreSQL test container desde P3

#### Instrução para implementador

Scripts SQL devem ser idênticos ao DDL de `data-model.md`. Não use `CREATE TABLE IF NOT EXISTS`
nos scripts Flyway. Depois de P3 concluída, substituir `@MockBean` dos repositórios pelos adapters
reais nos testes de integração.

---

### Prioridade 4 — Integrações externas + busca semântica

**Status:** pendente

#### Objetivo / Resultado esperado

Adapters Feign para ACS, PAC/GEST e HIST conectados com WireMock stubs. `SemanticSearchService`
funcional com ES via `SearchPort`. Gaps de integração documentados (INT-CONV-001..003, 005).

#### Issues vinculadas

- CONV-011 — AcsSessionAdapter (Feign, blacklist)
- CONV-012 — VocabularyAdapter (Feign, client_credentials)
- CONV-013 — HistAuditAdapter (Feign, best-effort)
- CONV-014 — SemanticSearchService (kNN, fail-open)
- INT-CONV-001 — GAP G4: formato engineFeatureRef
- INT-CONV-002 — GAP G5: contrato CONV↔TREI
- INT-CONV-003 — Contrato CONV↔HIST ingest
- INT-CONV-005 — SearchPort model_id alignment

#### Specs cobertas

- SPEC-03, SPEC-06, SPEC-09, SPEC-10, SPEC-12

#### Sequência de execução

1. INT-CONV-003 (confirmar contrato HIST antes de implementar o adapter) → 2. CONV-011 + CONV-012 + CONV-013 em paralelo → 3. CONV-014 (SemanticSearch, depende de SearchPort e HistAuditPort) → 4. INT-CONV-001 + INT-CONV-002 + INT-CONV-005 (documentação, paralelo)

#### Critérios de aceite

- Learning D-533: `neuroassist.acs.url`, `neuroassist.pac.url`, `neuroassist.hist.url` definidos em `application-test.yml` (Feign não falha no boot sem URL)
- WireMock stubs para ACS blacklist, PAC vocabulary, HIST ingest implementados
- SemanticSearchService: `k > 50` clamped; ES down → `degraded: true, retrieval: static, 200`; `intentHash` SHA-256 (nunca intent raw)

#### Riscos

- OAuth2 `client_credentials` para VocabularyAdapter requer Keycloak no ambiente de integração — usar WireMock stub de token endpoint para testes

#### Instrução para implementador

Learning D-533 é crítico: `application-test.yml` DEVE ter os 3 URLs de stub de Feign. Sem isso,
o contexto Spring falha na inicialização em testes. Para o `SemanticSearchService`, use o corpo kNN
exato de `integration-contracts.md` §1.1 — não inventar campos. `intentHash` = `SHA-256(intent)`,
NUNCA loga o texto raw do intent.

---

### Prioridade 5 — Security + multi-tenancy + guards

**Status:** pendente

#### Objetivo / Resultado esperado

OAuth2 Resource Server configurado; escopos enforced; caregiver/admin rejeitado; guard anti-biométrico
e locale no place; `ConvExceptionHandler` com RFC 7807 completo; client_credentials provisionado
para VocabularyPort (INT-CONV-006).

#### Issues vinculadas

- CONV-015 — OAuth2 Resource Server (SecurityConfig)
- CONV-016 — Guards anti-biométrico + locale
- INT-CONV-006 — Provisionamento client_credentials CONV→PAC/GEST

#### Specs cobertas

- SPEC-06, SPEC-11

#### Sequência de execução

1. CONV-015 (SecurityConfig — base) → 2. CONV-016 (guards, depende do filtro chain) + INT-CONV-006 (provisionamento Keycloak, paralelo com CONV-016)

#### Critérios de aceite

- `application-test.yml` com `jwk-set-uri` estático (D-492 critical)
- Token com `ROLE_CAREGIVER` + scope CONV válido → `403`
- POST com campo `image` no body → `400 no-biometric-ingress`
- Todos os erros RFC 7807 com `correlationId` presente

#### Riscos

- `ConvJwtAuthConverter` precisa rejeitar autoridade caregiver ANTES do scope check — verificar ordem dos filtros Spring Security

#### Instrução para implementador

`SecurityConfig` é pré-requisito para qualquer teste de integração com auth. Configure WireMock como
JWKS server nos testes para que Spring Security valide JWTs gerados pelo builder de teste sem
precisar do Keycloak real.

---

### Prioridade 6 — Outbox sweep

**Status:** pendente

#### Objetivo / Resultado esperado

`OutboxRetrySweepService` com `@Scheduled` funcional; retry budget respeitado; `DESCARTADO` após
exaurir tentativas com log de alerta.

#### Issues vinculadas

- CONV-017 — OutboxRetrySweepService

#### Specs cobertas

- SPEC-09

#### Sequência de execução

1. CONV-017 (único; depende de CONV-013 e CONV-009)

#### Critérios de aceite

- `@Scheduled` com `fixedDelayString` configurável (não hardcoded)
- `@Transactional` no adapter de update de status (não no service)
- Budget configurável via `neuroassist.conv.outbox.max-tentativas`

#### Riscos

- `@EnableScheduling` em ambiente de teste pode disparar o sweep durante testes de unidade — desabilitar com `spring.task.scheduling.enabled=false` em `application-test.yml`

---

### Prioridade 7 — Observabilidade + health

**Status:** pendente

#### Objetivo / Resultado esperado

`GET /api/v1/conv/health` funcional; métricas Micrometer registradas; `ConvServedDegraded` emitido
quando ES/audio degradado.

#### Issues vinculadas

- CONV-018 — Health endpoint + ConvServedDegraded + métricas

#### Specs cobertas

- SPEC-07

#### Sequência de execução

1. CONV-018 (depende de CONV-011 ACS e CONV-014 Semantic para verificar reachability)

#### Critérios de aceite

- `GET /health` sem autenticação retorna `200` com shape `{status, elasticsearch, audioStore, acs, degradedCapabilities[]}`
- `ACS unreachable` → `status: DEGRADED`
- Contadores `conv.retrieval.total` e `conv.playback.total` registrados no Actuator

---

### Prioridade 8 — BDD end-to-end

**Status:** pendente

#### Objetivo / Resultado esperado

Todos os 5 arquivos `.feature` existentes + `outbox-retry-sweep.feature` com 0 cenários UNDEFINED
ou FAILED. Infraestrutura BDD (CucumberSpringContext, SharedSteps) sem `@EmbeddedKafka`; step
novo D-492 implementado.

#### Issues vinculadas

- CONV-019 — CucumberSpringContext + SharedSteps + AssistedSearchSteps (D-492)
- CONV-020 — Step definitions para 4 features restantes

#### Specs cobertas

- SPEC-08

#### Sequência de execução

1. CONV-019 (infraestrutura BDD — base) → 2. CONV-020 (step defs para as 4 features restantes)

#### Critérios de aceite

- `./gradlew :conv-service:test` com relatório Cucumber: 0 FAILED, 0 UNDEFINED
- Learning D-492: sem `@EmbeddedKafka`, sem aspas escapadas em Cucumber Expressions, `the response status is {int}` apenas em SharedSteps
- Learning D-533: 3 URLs de stub em `application-test.yml`

#### Riscos

- `SessionRevocationSteps` precisa simular token revogado via WireMock (não via bypass do Spring Security) — garantir que o filtro de blacklist seja acionado realmente no teste

---

### Prioridade 9 — Hardening

**Status:** pendente

#### Objetivo / Resultado esperado

ADR de cache blacklist ACS formalizado (decisão MVP: sem cache); `outbox-retry-sweep.feature`
cobrindo gap de BDD do SPEC-09.

#### Issues vinculadas

- CONV-021 — outbox-retry-sweep.feature (gap BDD SPEC-09)
- CONV-022 — ADR técnico cache blacklist ACS (risco R3)

#### Specs cobertas

- SPEC-06, SPEC-09

#### Sequência de execução

CONV-021 e CONV-022 em paralelo (independentes entre si).

#### Critérios de aceite

- `outbox-retry-sweep.feature` com cenário de DESCARTADO e idempotência passando
- `ADR-006-blacklist-cache-strategy.md` com decisão MVP documentada
- Nenhum TODO[ADR-006] no `AcsSessionAdapter` sem o comentário de extensão correspondente

---

## 3. Dependências externas

| Dependência | Postura | Impacto se ausente |
|---|---|---|
| `acs-service` (ACS) | **Hard / fail-closed** | CONV não processa nenhuma requisição |
| `neuroassist-ai-migrations` (MIG) | Build/provision time | Tabelas ausentes → deploy bloqueado |
| `elastic-stack-client-lib` | Soft / SearchPort | Busca semântica degradada a estático (OPS-003) |
| `pac-service` / `gest-service` | Soft (canal gesto) | `bound: false` permanente; navegação e toque OK |
| `hist-service` (HIST) | Best-effort / outbox | Auditoria buffered no outbox; paciente não é impactado |
| TREI (dados compartilhados) | **Bloqueia produção** | Sem embeddings ES e sem áudio → busca semântica e TTS indisponíveis |
| Keycloak (shared realm) | Hard (auth) | Sem JWTs válidos; serviço inacessível |

---

## 4. Restrições arquiteturais (Java/Spring Boot)

- **ArchUnit RULE (ADR-001-hexagonal-architecture.md):** camadas `api → application → domain` (não o inverso); `infrastructure` implementa ports declarados em `domain`; `domain` nunca importa `org.springframework.*` ou `co.elastic.*`
- `@Transactional` somente em `application.service` (nunca em controllers, adapters ou domain)
- `@Scheduled` somente em `application.service.OutboxRetrySweepService`
- CONV nunca escreve em `conv_no_arvore_fala` / `conv_no_conteudo` (authoring é exclusivo do TREI)
- `engineFeatureRef` tratado como string opaca em toda a codebase (nenhum `split("/")`, `split(":")` ou regex de estrutura interna)
- `intentHash` = SHA-256 do intent; texto raw nunca persistido/logado (LGPD/SEC-002)
- Learning D-492 (aplicado obrigatoriamente): `application-test.yml` com `jwk-set-uri` estático; sem `@EmbeddedKafka`; `SharedSteps.java` único com `the response status is {int}`; sem aspas escapadas em Cucumber Expressions; sem `<param>` em `Scenario` plano

---

## 5. Referências

- `docs/architecture/backend/services/conv-service/contracts/api-contract.md`
- `docs/architecture/backend/services/conv-service/contracts/data-model.md`
- `docs/architecture/backend/services/conv-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/conv-service/contracts/hub-integration.md`
- `docs/architecture/backend/services/conv-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/conv-service/spec/backend/SPEC-01..SPEC-12`
- `docs/architecture/backend/services/conv-service/tests/cucumber/*.feature`
- `docs/architecture/backend/services/conv-service/design/index-design-arvore-fala.md`
- `docs/architecture/backend/services/conv-service/generator-config.yml`
- `docs/architecture/backend/issues/issues_map-CONV.md`
