# Plano -- GEST (gest-service)

## Status das prioridades
- P1 -- pendente
- P2 -- pendente
- P3 -- pendente
- P4 -- pendente
- P5 -- pendente
- P6 -- pendente
- P7 -- pendente
- P8 -- pendente
- P9 -- pendente

---

## 0. Leitura executiva

### Papel do servico (extraido de 01-architectural-vision.md)
`gest-service` e a **autoridade cloud-side de deteccao de gesto** (ADR-005 BE). Ele NAO recebe pixels nem executa visao computacional — isso ocorre localmente no edge. Sua responsabilidade e: (1) manter o catalogo de primitivos faciais com ciclo de vida controlado (`DetectabilityState`), (2) publicar perfis de deteccao imutaveis e versionados que o edge puxa para se configurar, (3) servir o guard de elegibilidade de emergencia consultado pelo PAC, e (4) congelar e publicar os schemas do contrato de deteccao (v1.0.0) usados pelo edge, EVNT e FE live-test.

### MVP scope (extraido de 01)
1. Catalogo de primitivos faciais com maquina de estados (registered→pending_training→supported→deprecated).
2. Guard de elegibilidade de emergencia: todos os primitivos devem ser `supported` para urgencia `emergencia` (invariante #1, ADR-006).
3. Perfis de deteccao imutaveis e versionados (invariante #2 snapshot, #3 monotonic versioning) — pull pelo edge via conditional GET (ETag/304).
4. Contrato de deteccao congelado v1.0.0: `RecognizedCommandReport` e `EdgeRecognitionSignal` (closes vision warning).
5. Validacao estateless de report (sem persistir biometricos — invariante LGPD).
6. Multi-tenancy schema-per-empresa via `SchemaRoutingDataSource`.

### Diagnostico inicial (gaps mapeados em 06)
- **G-1 (aberto — INT-GEST-007):** formato de credencial de dispositivo (mTLS vs. OAuth2 `client_credentials`) ainda nao congelado — SecurityConfig usa JWT-device provisorio; decisao conjunta GEST/EDGE pendente.
- **G-2 (diferido — GEST-024):** bddGate diferido para pos-scaffold; replay obrigatorio apos GEST-024.
- **G-3 (aceito):** criterio de promocao `pending_training→supported` e manual no MVP (SPEC-02 §4.4) — nenhum mecanismo automatico; conduzido out-of-band pelo conductor.
- **G-4 (aceito):** messaging desabilitado no MVP — eventos `Primitive*` e `DetectionProfilePublished` sao in-process apenas; extracao para broker deferred sem impacto de renaming.

---

## 1. Estrategia geral

### Objetivo macro
Entregar o `gest-service` como a autoridade cloud-side de deteccao com ciclo de vida de primitivos robusto, guard de seguranca de emergencia, perfis versionados para o edge, contrato de deteccao congelado e cobertura BDD completa — com zero persistencia de biometricos (invariante LGPD) e multi-tenancy schema-per-empresa.

### Ordem das prioridades

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + contratos minimos | base de compilacao; nada mais funciona sem o modulo Gradle, entidades JPA e ports |
| P2 | Orquestracao do dominio | golden path completo dos 4 invariantes ADR-006: register, transition, eligibility, profile publish |
| P3 | Persistencia + migrations | adapters JPA, Flyway V0400, schema routing — P2 precisa compilar, P3 faz funcionar com BD real |
| P4 | Integracoes externas | EdgePairingClient + conditional-GET; device auth aberto — INT-GEST-007 |
| P5 | Security + multi-tenancy | OAuth2 Resource Server + TenantContextHolder — habilita uso de JWT real |
| P6 | Messaging (in-process) | TransactionalEventListeners para eventos de dominio pos-commit |
| P7 | Observability + health | Actuator + Micrometer + logging JSON sem biometricos |
| P8 | BDD end-to-end | Cucumber infra + 4 features (~54 cenarios); bddGate replay |
| P9 | Hardening | Resilience4j circuit breaker + LGPD audit consolidado |

---

## 2. Plano por prioridade

### Prioridade 1 -- Scaffold + contratos minimos
Status: pendente

#### Objetivo / Resultado esperado
Modulo Gradle compilavel, entidades JPA TipoGesto/DetectionProfile com constraints, ports hexagonais, controllers skeleton com DTOs e error model RFC 7807. `./gradlew :services:gest-service:bootRun` sobe na porta 8084.

#### Issues vinculadas
- GEST-001: Gradle module + Spring Boot entrypoint + hexagonal skeleton
- GEST-002: TipoGesto + TipoGestoTransition JPA entities
- GEST-003: DetectionProfile + DetectionProfilePrimitive JPA entities
- GEST-004: Port interfaces (TipoGestoRepositoryPort, DetectionProfileRepositoryPort, EdgePairingPort)
- GEST-005: REST controllers skeleton + DTOs + RFC 7807 error handler

#### Specs cobertas
- SPEC-01, SPEC-04, data-model.md, api-contract.md (estrutura), ADR-006

#### Sequencia de execucao
- Fase 1: GEST-001 (Gradle + pacotes)
- Fase 2: GEST-002, GEST-003 em paralelo (entidades JPA independentes)
- Fase 3: GEST-004 (ports dependem das entidades)
- Fase 4: GEST-005 (controllers dependem dos ports)

#### Criterios de aceite
- `./gradlew :services:gest-service:compileJava` exit 0
- `GET /actuator/health` retorna `200 {status:UP}`
- `GET /api/v1/gest/tipos-gesto` retorna `200` (lista vazia)
- ArchUnit rule: application/domain nao importam infrastructure

#### Riscos
- **R1 (baixo):** constraints JPA (`@Check`) podem nao ser suportadas por todos os dialetos Hibernate — verificar PostgreSQL dialect.

#### Instrucao para implementador
Comece por GEST-001 (Gradle), depois implemente as entidades JPA em paralelo (GEST-002/003), depois os ports (GEST-004), e por ultimo os controllers (GEST-005). Nao implemente logica de negocio ainda — os services ficam como stubs que lancam `UnsupportedOperationException`. Valide com `compileJava` a cada issue.

---

### Prioridade 2 -- Orquestracao do dominio
Status: pendente

#### Objetivo / Resultado esperado
Golden path completo dos 4 invariantes-chave: (1) register + duplicate guard, (2) transition state machine com audit, (3) eligibility check emergencia, (4) detection profile publish com snapshot. Contrato de deteccao (getContractDescriptor + validateReport) operacional. INT-GEST-006 (contrato com PAC) validado.

#### Issues vinculadas
- GEST-006: TipoGestoService.register()
- GEST-007: TipoGestoService.transition()
- GEST-008: EligibilityService.check()
- GEST-009: DetectionProfileService.publish()
- GEST-010: DetectionContractService (descriptor + validateReport)
- INT-GEST-006: contrato com PAC

#### Specs cobertas
- SPEC-02, SPEC-03, SPEC-05, api-contract.md §2..§5, event-contracts.md §2..§3, ADR-006

#### Sequencia de execucao
- Fase 1: GEST-006 (register — mais simples, sem dependencias de servico)
- Fase 2: GEST-007 (transition — depende de register funcionando para testes)
- Fase 3: GEST-008 (eligibility — read-only, independente do anterior)
- Fase 4: GEST-009 (profile publish — depende de transition para ter SUPPORTED)
- Fase 5: GEST-010 (detection contract — depende de profile existente), INT-GEST-006

#### Criterios de aceite
- `POST /tipos-gesto` cria primitivo em REGISTERED, `409` em duplicata
- Todas as 5 transicoes permitidas do ADR-006 funcionam; ilegais retornam 409
- `POST /eligibility:check` com emergencia+todos-supported retorna `eligible:true`
- `POST /detection-profiles` cria perfil versionado e desativa o anterior
- `POST /recognized-commands` valida report sem persistir nada

#### Riscos
- **R2 (medio):** `nextVersion()` com lock pessimistico — verificar comportamento em PostgreSQL sob concorrencia (GEST-012 trata isso em P3).

#### Instrucao para implementador
Implemente em ordem sequencial (GEST-006→007→008→009→010). Os tests unitarios de cada issue devem passar antes de avancar. Nao conecte ao BD real ainda (use Mockito para `TipoGestoRepositoryPort` e `DetectionProfileRepositoryPort`) — a conexao real vira em P3.

---

### Prioridade 3 -- Persistencia + migrations
Status: pendente

#### Objetivo / Resultado esperado
JPA adapters funcionando com PostgreSQL real (Testcontainers); migration V0400 aplicavel; schema routing multi-tenant operacional. P2 agora funciona com BD real end-to-end.

#### Issues vinculadas
- GEST-011: JPA adapters TipoGesto + TipoGestoTransition
- GEST-012: JPA adapters DetectionProfile + DetectionProfilePrimitive
- GEST-013: Flyway migration V0400
- GEST-014: SchemaRoutingDataSource + 503 guard
- INT-GEST-005: contribuicao DDL para MIG

#### Specs cobertas
- SPEC-04, data-model.md, hub-integration.md §3

#### Sequencia de execucao
- Fase 1: GEST-013 (gerar migration — base para todos os testes de integracao)
- Fase 2: GEST-014 (schema routing — prerequisito para qualquer test de integracao com BD)
- Fase 3: GEST-011, GEST-012 em paralelo (adapters JPA)
- Fase 4: INT-GEST-005 (contribuir DDL para MIG)

#### Criterios de aceite
- Testes de integracao de GEST-011/012 passam com Testcontainers PostgreSQL
- Cross-tenant isolation: dados de tenant A nao vazam para tenant B
- `503` para tenant sem schema (guard de GEST-014)
- MIG aplica V0400 sem erros

#### Riscos
- **R3 (medio):** `ux_tipo_gesto_nome_active` e um indice parcial — verificar suporte no Hibernate Schema validator.

#### Instrucao para implementador
Comece pela migration (GEST-013) para ter o DDL como referencia. Depois configure o schema routing (GEST-014). Os adapters JPA (GEST-011/012) devem ter testes de integracao com Testcontainers que aplicam a migration antes dos cenarios.

---

### Prioridade 4 -- Integracoes externas
Status: pendente

#### Objetivo / Resultado esperado
EdgePairingClient funcional com WireMock; conditional-GET com ETag/304 operacional; descoberta via Eureka funcionando; INT-GEST-007 (bridge auth) com decisao iniciada.

#### Issues vinculadas
- GEST-015: EdgePairingClient Feign adapter
- GEST-016: Conditional-GET ETag/304 no profile pull
- INT-GEST-001: integracao EDGE fim-a-fim
- INT-GEST-003: integracao GWY
- INT-GEST-004: integracao REG
- INT-GEST-007: decisao de bridge transport auth (aberta)

#### Specs cobertas
- SPEC-10, integration-contracts.md §1, api-contract.md §4.4, §6.3

#### Sequencia de execucao
- Fase 1: GEST-015 (Feign client — base de INT-GEST-001 e GEST-016)
- Fase 2: GEST-016 (conditional-GET — depende de GEST-009 e GEST-015)
- Fase 3: INT-GEST-001, INT-GEST-003, INT-GEST-004 em paralelo
- Fase 4: INT-GEST-007 (agendar sessao com time EDGE)

#### Criterios de aceite
- Profile pull com device pareado retorna perfil ativo
- Profile pull com device nao-pareado retorna 403
- ETag/304 funciona para polling repetido
- INT-GEST-007 iniciado (sessao agendada)

#### Riscos
- **R4 (alto):** INT-GEST-007 depende de disponibilidade do time EDGE — nao bloqueia P4 mas deve ser iniciado nesta prioridade para nao atrasar P5.

#### Instrucao para implementador
Use WireMock para simular o EDGE em todos os testes do Feign client. A resolucao via Eureka (`lb://edge-service`) funciona apenas com REG no ar — em testes, use `feign.url` fixo via propriedade de teste.

---

### Prioridade 5 -- Security + multi-tenancy
Status: pendente

#### Objetivo / Resultado esperado
OAuth2 Resource Server configurado com scopes reais; TenantContextHolder populado do JWT; X-Correlation-Id ecoado em todas as respostas.

#### Issues vinculadas
- GEST-017: OAuth2 JWT resource server config
- GEST-018: TenantContextHolder + MDC + CorrelationIdFilter
- INT-GEST-002: integracao ACS (jwk-set-uri)

#### Specs cobertas
- SPEC-05, SPEC-06, api-contract.md §6, hub-integration.md §4

#### Sequencia de execucao
- Fase 1: GEST-017 (SecurityConfig — prerequisito para GEST-018)
- Fase 2: GEST-018 (filtros de contexto)
- Fase 3: INT-GEST-002 (validacao com ACS)

#### Criterios de aceite
- Request sem JWT retorna 401
- Token de scope gest.read em endpoint manage retorna 403
- tenantId nunca vem do request body (apenas do JWT)
- X-Correlation-Id ecoado em todas as respostas

#### Riscos
- **R5 (baixo):** jwk-set-uri aponta para acs-service via REG — em testes, usar TestConfiguration com chave de teste embutida.

#### Instrucao para implementador
Configure jwk-set-uri como variavel de ambiente com fallback para teste embutido. Use `@WithMockJwtUser` ou equivalente para testes de seguranca sem ACS real.

---

### Prioridade 6 -- Messaging (in-process)
Status: pendente

#### Objetivo / Resultado esperado
Eventos de dominio `Primitive*` e `DetectionProfilePublished` publicados pos-commit via Spring ApplicationEventPublisher; listeners isolados do caminho critico.

#### Issues vinculadas
- GEST-019: TransactionalEventListener para Primitive* events
- GEST-020: DetectionProfilePublished listener + cache invalidation

#### Specs cobertas
- SPEC-09, event-contracts.md §2

#### Sequencia de execucao
- Fase 1: GEST-019 (base de eventos — GEST-020 reutiliza o padrao)
- Fase 2: GEST-020 (DetectionProfilePublished listener)

#### Criterios de aceite
- Listener nao e chamado em rollback
- Excecao em listener nao afeta resposta do endpoint
- Cache invalido apos DetectionProfilePublished

#### Riscos
- **R6 (baixo):** `@TransactionalEventListener` com phase=AFTER_COMMIT pode nao ver a transacao ativa — garantir que listeners nao iniciam novas transacoes sem `@Transactional(propagation=REQUIRES_NEW)`.

#### Instrucao para implementador
Todos os listeners usam `@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)`. Se um listener precisar escrever no BD (ex.: cache persistido), use `propagation=REQUIRES_NEW`.

---

### Prioridade 7 -- Observability + health
Status: pendente

#### Objetivo / Resultado esperado
Actuator health/readiness/prometheus operacionais; metricas de dominio GEST incrementando; logging JSON estruturado sem biometricos.

#### Issues vinculadas
- GEST-021: Actuator + Micrometer metrics
- GEST-022: Structured JSON logging (privacy audit)

#### Specs cobertas
- SPEC-07, api-contract.md §6.1

#### Sequencia de execucao
- Fase 1: GEST-021, GEST-022 em paralelo (independentes)

#### Criterios de aceite
- `/actuator/health` retorna 200 sem JWT
- `/actuator/prometheus` expoe `gest_profile_versions_published_total`
- Nenhum log contem `video`, `image`, `landmark`, `base64`

#### Riscos
- Nenhum risco critico.

#### Instrucao para implementador
O teste de compliance de privacidade (GEST-022) deve ser executado como parte do CI — nao apenas localmente.

---

### Prioridade 8 -- BDD end-to-end
Status: pendente

#### Objetivo / Resultado esperado
Suite Cucumber com ~54 cenarios passando; bddGate muda de DEFERRED para PASS no master-plan.

#### Issues vinculadas
- GEST-023: Cucumber infra (Testcontainers + stubs)
- GEST-024: Step definitions para as 4 features

#### Specs cobertas
- SPEC-08, todos os invariantes ADR-006 cobertos por cenarios

#### Sequencia de execucao
- Fase 1: GEST-023 (infra — prerequisito obrigatorio)
- Fase 2: GEST-024 (step definitions — depende de toda a logica P1..P6)

#### Criterios de aceite
- 54/54 cenarios passam
- Nenhum `PendingException`
- `be-gates-replay.py --gate bddGate` retorna PASS

#### Riscos
- **R7 (medio):** bddGate estava DEFERRED desde M3 — se step definitions nao cobrirem todos os cenarios Gherkin, replay falha. Verificar feature files antes de implementar steps.

#### Instrucao para implementador
Leia as 4 feature files em `docs/architecture/backend/services/gest-service/tests/cucumber/` antes de implementar qualquer step. Copie-as para `src/test/resources/features/` sem modificacao. Implemente steps em ordem: lifecycle → eligibility → detection-profile → report-validation.

---

### Prioridade 9 -- Hardening
Status: pendente

#### Objetivo / Resultado esperado
Resilience4j circuit breaker no EdgePairingClient operacional; audit LGPD consolidado em toda a superficie do servico.

#### Issues vinculadas
- GEST-025: Resilience4j + LGPD audit

#### Specs cobertas
- SPEC-06, SPEC-09, integration-contracts.md §1.1..§1.2

#### Sequencia de execucao
- Fase 1: GEST-025 (Resilience4j + audit — unica issue de P9)

#### Criterios de aceite
- Circuit aberto apos 5 falhas consecutivas de EDGE
- 503 rapido (sem timeout) com circuito aberto
- Zero campos biometricos em qualquer surface de erro

#### Riscos
- **R8 (baixo):** configuracao Resilience4j para Feign pode conflitar com retry do OpenFeign — desabilitar retry do Feign e usar Resilience4j Retry exclusivamente.

#### Instrucao para implementador
Use `@CircuitBreaker(name="edgePairing")` apenas em `EdgePairingAdapter`, nao no Feign client diretamente. Teste o circuit breaker com WireMock configurando delay/5xx e `CircuitBreakerRegistry` injetado para introspeccao do estado.

---

## 3. Dependencias externas

| Servico | Tipo | Contrato | Status |
|---|---|---|---|
| EDGE service | Outbound (Feign) | `GET /api/v1/edge/devices/{deviceId}/tenant` | Contrato de resposta definido; transport auth ABERTO (INT-GEST-007) |
| ACS service | Inbound auth | `jwk-set-uri: http://acs-service/oauth2/jwks` | Operacional |
| GWY | Ingresso | Rota `/api/v1/gest/**` via Eureka | Operacional (GWY-002) |
| REG | Discovery | Eureka defaultZone | Operacional (REG-001..010) |
| MIG | Migrations | V0400..V0499 GEST DDL | A contribuir (GEST-013, INT-GEST-005) |
| PAC | Consumer | `POST /eligibility:check` consumido pelo PAC | Contrato definido (INT-GEST-006) |
| EVNT | Consumer | Recebe `RecognizedCommandReport` do edge (GEST valida estateless) | Contrato congelado v1.0.0 |

---

## 4. Restricoes arquiteturais

- **ArchUnit RULE-01..08 (CLAUDE.md):** aplicaveis ao hexagonal package layout.
- **Camadas hexagonais:** `api` → `application` → `domain` ← `infrastructure`; domain e application nao importam infrastructure.
- **`@Transactional` somente em `application.service`:** services sao a unica camada que gerencia transacoes; controllers e adapters nao usam `@Transactional`.
- **LGPD hard rule:** nenhuma coluna, payload, log ou erro pode carregar video, imagem, landmark, confianca (confidence) de gesto ou qualquer dado biometrico identificavel. Apenas identificadores opacos, estados e parametros numericos de classificacao.
- **Messaging desabilitado:** nenhuma dependencia de broker no classpath. Eventos sao in-process (`ApplicationEventPublisher`).
- **Schema routing:** toda query de negocio executa em `t_<tenantId>`; schema global `neuroassist_global` nao contem dados GEST.
- **Imutabilidade:** `TipoGestoTransition` e append-only. `DetectionProfile` e imutavel apos publish (apenas `active` flip). Nenhum DELETE hard em `TipoGesto` — apenas transicao para `deprecated`.

---

## 5. Referencias

- `docs/architecture/backend/services/gest-service/01-architectural-vision.md`
- `docs/architecture/backend/services/gest-service/contracts/api-contract.md`
- `docs/architecture/backend/services/gest-service/contracts/data-model.md`
- `docs/architecture/backend/services/gest-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/gest-service/contracts/hub-integration.md`
- `docs/architecture/backend/services/gest-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/gest-service/spec/backend/SPEC-01..SPEC-10`
- `docs/architecture/backend/services/gest-service/adr/ADR-005-edge-cloud-detection-split.md`
- `docs/architecture/backend/services/gest-service/adr/ADR-006-tipogesto-detectability-lifecycle.md`
- `docs/architecture/backend/services/gest-service/tests/cucumber/*.feature`
- `docs/architecture/backend/issues/GEST-001..GEST-025.md`
- `docs/architecture/backend/issues/INT-GEST-001..INT-GEST-007.md`
- `docs/architecture/backend/issues/issues_map-GEST.md`
- `docs/architecture/backend/services/gest-service/generator-config.yml`
