<!-- OUTPUT-SCHEMA
- Status
- §0 Leitura executiva
- §1 Achados
- §2 Estratégia
- §3 Prioridades
- §4 Riscos
- §5 Critérios de aceite
-->

# Plano — EVNT (evnt-service)

> Service: `evnt-service` · Module: **Eventos e Alarmes** · Project: NeuroAssist AI
> Atualizado: 2026-08-15 · Trace: tr_5e260914572a (baseline) · Planejamento: Incremental

## Status das prioridades

| Prioridade | Titulo | Status |
|---|---|---|
| P1 | Scaffold + API controllers + GlobalExceptionHandler | CONCLUIDA |
| P2 | Notification orchestration (IngestRecognizedReport, OrchestrateNotification, ConfirmEvento, retry sweep) | CONCLUIDA |
| P3 | Persistência JPA + Flyway migrations + Tenant routing | CONCLUIDA* (Flyway V1..V4 ausentes em neuroassist-ai-migrations — EVNT-011) |
| P4 | SSE live delivery (SseEmitterAdapter + Last-Event-ID replay + TenantResolverPort) | CONCLUIDA (2026-08-14: SseEmitterAdapter + keepalive + seq-based replay + RULE-04 fix) |
| P5 | Feign adapters (GEST, PAC, ACS, DASH, HIST) + contract tests | CONCLUIDA* (adapters presentes; WireMock contract tests INT-EVNT-002..005 pendentes) |
| P6 | Security (OAuth2 resource server + device-token filter) | CONCLUIDA* (interim — INT-EVNT-001 arquitetura pendente) |
| P7 | Observability + health (Actuator + Micrometer + MDC) | CONCLUIDA* (Actuator/MDC presentes; Micrometer business metrics a verificar) |
| P8 | BDD Cucumber end-to-end (36 cenários) | EM-PROGRESSO — 25/46 falhando (FULL-VALIDACAO); Spring context + Feign URI config issue. Bloqueador: CucumberSpringContext + Testcontainers setup |
| P9 | Hardening + circuit breakers + gap closeouts | EM-PROGRESSO (parcial) |

---

## Status

**Tipo:** Incremental
**Resumo:** Score 82/100 (Consistente) — atualizado 2026-08-15 (FULL-VALIDACAO). P1-P7 concluídas. P8 (BDD) framework pronto mas testes falhando por Spring context + Testcontainers config issue. P9 (hardening) parcial. Próxima ação (B2): resolver CucumberSpringContext + Feign URI config — após fix, retornar para re-validação.

---

## §0 Leitura executiva

### Papel do serviço
`evnt-service` é o system-of-record dos Eventos do NeuroAssist AI (ADR-004). Recebe
`RecognizedCommandReport` do agente edge via `POST /reports`, valida com GEST, persiste `Evento`
com idempotência por `reportId`, despacha notificação com tier determinístico por urgência
(`emergencia → full_screen_alarm`, `rotina → discreet`), e fecha o loop com feedback terminal do
cuidador. O canal live para o painel Angular é SSE (`GET /events/stream`) + polling fallback.

### Estado atual (pós-P4 implementação, 2026-08-14)
Score 78/100 (up from 72/100). O orquestrador de notificação está completo (P2), a persistência JPA e o roteamento
de tenant estão operacionais (P3), SSE live delivery agora funcional com replay (P4 NOVO), e todos os adapters Feign existem (P5).
O gap crítico que persiste é: 24/36 cenários BDD falhando por Spring context issue + Testcontainers. Bloqueador: P8 (BDD)
requer correção de spring context setup, não mais bloqueado por SSE.

### Continuidade da última rodada
Não há `last_implement` no ES (as prioridades P1..P7 foram implementadas em ciclo único antes do
baseline). O baseline identificou os bloqueadores e este plano refina a sequência de execução.

### Estado multi-empresa
`TenantDataSourceRouter` implementado em P3 (EVNT-012). `EmpresaContextHolder` propagado via
filtro HTTP e MDC. Schemas alvo: `t_<empresa_slug>` (schema-per-company). O SSE a implementar em
P4 deve fazer fan-out **por tenant** — somente emitters do tenant correto recebem frames.

---

## §1 Achados

### Pontos fortes
- ArchUnit OK: RULE-01..09 todas passando (`architectureCheck` exit 0)
- Orquestração de negócio completa (P2): ingest → persist → dispatch → confirm
- Adapters Feign declarados para GEST, PAC, ACS, DASH, HIST (P5)
- OAuth2 resource server + device-token filter interim operacionais (P6)
- Actuator health + MDC structured logging presentes (P7)

### Gaps identificados

| # | Gap | Causa | Prioridade | Issues |
|---|---|---|---|---|
| G1 | `SseEventBroadcastNoOpAdapter` é stub — SSE não funciona | P4 não implementada | P4 | EVNT-013, EVNT-014 |
| G2 | 24/36 BDD falhando — Spring context sem Testcontainers + steps de SSE sem adapter | P8 bloqueada parcialmente por P4 | P8 | EVNT-022..024 |
| G3 | Flyway V1..V4 ausentes em `neuroassist-ai-migrations` | P3 incompleta | P3 (residual) | EVNT-011 |
| G4 | WireMock contract tests ausentes (INT-EVNT-002..005) | P5 incompleta | P5 (residual) | INT-EVNT-002..005 |
| G5 | INT-EVNT-001 (device-token mTLS vs client_credentials) — decisão arquitetural pendente | Aguarda EDGE | P6 (residual) | INT-EVNT-001 |
| G6 | Micrometer business metrics — counters/timers de negócio a verificar | P7 parcial | P7 (residual) | EVNT-021 |

### Diagnóstico do BDD (24/36 failures)

**Causa raiz confirmada pela inspeção do código:**
1. `CucumberSpringContext` usa `@SpringBootTest(RANDOM_PORT)` mas NÃO configura Testcontainers
   PostgreSQL — diferente de `BaseIntegrationTest` que configura via `@DynamicPropertySource`
2. `application-test.yml` tem `ddl-auto: create-drop` e `flyway.enabled: false` →
   Spring Boot auto-configura H2 in-memory para o contexto BDD
3. `init-schema.sql` cria apenas schema + extensões, sem tabelas EVNT (Flyway V1-V4 ausentes)
4. `CucumberIT` roda em `DRY_RUN=true` — steps com `PendingException` são PENDING/SKIP
5. Cenários com steps SEM definição correspondente → UNDEFINED → FAIL mesmo em dry-run
6. Cenários de SSE (EVNT-024) requerem `SseEmitterAdapter` real (P4 não implementada)

**Conclusão:** as 24 falhas = cenários com steps UNDEFINED ou com compatibilidade H2/PostgreSQL
quebrada. O fix requer: (a) Testcontainers no `CucumberSpringContext`; (b) Flyway V1-V4 em
`neuroassist-ai-migrations`; (c) implementação real dos steps; (d) P4 completa para EVNT-024.

### Aderência às regras (RULE-01..09)
- RULE-01/02: domain sem Spring/JPA — VERIFICADO pelo ArchUnit (pass)
- RULE-03: @Transactional somente em application.service — VERIFICADO
- RULE-04/05: api não depende de infra, infra não depende de api — VERIFICADO
- RULE-06: @FeignClient somente em infrastructure.clients — VERIFICADO
- RULE-08: open-in-view=false — VERIFICADO em application.yml
- RULE-09: base package com.neuroassist.evnt — VERIFICADO

---

## §2 Estratégia

### Objetivo macro
Elevar `evnt-service` de 72/100 para >= 88/100 (Maduro) nos próximos 2 ciclos:
ciclo atual → P4 completa (SSE real); próximo ciclo → P8 completa (36/36 BDD green).
Os gaps residuais de P3/P5/P7 são fechados como parte de P8 (infra de teste).

### Ordem das prioridades

| P | Titulo | Motivo | Issues |
|---|---|---|---|
| P4 | SSE live delivery (SseEmitterAdapter + Last-Event-ID replay) | Core feature FE (ADR-005b); pré-requisito de EVNT-024; dependências P1/P2/P3 todas prontas | EVNT-013, EVNT-014 |
| P8 | BDD Cucumber end-to-end (fix infra + implementar 36 cenários) | Score sobe de 72 → 88+; EVNT-024 agora possível com P4 concluída | EVNT-022, EVNT-023, EVNT-024, INT-EVNT-006 |
| P9 | Hardening + circuit breakers + gap closeouts | Prod readiness; fecha gaps G3/G4/G5/G6 residuais | EVNT-025, INT-EVNT-002..007 |

---

## §3 Prioridades

### Prioridade 4 — SSE live delivery (SseEmitterAdapter + Last-Event-ID replay)

**Status:** PROXIMA PRIORIDADE
**Issues cobertas:** EVNT-013, EVNT-014

#### Objetivo
Substituir `SseEventBroadcastNoOpAdapter` (stub) por `SseEmitterAdapter` funcional com fan-out
por tenant, replay de eventos perdidos via `Last-Event-ID`, e keepalive. O painel Angular poderá
consumir eventos em tempo real sem polling.

#### Problema
`SseEventBroadcastNoOpAdapter` está anotado com `@Primary` e retorna void — nenhum frame SSE é
enviado. `EventStreamController` (stub de EVNT-003) não está conectado a nenhum adapter real.
`Last-Event-ID` e keepalive são inexistentes.

#### Correcoes mandatorias
1. Remover `@Primary` de `SseEventBroadcastNoOpAdapter` (ou remover o arquivo inteiramente)
2. Criar `SseEmitterAdapter` em `infrastructure.sse` implementando `SseEventBroadcastPort`
3. Manter registry `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` (thread-safe)
4. Implementar `register(tenant, emitter)` com callbacks de cleanup (completion/timeout/error)
5. Implementar `broadcast(tenant, eventType, data)` com `id: evento.seq` em cada frame
6. Wiring `@EventListener` em `EventoCriado` → `broadcast(tenant, "evento.created", ...)`
7. Wiring `@EventListener` em `EventoConfirmado`/`FalsoPositivoMarcado` → `broadcast(tenant, "evento.updated", ...)`
8. Implementar `Last-Event-ID` em `EventStreamController`: ler header → query `findBySeqGreaterThan` → enviar backfill antes de registrar emitter
9. Implementar keepalive: `@Scheduled(fixedDelayString = "${evnt.sse.keepalive-seconds:15}000")` iterando todos os emitters abertos

#### Alvos de alteracao (caminhos completos)

```
backend/services/evnt-service/src/main/java/com/neuroassist/evnt/
├── infrastructure/
│   ├── sse/
│   │   └── SseEmitterAdapter.java          [CRIAR]
│   └── persistence/adapter/
│       └── SseEventBroadcastNoOpAdapter.java  [REMOVER ou desativar @Primary]
├── api/controller/
│   └── EventStreamController.java          [ALTERAR — conectar ao SseEmitterAdapter]
└── infrastructure/config/
    └── SseConfig.java                      [CRIAR — @Bean SseEmitterAdapter timeout config]

backend/services/evnt-service/src/main/resources/
└── application.yml                         [ALTERAR — adicionar evnt.sse.keepalive-seconds: 15]

backend/services/evnt-service/src/test/java/com/neuroassist/evnt/
└── infrastructure/sse/
    └── SseEmitterAdapterTest.java          [CRIAR — unit tests]
```

#### Contrato operacional

**Deve existir:**
- `SseEmitterAdapter` implements `SseEventBroadcastPort` em `infrastructure.sse`
- Fan-out restrito por tenant: broadcast de `tenant_A` não envia frames a emitters de `tenant_B`
- `id: <evento.seq>` presente em cada frame SSE de negócio
- Emitter completado/timeout → removido do registry sem memory leak
- Keepalive frame (`event: keepalive`) emitido a cada `evnt.sse.keepalive-seconds` (default 15)
- Reconnect com `Last-Event-ID=N` → frames com `seq > N` enviados como backfill antes do stream

**Nao deve existir:**
- `SseEventBroadcastNoOpAdapter` com `@Primary` ativo após esta prioridade
- Sequência in-memory para `seq` — sempre usar `evento.seq` (bigserial do DB)
- `@Transactional` em `SseEmitterAdapter` (é infra, não application.service)

#### Sequencia de execucao
1. Fase 1 — Domain/Port: verificar `SseEventBroadcastPort` (porta out existente) — nenhuma alteração esperada
2. Fase 2 — Infrastructure: criar `SseEmitterAdapter` com registry thread-safe e `broadcast()` básico
3. Fase 3 — Infrastructure: adicionar `@EventListener` para `EventoCriado`/`EventoConfirmado`/`FalsoPositivoMarcado`
4. Fase 4 — Infrastructure: adicionar keepalive scheduler via `@Scheduled`
5. Fase 5 — API: alterar `EventStreamController` para chamar `SseEmitterAdapter.register()` e implementar `Last-Event-ID` backfill
6. Fase 6 — Config: adicionar `evnt.sse.keepalive-seconds` em `application.yml`; criar `SseConfig.java` se necessário
7. Fase 7 — Tests: criar `SseEmitterAdapterTest.java` (fan-out, cleanup, keepalive)

#### Politica de testes
- **Unit obrigatório:** `SseEmitterAdapterTest` com MockEmitters — fan-out correto por tenant, cleanup de emitter morto, sem cross-tenant leakage, backfill de seq > N
- **Integration:** não obrigatória para P4 (será coberta pelos BDD em P8 via EVNT-024)

#### Multi-empresa
`SseEmitterAdapter` deve usar o tenant como chave do registry. O tenant é extraído do
`TenantContextHolder` no momento da conexão SSE (`EventStreamController` já tem o tenant do JWT).

#### Migracoes Flyway necessarias
Nenhuma para P4 — SSE é in-process, sem persistência nova.

#### Criterios de aceite
- `./gradlew :services:evnt-service:compileJava` exits 0 após remoção do `@Primary` do NoOp adapter
- `SseEmitterAdapter` passa todos os unit tests: fan-out, cleanup, keepalive, backfill
- `EventStreamController` conectado ao adapter real (SseEmitterAdapter injetado, não NoOp)
- `GET /events/stream` retorna `Content-Type: text/event-stream` (verificável com curl)
- Broadcasting `EventoCriado` emite frame com `event: evento.created`, `data: {eventoId, urgencia, ...}`, `id: <seq>`
- Reconexão com `Last-Event-ID=3` retorna frames com `seq > 3` como backfill
- Keepalive verificável via unit test (task dispara → todos os emitters abertos recebem frame)

#### Riscos
- `SsEmitter` com Tomcat pode ter behavior de threading diferente — testar com `CopyOnWriteArrayList` e verificar comportamento de `complete()` em thread diferente do request
- Se `EventoCriado` é publicado via `ApplicationEventPublisher` síncrono, o `@EventListener` rodará na mesma thread do request — garantir que não bloqueia

#### Instrucao para implementador
Começar pela Fase 2 (criar `SseEmitterAdapter` básico e registro no Spring como `@Component`).
Remover `@Primary` do `SseEventBroadcastNoOpAdapter` no mesmo commit para evitar conflito de bean.
O `EventStreamController.streamEvents()` já existe como stub — conectar o `SseEmitterAdapter`
injetado via construtor (`@RequiredArgsConstructor`). Nunca usar `@Autowired` em field.
O `Last-Event-ID` vem do header HTTP `Last-Event-ID` (não de query param).
Usar `EventoRepositoryPort.findBySeqGreaterThan(tenantId, lastSeq)` para backfill — essa query
pode precisar ser adicionada ao port se não existir.

---

### Prioridade 8 — BDD Cucumber end-to-end (fix infra + implementar 36 cenários)

**Status:** EM-PROGRESSO — aguarda P4 completa para EVNT-024
**Issues cobertas:** EVNT-022, EVNT-023, EVNT-024, INT-EVNT-006

#### Objetivo
Corrigir o bloqueio sistêmico da infra de teste BDD (Spring context sem Testcontainers) e
implementar todos os 36 step definitions das 4 feature files, atingindo 36/36 cenários verdes.

#### Problema
Duas causas combinadas para 24/36 failures:
(a) `CucumberSpringContext` não configura Testcontainers PostgreSQL → Spring context usa H2
in-memory → queries incompatíveis com esquema EVNT falham; (b) step definitions são stubs com
`PendingException`; steps sem definição são UNDEFINED → FAIL.

#### Correcoes mandatorias
1. Alterar `CucumberSpringContext` para iniciar `PostgreSQLContainer` estático e registrar URL via `@DynamicPropertySource` (padrão `BaseIntegrationTest`)
2. Remover `DRY_RUN=true` de `CucumberIT` (ou mudar para `false`) após implementar os steps
3. Adicionar Flyway V1..V4 em `neuroassist-ai-migrations` (EVNT-011 residual de P3) — prerequisito para que o schema exista no Testcontainer
4. Implementar todos os steps de `UrgencyTieredNotificationSteps`, `ReportIngressIdempotencySteps`, `CaregiverConfirmationFeedbackSteps`, `LiveEventDeliverySteps`, `SharedSteps`
5. Configurar WireMock stubs para GEST/PAC/ACS/DASH/HIST no contexto BDD (substituir dependências reais)
6. EVNT-024 SSE steps requerem `SseEmitterAdapter` real (P4 concluída) e client SSE de teste (`WebTestClient` com `text/event-stream`)

#### Alvos de alteracao (caminhos completos)

```
backend/services/evnt-service/src/test/java/com/neuroassist/evnt/
├── bdd/
│   ├── CucumberSpringContext.java          [ALTERAR — adicionar PostgreSQLContainer + @DynamicPropertySource]
│   ├── CucumberIT.java                     [ALTERAR — remover DRY_RUN=true]
│   └── steps/
│       ├── SharedSteps.java                [ALTERAR — implementar todos os steps comuns]
│       ├── UrgencyTieredNotificationSteps.java  [ALTERAR — implementar 12 steps]
│       ├── ReportIngressIdempotencySteps.java   [ALTERAR — implementar steps de idempotência]
│       ├── CaregiverConfirmationFeedbackSteps.java [ALTERAR — implementar steps de confirmação]
│       └── LiveEventDeliverySteps.java     [ALTERAR — implementar steps SSE/Last-Event-ID]

backend/neuroassist-ai-migrations/src/main/resources/db/migration/
└── V1__evnt_evento.sql                     [CRIAR — schema t_emp_* + tabelas evento, notification_dispatch, report_idempotency]
└── V2__evnt_indexes.sql                    [CRIAR — índices por tenant]
└── V3__evnt_sequences.sql                  [CRIAR — bigserial seq]
└── V4__evnt_constraints.sql                [CRIAR — UNIQUE report_id, FK constraints]
```

#### Sequencia de execucao
1. Fase 1 — Flyway (EVNT-011): criar V1..V4 em `neuroassist-ai-migrations` baseado em `contracts/data-model.md`
2. Fase 2 — BDD infra: alterar `CucumberSpringContext` (Testcontainers + `@DynamicPropertySource`)
3. Fase 3 — BDD steps EVNT-022: implementar `UrgencyTieredNotificationSteps` + `SharedSteps` (WireMock GEST/PAC/ACS/DASH)
4. Fase 4 — BDD steps EVNT-023: implementar `ReportIngressIdempotencySteps` + `CaregiverConfirmationFeedbackSteps` (WireMock ACS/HIST)
5. Fase 5 — BDD steps EVNT-024: implementar `LiveEventDeliverySteps` (SSE client via WebTestClient + Last-Event-ID)
6. Fase 6 — Remover `DRY_RUN=true` de `CucumberIT`; executar e corrigir até 36/36 green

#### Politica de testes
- Integration obrigatória: Testcontainers PostgreSQL no contexto BDD
- WireMock para todos os adapters Feign no contexto de teste

#### Criterios de aceite
- `./gradlew :services:evnt-service:test --tests '*CucumberIT*'` exits 0 com 36/36 cenários verdes
- Nenhum step UNDEFINED no relatório Cucumber
- Flyway V1..V4 aplicam sem erro contra Testcontainers PostgreSQL
- WireMock stubs cobrem GEST, PAC, ACS, DASH, HIST

---

### Prioridade 9 — Hardening + circuit breakers + gap closeouts

**Status:** EM-PROGRESSO (parcial)
**Issues cobertas:** EVNT-025, INT-EVNT-002..007

#### Objetivo
Fechar todos os gaps residuais: Resilience4j circuit breakers configurados e verificados (EVNT-025),
WireMock contract tests para P5 (INT-EVNT-002..005), métricas de negócio Micrometer (EVNT-021),
e interface de falso-positivo com PAC (INT-EVNT-007).

#### Correcoes mandatorias
1. Verificar e completar configuração Resilience4j em `application.yml` para todos os adapters Feign
2. Criar testes de circuit breaker: após N falhas, calls falham rápido sem atingir upstream
3. Criar `evnt.dispatch.exhausted` Micrometer counter e verificar em test
4. Criar WireMock contract tests INT-EVNT-002..005 (GEST, PAC, ACS, DASH)
5. Documentar interface PAC→EVNT para consumo de falso-positivo rate (INT-EVNT-007)

#### Alvos de alteracao (caminhos completos)
```
backend/services/evnt-service/src/main/resources/
└── application.yml                         [ALTERAR — resilience4j configs explícitos por adapter]

backend/services/evnt-service/src/test/java/com/neuroassist/evnt/
├── integration/
│   └── CircuitBreakerIT.java               [CRIAR]
└── infrastructure/metrics/
    └── EvntMetricsTest.java                [CRIAR]

docs/architecture/backend/issues/
├── INT-EVNT-002.md                         [ALTERAR — marcar como WireMock implementado]
├── INT-EVNT-003.md                         [ALTERAR]
├── INT-EVNT-004.md                         [ALTERAR]
└── INT-EVNT-005.md                         [ALTERAR]
```

#### Criterios de aceite
- Circuit breaker GEST: após 5 falhas consecutivas → calls subsequentes retornam `UpstreamUnavailableException` sem atingir WireMock
- `evnt.dispatch.exhausted` counter incrementa quando todos os retry attempts falham
- WireMock contract tests INT-EVNT-002..005 passam em `integrationTest`
- INT-EVNT-007: documento de interface PAC→EVNT rate consumo criado em `docs/architecture/`

---

## §4 Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| SseEmitter threading com Tomcat | Média | Alta | Usar `CopyOnWriteArrayList` + testar cleanup em thread diferente |
| Flyway V1..V4 incompatíveis com JPA entities existentes | Média | Alta | Derivar DDL estritamente de `contracts/data-model.md`; rodar `ddl-auto=validate` em teste |
| Steps BDD com gaps de mapeamento (step UNDEFINED por regex mismatch) | Alta | Média | Rodar em dry-run primeiro para identificar UNDEFINEDs antes de implementar |
| INT-EVNT-001 (device-token) pode exigir alteração no adapter EDGE | Baixa | Média | `DeviceTokenValidator` interface já isola; implementação swappável |
| `EventoCriado` publicado síncronamente pode bloquear request thread via SSE | Baixa | Alta | Validar com teste de integração async; considerar `@Async` em `@EventListener` se necessário |

---

## §5 Critérios de aceite globais

### P4 concluída (próximo ciclo)
- `SseEmitterAdapter` implementado em `infrastructure.sse` — sem `@Primary` no NoOp adapter
- Unit tests do adapter passam: fan-out, cleanup, backfill, keepalive
- `GET /events/stream` retorna `Content-Type: text/event-stream`
- Broadcasting `EventoCriado` → frame com `id: <seq>` enviado aos emitters do tenant correto

### P8 concluída (ciclo seguinte)
- `./gradlew :services:evnt-service:test --tests '*CucumberIT*'` exits 0 com 36/36 verdes
- Flyway V1..V4 aplicam sem erro contra Testcontainers PostgreSQL
- `CucumberSpringContext` com Testcontainers — sem H2 in-memory

### P9 concluída (ciclo de hardening)
- Circuit breakers verificados por testes de integração
- WireMock contract tests INT-EVNT-002..005 passando
- Score esperado: >= 88/100 (Maduro)

---

## 4. Dependências externas

| Dependência | Módulo | Tipo | Observação |
|---|---|---|---|
| GEST validation | GEST | REST Feign | Fail-closed na emergency path |
| PAC command snapshot | PAC | REST Feign | `urgencia_snapshot` vem daqui |
| ACS caregiver routing | ACS | REST Feign | Routing + confirmador resolve |
| DASH notification delivery | DASH | REST Feign | Fail-open; retry durable |
| HIST audit trail | HIST | REST Feign | Retryable; não na critical path |
| MIG | MIG | Infra | Aplica V1..V4 aos schemas de empresa |
| EDGE (device-token) | EDGE | Decisão | INT-EVNT-001 — pendente |

---

## 5. Restrições arquiteturais

- ArchUnit RULE-01..09: verificado e passando — manter ao adicionar código em P4/P8
- `@Transactional` somente em `application.service` — SseEmitterAdapter é infra, não transacional
- Durable-first: persist Evento antes de qualquer dispatch — invariante de ADR-004
- Tier derivation pura: `emergencia → full_screen_alarm` — sem branch que rebaixe urgência
- LGPD: zero dados biométricos em logs, frames SSE ou tabelas

---

## 6. Referências

- `docs/architecture/backend/services/evnt-service/contracts/api-contract.md`
- `docs/architecture/backend/services/evnt-service/contracts/data-model.md`
- `docs/architecture/backend/services/evnt-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/evnt-service/spec/backend/SPEC-03.md` (SSE)
- `docs/architecture/backend/services/evnt-service/spec/backend/SPEC-08.md` (BDD)
- `docs/architecture/backend/issues/EVNT-013.md`, `EVNT-014.md` (P4)
- `docs/architecture/backend/issues/EVNT-022.md`, `EVNT-023.md`, `EVNT-024.md` (P8)
- ADR-004 (durable-first), ADR-005 (tier derivation), ADR-005b (SSE live delivery)
