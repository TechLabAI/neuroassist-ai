# Issues Map -- REG (neuroassist-ai-service-registry)

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| REG-001 | Criar neuroassist-ai-service-registry: modulo Gradle e bootstrap do Eureka Server | BE | P1 | INTACT |
| REG-002 | Configurar application.yml completo: lease lifecycle, self-preservation e flags de single-node | BE | P1 | INTACT |
| REG-003 | Implementar endpoint de readiness customizado: registry aceitando registros | BE | P1 | INTACT |
| REG-004 | Configurar seguranca da superficie interna: sem auth, restricao de rede | BE | P2 | INTACT |
| REG-005 | Configurar observabilidade: logs estruturados de ciclo de vida e metricas Prometheus | BE | P2 | INTACT |
| REG-006 | Configurar liveness e metricas de self-preservation como sinal de operador | BE | P2 | INTACT |
| REG-007 | Escrever testes de bootstrap e configuracao: SpringBootTest + propriedades | BE | P3 | INTACT |
| REG-008 | Implementar BDD Cucumber: registro, eviction e discovery | BE | P3 | INTACT |
| REG-009 | Docker Compose local: neuroassist-ai-service-registry + healthcheck | BE | P3 | INTACT |
| REG-010 | Hardening: configuracoes de producao e runbook de operacao | BE | P4 | INTACT |
| INT-REG-001 | Validar integracao de registro com clientes in-fleet: GWY, PAC, EVNT | BE | P2 | INTACT |
| INT-REG-002 | Validar integracao de observabilidade: logs de ciclo de vida no pipeline da plataforma | BE | P3 | INTACT |
| INT-REG-003 | Validar registro da frota completa: todos os 9 servicos em membership | BE | P4 | INTACT |

## Por SPEC

| SPEC | Issues |
|---|---|
| SPEC-01 (API Surface / Eureka Protocol) | REG-001, REG-003, REG-009, INT-REG-001 |
| SPEC-02 (Registry Core: lease lifecycle, eviction, discovery) | REG-001, REG-002, REG-007, REG-008, INT-REG-001, INT-REG-003 |
| SPEC-03 (Standalone Posture e Fail-Open Discovery) | REG-002, REG-006, REG-010 |
| SPEC-04 (Data Model / No Schema) | _(N/A -- sem schema, sem migrations)_ |
| SPEC-05 (Idempotency / Concurrency) | REG-008 |
| SPEC-06 (Security / Surface Interna) | REG-004, REG-010 |
| SPEC-07 (Observability / Health) | REG-003, REG-005, REG-006, REG-010, INT-REG-002 |
| SPEC-08 (BDD Test Scenarios) | REG-007, REG-008 |
| SPEC-09 (Messaging / N/A) | _(N/A -- REG nao e participante de broker)_ |
| SPEC-10 (Integrations) | INT-REG-001, INT-REG-002, INT-REG-003 |

## Por prioridade

| Prioridade | Issues |
|---|---|
| P1 | REG-001, REG-002, REG-003 |
| P2 | REG-004, REG-005, REG-006, INT-REG-001 |
| P3 | REG-007, REG-008, REG-009, INT-REG-002 |
| P4 | REG-010, INT-REG-003 |

## Por camada / componente

| Componente | Issues |
|---|---|
| Scaffold / Gradle / Bootstrap | REG-001 |
| Configuracao declarativa (YAML) | REG-002 |
| Health / Readiness | REG-003, REG-006 |
| Security (surface interna, sem auth) | REG-004 |
| Observabilidade / Metricas | REG-005, REG-006 |
| Testes unitarios / Spring Boot Test | REG-007 |
| BDD Cucumber | REG-008 |
| Docker Compose / Infra local | REG-009 |
| Hardening / Producao / Runbook | REG-010 |
| Integracao: clientes in-fleet | INT-REG-001, INT-REG-003 |
| Integracao: observabilidade | INT-REG-002 |

## Totais

- Issues funcionais: 10 (REG-001..REG-010)
- Issues de integracao: 3 (INT-REG-001..INT-REG-003)
- Total: 13
