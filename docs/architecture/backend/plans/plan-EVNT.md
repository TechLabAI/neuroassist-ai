# Plano -- EVNT (evnt-service)

> Service: `evnt-service` · Module: **Eventos e Alarmes** · Project: NeuroAssist AI
> Generated: 2026-07-28 · Trace: tr_d19221e0c25b

## Status das prioridades

| Prioridade | Titulo | Status |
|---|---|---|
| P1 | Scaffold + API controllers + GlobalExceptionHandler | pendente |
| P2 | Notification orchestration (IngestRecognizedReport, OrchestrateNotification, ConfirmEvento, retry sweep) | pendente |
| P3 | Persistência JPA + Flyway migrations + Tenant routing | pendente |
| P4 | SSE live delivery (SseEmitterAdapter + Last-Event-ID replay) | pendente |
| P5 | Feign adapters (GEST, PAC, ACS, DASH, HIST) + contract tests | pendente |
| P6 | Security (OAuth2 resource server + device-token filter) | pendente |
| P7 | Observability + health (Actuator + Micrometer + MDC) | pendente |
| P8 | BDD end-to-end Cucumber scenarios | pendente |
| P9 | Hardening + circuit breakers + gap closeouts | pendente |

---

## 0. Leitura executiva

### Papel do serviço
`evnt-service` é o **system-of-record** dos Eventos do NeuroAssist AI (ADR-004). Recebe o
`RecognizedCommandReport` do agente edge via `POST /reports`, valida com GEST, persiste um `Evento`
com idempotência por `reportId`, despacha a notificação com tier determinístico pela urgência
(`emergencia → full_screen_alarm`, `rotina → discreet`), e fecha o loop com o feedback terminal do
cuidador (`real` / `falso_positivo`). O canal live para o painel Angular (`@neuroassist/evnt`)
é SSE (`GET /events/stream`) + polling fallback (`GET /events/active`), sem broker no MVP (ADR-005).

### MVP scope
- Ingress idempotente por `reportId` (GEST validation + PAC snapshot)
- Notificação urgência-determinística + retry durable para DASH (fail-open)
- Canal SSE com `Last-Event-ID` resume + keepalive
- Feedback terminal do cuidador (`confirmadoPor` via ACS + audit via HIST)
- Taxa de falso-positivo via `GET /events?groupBy=comando` (feed para PAC sensitivity tuning)
- Multi-tenancy `schema-per-empresa`
- LGPD: zero dados biométricos persistidos

### Diagnóstico inicial (gaps mapeados nos handoffs M2)
| # | Gap | Resolução |
|---|---|---|
| 1 | Auth device-token (mTLS vs OAuth2 client_credentials) | INT-EVNT-001 P1 — freezar com EDGE antes do scaffold hardening |
| 3 | Escalonamento de emergência via ACS (caregiver secundário) | INT-EVNT-004 P5 — decisão MVP: sem escalação automática; fail closed + métrica |
| 5 | Consumidor da taxa de falso-positivo | INT-EVNT-007 P9 — interface PAC→EVNT documentada em contracts |

---

## 1. Estratégia geral

### Objetivo macro
Entregar `evnt-service` compilável, testável por BDD, e operacionalmente seguro para o MVP do
NeuroAssist AI: zero eventos perdidos, zero dados biométricos persistidos, e canal live confiável
para o painel de cuidador.

### Ordem das prioridades

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + API + Exception handler | Fundação: sem isso nada compila ou é testável |
| P2 | Orquestração (Ingest → Generate → Notify → Confirm) | Golden path completo — dependência de todas as prioridades seguintes |
| P3 | Persistência + migrations + tenant routing | JPA entities e migrations são pré-requisito de qualquer integration test |
| P4 | SSE live delivery | Core feature de EVNT (FE ADR-005b depende disso); sem SSE o painel não funciona |
| P5 | Feign adapters + contract tests | Integrações reais com GEST/PAC/ACS/DASH/HIST + WireMock validation |
| P6 | Security (OAuth2 + device-token) | Requerido para qualquer teste de ponta a ponta com JWT real |
| P7 | Observability + health | Prod readiness; sem metrics não há alertas; sem health não há k8s routing |
| P8 | BDD Cucumber end-to-end | Validação de todos os cenários críticos (idempotência, SSE, confirmação, falso-positivo) |
| P9 | Hardening + circuit breakers + gap #1/#3/#5 | Refinamento final; depende de todos os adapters estarem implantados |

**Nota sobre SSE (P4) vs Integrações (P5):** SSE está em P4 porque a FE depende disso para a
entrega do MVP (ADR-005b). Os adapters Feign (P5) podem ser desenvolvidos em paralelo com P4
depois que P3 estiver completo, pois não há dependência direta entre eles.

---

## 2. Plano por prioridade

### Prioridade 1 -- Scaffold + API controllers + GlobalExceptionHandler

**Status:** pendente

#### Objetivo / Resultado esperado
Um `evnt-service` compilável com todos os controllers mapeados, DTOs declarados, e um
`GlobalExceptionHandler` que emite RFC 7807 `ProblemDetail` em todas as respostas de erro.
As issues são desenvolvíveis em paralelo depois de EVNT-001.

#### Issues vinculadas
- EVNT-001 (scaffold Gradle + port interfaces)
- EVNT-002 (ReportIngressController + biometric guard)
- EVNT-003 (EventQueryController + DTOs)
- EVNT-004 (ConfirmationController + ack)
- EVNT-005 (GlobalExceptionHandler RFC 7807)
- INT-EVNT-001 (freezar device-token contract com EDGE — arquitetura, não código)

#### Specs cobertas
- SPEC-01 (API surface)

#### Sequencia de execucao
1. Fase 1: EVNT-001 (scaffold base)
2. Fase 2: EVNT-005 (exception handler, pré-requisito de testes) em paralelo com EVNT-002, EVNT-003, EVNT-004
3. Fase 3: INT-EVNT-001 (decisão arquitetural com EDGE — pode ser feita em paralelo como discussão)

#### Criterios de aceite
- `./gradlew :services:evnt-service:compileJava` exits 0
- Todos os endpoints de api-contract §2 respondem (mesmo com services stubados)
- GlobalExceptionHandler mapeia todas as exceções de api-contract §6

#### Riscos
- INT-EVNT-001 pode bloquear P6 se a decisão demorar; o interim JWT-shaped token é suficiente para P6 não ser bloqueada imediatamente

#### Instrucao para implementador
Execute EVNT-001 antes de qualquer outra issue desta prioridade. Após o projeto compilar, EVNT-002/003/004/005 podem ir em paralelo por diferentes desenvolvedores. INT-EVNT-001 é uma reunião + decisão + documento, não requer código neste momento.

---

### Prioridade 2 -- Notification orchestration (IngestRecognizedReport, OrchestrateNotification, ConfirmEvento, retry sweep)

**Status:** pendente

#### Objetivo / Resultado esperado
A sequência completa de negócio: edge report → Evento persistido → dispatch criado → feedback do
cuidador registrado. Todos os ports chamados via mocks; sem adapters reais (que chegam em P3/P5).

#### Issues vinculadas
- EVNT-006 (IngestRecognizedReport + GenerateEvento)
- EVNT-007 (OrchestrateNotification + EventoCriado listener)
- EVNT-008 (ConfirmEvento + EventoConfirmado/FalsoPositivoMarcado)
- EVNT-009 (boot-time dispatch retry sweep)

#### Specs cobertas
- SPEC-02 (notification orchestration core)
- SPEC-05 (idempotency + concurrency)

#### Sequencia de execucao
1. Fase 1: EVNT-006 (IngestRecognizedReport — define o domain model Evento e publica EventoCriado)
2. Fase 2: EVNT-007 (OrchestrateNotification — consome EventoCriado)
3. Fase 3: EVNT-008 (ConfirmEvento — fecha o loop de feedback)
4. Fase 4: EVNT-009 (retry sweep — boot-time recovery)

#### Criterios de aceite
- Ingest → Evento criado atomicamente → EventoCriado publicado (unit test)
- Dispatch criado com tier correto por urgência determinística
- Feedback terminal set once; HIST e domain event publicados
- Retry sweep recupera dispatches pendentes no startup

#### Riscos
- EVNT-007 depende de CaregiverRoutingPort (ACS) para resolver o cuidador — mockado aqui; real em P5; testar fail-closed path com exceção mockada

#### Instrucao para implementador
EVNT-006 em primeiro. Use `@Transactional` apenas no `application.service` (CLAUDE.md RULE hexagonal). Portas são interfaces injetadas — use `@MockBean` nos testes de unidade. A sequência durable-first (persist antes de qualquer dispatch) é invariante — não inverta a ordem.

---

### Prioridade 3 -- Persistência JPA + Flyway migrations + Tenant routing

**Status:** pendente

#### Objetivo / Resultado esperado
Adapters JPA reais para os três portes de persistência (`EventoRepositoryPort`, `DispatchRepositoryPort`,
`IdempotencyRepositoryPort`), scripts Flyway V1..V4, e `TenantDataSourceRouter` que roteia conexões
para o schema correto. Após P3, todos os testes de integração podem rodar contra Testcontainers.

#### Issues vinculadas
- EVNT-010 (JPA entities + adapters)
- EVNT-011 (Flyway V1..V4)
- EVNT-012 (TenantDataSourceRouter + TenantContextHolder)

#### Specs cobertas
- SPEC-04 (data model + migrations)

#### Sequencia de execucao
1. Fase 1: EVNT-011 (scripts Flyway — pré-requisito para o schema existir)
2. Fase 2: EVNT-012 (TenantDataSourceRouter — schema routing funcional)
3. Fase 3: EVNT-010 (JPA entities + adapters + integration tests com Testcontainers)

#### Criterios de aceite
- V1..V4 aplicam sem erro contra Testcontainers PostgreSQL
- `evento.report_id` UNIQUE constraint dispara em insert duplicado
- `evento.seq` é bigserial gerenciado pelo DB (nunca setado pela aplicação)
- Cross-tenant: inserção em `t_empresa_a` não visível em `t_empresa_b`

#### Riscos
- `TenantDataSourceRouter` pode conflitar com Spring Boot auto-config de DataSource — testar com perfil `local` antes de P6 (onde o JWT real alimentará o tenant)

#### Instrucao para implementador
Derive as entidades JPA estritamente do DDL em `contracts/data-model.md` (não reinvente). O `seq` é `insertable=false, updatable=false` — o DB controla. Não habilite `spring.jpa.hibernate.ddl-auto` em nenhum perfil.

---

### Prioridade 4 -- SSE live delivery (SseEmitterAdapter + Last-Event-ID replay)

**Status:** pendente

#### Objetivo / Resultado esperado
Canal SSE funcional com fan-out por tenant, replay de eventos perdidos via `Last-Event-ID`, e
keepalive. Fecha definitivamente o FE ADR-005b que foi desbloqueado em M2. O painel Angular pode
agora consumir eventos em tempo real sem polling.

#### Issues vinculadas
- EVNT-013 (SseEmitterAdapter — fan-out por tenant)
- EVNT-014 (Last-Event-ID replay + keepalive scheduler)

#### Specs cobertas
- SPEC-03 (live event delivery)

#### Sequencia de execucao
1. Fase 1: EVNT-013 (SseEmitterAdapter básico com fan-out)
2. Fase 2: EVNT-014 (Last-Event-ID replay + keepalive)

#### Criterios de aceite
- `evento.created` SSE frame emitido para cada emitter aberto do tenant correto
- Reconnect com `Last-Event-ID=N` → frames com `seq > N` são reenviados
- Keepalive emitido a cada `keepAliveSeconds`
- Dead emitters removidos do registry automaticamente

#### Riscos
- `SseEmitter` com Tomcat pode ter comportamento diferente de Undertow — verificar servlet container do projeto
- Thread-safety do registry de emitters em carga alta (usar `CopyOnWriteArrayList`)

#### Instrucao para implementador
O SSE adapter usa `ApplicationEventPublisher` in-process — não há broker. O `EventStreamController`
(stub em P1/EVNT-003) será conectado ao adapter em EVNT-013. O `seq` vem do campo `evento.seq`
(bigserial) — nunca de uma sequência in-memory (perde no restart).

---

### Prioridade 5 -- Feign adapters (GEST, PAC, ACS, DASH, HIST) + contract tests

**Status:** pendente

#### Objetivo / Resultado esperado
Todos os cinco adapters Feign implementados e seus contratos validados com WireMock. Após P5,
os testes de integração end-to-end podem rodar sem mocks de ports.

#### Issues vinculadas
- EVNT-015 (GestFeignAdapter — fail-closed)
- EVNT-016 (PacFeignAdapter — snapshot resolve)
- EVNT-017 (AcsFeignAdapter + DashFeignAdapter + HistFeignAdapter)
- INT-EVNT-002 (contract test EVNT→GEST)
- INT-EVNT-003 (contract test EVNT→PAC)
- INT-EVNT-004 (contract test EVNT→ACS + escalação gap #3)
- INT-EVNT-005 (contract test EVNT→DASH fail-open)

#### Specs cobertas
- SPEC-10 (integrations)

#### Sequencia de execucao
1. Fase 1: EVNT-015, EVNT-016 (adapters críticos: GEST + PAC — na critical path do ingress)
2. Fase 2: EVNT-017 (ACS + DASH + HIST)
3. Fase 3: INT-EVNT-002..005 (contract tests)
4. Fase 4: INT-EVNT-004 (inclui resolução do gap #3 de escalação — decisão arquitetural)

#### Criterios de aceite
- Todos os adapters propagam `X-Correlation-Id`
- GEST fail-closed: timeout → `UpstreamUnavailableException` → `502/504`
- DASH fail-open: `503` → `DeliveryFailedException` → dispatch `failed` + retry
- HIST failure não bloqueia confirmação
- WireMock contract tests passam para todos os adapters

#### Riscos
- INT-EVNT-004 (gap #3 — escalação) pode revelar necessidade de campo adicional na API de ACS; coordenar com equipe ACS antes de implementar o adapter

#### Instrucao para implementador
Use `@FeignClient(url="${evnt.<service>.baseurl}")` para desenvolvimento local; Eureka resolve em produção quando `url` está vazio. Resilience4j circuit breakers são configurados em `application.yml` — não hardcode thresholds no código.

---

### Prioridade 6 -- Security (OAuth2 resource server + device-token filter)

**Status:** pendente

#### Objetivo / Resultado esperado
Todos os endpoints protegidos com os mecanismos corretos; tenant extraído do JWT e propagado para
`TenantContextHolder`; device-token interim implementado e swappável.

#### Issues vinculadas
- EVNT-018 (OAuth2 resource server + scope enforcement)
- EVNT-019 (device-token validation filter interim)

#### Specs cobertas
- SPEC-06 (security)

#### Sequencia de execucao
1. Fase 1: EVNT-018 (OAuth2 resource server — caregiver endpoints)
2. Fase 2: EVNT-019 (device-token filter para `/reports`)

#### Criterios de aceite
- GET endpoints sem JWT → 401
- POST /confirmation sem `evnt:confirm` → 403
- POST /reports com device JWT de tenant errado → 403 (sem leakage)
- Biometric payload + valid JWT → 400 (guard anterior à auth)

#### Riscos
- INT-EVNT-001 ainda pode estar em aberto; EVNT-019 usa interim JWT-shaped token — garantir que a interface `DeviceTokenValidator` isola a implementação final

#### Instrucao para implementador
`POST /reports` NÃO usa OAuth2 resource server — tem seu próprio filtro (EVNT-019). Garanta que a `SecurityFilterChain` exclua `/api/v1/reports` do JWT OAuth2 filter e inclua o device-token filter apenas nesse path.

---

### Prioridade 7 -- Observability + health

**Status:** pendente

#### Objetivo / Resultado esperado
Liveness/readiness probes para Kubernetes, métricas de negócio no Micrometer (Prometheus), e
logs estruturados JSON com `correlationId`/`tenantId` em cada linha.

#### Issues vinculadas
- EVNT-020 (Actuator health endpoints)
- EVNT-021 (Micrometer metrics + MDC structured logging)

#### Specs cobertas
- SPEC-07 (observability + health)

#### Sequencia de execucao
1. Fase 1: EVNT-020 + EVNT-021 em paralelo

#### Criterios de aceite
- `/actuator/health/liveness` e `/actuator/health/readiness` sem auth
- Counters, timers e gauges do Micrometer acessíveis em `/actuator/metrics`
- Log JSON com `correlationId`, `tenantId`, `eventoId` em cada linha de negócio

#### Riscos
- Logs estruturados podem revelar dados sensíveis se não configurados cuidadosamente — garantir que `deviceId` e `pacienteId` em logs sejam UUIDs opacos, não nomes/dados pessoais

---

### Prioridade 8 -- BDD Cucumber end-to-end

**Status:** pendente

#### Objetivo / Resultado esperado
Todos os cenários Cucumber gerados em M2 têm step definitions implementadas e passam em CI.
Cobertura: ingress golden path, idempotência, confirmação terminal, SSE live delivery, Last-Event-ID, taxa falso-positivo.

#### Issues vinculadas
- EVNT-022 (BDD: ingress golden path)
- EVNT-023 (BDD: idempotent ingress + terminal confirmation)
- EVNT-024 (BDD: SSE + Last-Event-ID + falso-positivo rate)
- INT-EVNT-006 (contract test EVNT→HIST — fact shapes)

#### Specs cobertas
- SPEC-08 (BDD test scenarios)

#### Sequencia de execucao
1. Fase 1: EVNT-022 (golden path — mais simples, valida P1+P2+P3+P5 de ponta a ponta)
2. Fase 2: EVNT-023 (idempotência + confirmação)
3. Fase 3: EVNT-024 (SSE) em paralelo com INT-EVNT-006 (HIST contract)

#### Criterios de aceite
- `./gradlew :services:evnt-service:test --tests '*CucumberRunner*'` exits 0 com todos os cenários verde
- Nenhum step undefined (sem amarelo no relatório Cucumber)
- Testcontainers PostgreSQL funcional em CI

---

### Prioridade 9 -- Hardening + circuit breakers + gap closeouts

**Status:** pendente

#### Objetivo / Resultado esperado
`evnt-service` endurecido para produção: circuit breakers configurados, dispatch exhaustion observável,
LGPD biometric guard auditado, e os três gaps dos handoffs fechados com decisões registradas.

#### Issues vinculadas
- EVNT-025 (Resilience4j circuit breakers + dispatch exhaustion + LGPD audit)
- INT-EVNT-007 (define interface consumidor falso-positivo rate com PAC — gap #5)

#### Specs cobertas
- SPEC-10 (integrations — circuit breakers)
- SPEC-06 (privacy invariant)

#### Criterios de aceite
- Circuit breaker abre após threshold; calls subsequentes falham rápido sem atingir o upstream
- `evnt.dispatch.exhausted` counter incrementado e alertável
- Biometric rejection auditada como `SECURITY_EVENT` no log
- INT-EVNT-007: PAC tem um contrato claro para consumir a taxa de falso-positivo

---

## 3. Dependências externas

| Dependência | Modulo | Tipo | Observação |
|---|---|---|---|
| GEST validation | GEST | REST Feign | Fail-closed na emergency path |
| PAC command snapshot | PAC | REST Feign | `urgencia_snapshot` vem daqui |
| ACS caregiver routing | ACS | REST Feign | Routing + confirmador resolve |
| DASH notification delivery | DASH | REST Feign | Fail-open; retry durable |
| HIST audit trail | HIST | REST Feign | Retryable; não na critical path |
| MIG | MIG | Infra | Aplica V1..V4 aos schemas de empresa |
| GWY + REG | GWY/REG | Infra | Routing + service discovery |
| EDGE (device-token) | EDGE | Decisão | INT-EVNT-001 — mTLS vs OAuth2 client_credentials |

---

## 4. Restrições arquiteturais

- **ArchUnit RULE hexagonal:** `domain` não pode importar `infrastructure`; `application` não pode
  importar `api` (controllers). Verificado por ArchUnit test em EVNT-025.
- **Camadas hexagonais:** `api` → `application` → `domain`; `infrastructure` implementa ports de `domain`/`application`.
- **`@Transactional` somente em `application.service`** — nunca em controllers ou repositories.
- **Durable-first:** persist `Evento` antes de qualquer attempt de dispatch. Uma falha de dispatch
  **nunca** reverte o `Evento` (ADR-004).
- **Tier derivation é pura:** `emergencia → full_screen_alarm`, `rotina → discreet` — nenhum branch
  pode baixar o nível de uma emergência (ADR-005).
- **Feedback terminal:** `feedback` transitiona de `nao_avaliado` para `real`/`falso_positivo` exatamente
  uma vez por `Evento`. Sobrescrição com verdict diferente = `409` (ADR-006).
- **LGPD:** zero dados de imagem, frame, ou landmark facial em qualquer tabela, log, ou payload.

---

## 5. Referências

- `docs/architecture/backend/services/evnt-service/contracts/api-contract.md`
- `docs/architecture/backend/services/evnt-service/contracts/data-model.md`
- `docs/architecture/backend/services/evnt-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/evnt-service/contracts/hub-integration.md`
- `docs/architecture/backend/services/evnt-service/spec/backend/SPEC-01..SPEC-10.md`
- `docs/architecture/backend/services/evnt-service/generator-config.yml`
- `docs/architecture/backend/services/evnt-service/setup-local.md`
- `docs/architecture/backend/issues/EVNT-001..EVNT-025.md`
- `docs/architecture/backend/issues/INT-EVNT-001..INT-EVNT-007.md`
- `docs/architecture/backend/issues/issues_map-EVNT.md`
