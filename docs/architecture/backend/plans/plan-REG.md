# Plano -- REG (neuroassist-ai-service-registry)

## Status das prioridades
- P1 -- pendente
- P2 -- pendente
- P3 -- pendente
- P4 -- pendente

## 0. Leitura executiva

### Papel do servico (extraido de 01-architectural-vision.md)
REG e o Service Registry da NeuroAssist AI: um servidor Netflix Eureka standalone
que da a todos os demais servicos backend (GEST, EVNT, PAC, ACS, HIST, DASH, EDGE,
MIG) e ao API Gateway (GWY) um lugar unico e autoritativo para se registrar e
resolver peers por nome logico. E infraestrutura pura -- nenhum dominio de
negocio, nenhuma persistencia, nenhum broker.

### MVP scope (extraido de 01)
- Servidor Eureka standalone (`spring-cloud-starter-netflix-eureka-server`).
- Registro, renovacao de lease (heartbeat) e eviction por expiracao.
- Dashboard Eureka na rede interna para operadores.
- Actuator `/health` para liveness/readiness do proprio registry.
- Fora de escopo: cluster HA peer-replicado, qualquer store persistente,
  logica de discovery customizada, dados de negocio/LGPD, auth no dashboard
  alem do isolamento de rede.

### Diagnostico inicial (gaps mapeados em 06)
Nenhum codigo existe ainda -- este e o backlog inicial. Riscos conhecidos a
observar durante a implementacao (06-risks-gaps-assumptions.md §Riscos): single
point of failure do node unico (mitigado por cache client-side), falsos
positivos de self-preservation em frota pequena, dashboard sem autenticacao
(mitigado por isolamento de rede), risco de version drift Spring Cloud x Spring
Boot.

## 1. Estrategia geral

### Objetivo macro
Entregar um servidor Eureka standalone, observavel e operavel, que sirva de
alicerce de discovery para toda a plataforma antes que qualquer outro modulo
backend possa registrar-se nele em ambiente local ou de compose.

### Ordem das prioridades (tabela)

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + configuracao core + readiness | REG precisa estar de pe e saudavel antes de qualquer outro servico poder registrar-se |
| P2 | Seguranca + observabilidade + sinal de operador | Superficie interna precisa estar protegida e observavel antes do rollout de clientes |
| P3 | Testes + BDD + infra local (compose) | Cobertura de regressao e ambiente reproduzivel antes de abrir para a frota completa |
| P4 | Hardening + validacao da frota completa | Runbook de producao e validacao de integracao com todos os 9 clientes Eureka |

## 2. Plano por prioridade

### Prioridade 1 -- Scaffold, configuracao core e readiness
Status: pendente

#### Objetivo / Resultado esperado
REG compila, sobe na porta 8761, expoe dashboard e Actuator, e reporta
readiness apenas quando efetivamente pronto para aceitar registros.

#### Issues vinculadas
- REG-001, REG-002, REG-003

#### Specs cobertas
- SPEC-01, SPEC-02, SPEC-07 (readiness)

#### Sequencia de execucao
- Fase 1: REG-001 (modulo Gradle + bootstrap `@EnableEurekaServer`)
- Fase 2: REG-002 (application.yml completo: lease lifecycle, self-preservation, single-node flags)
- Fase 3: REG-003 (RegistryReadinessHealthIndicator no grupo readiness)

#### Criterios de aceite
- `./gradlew :neuroassist-ai-service-registry:build` compila sem erros
- `GET /actuator/health` e `GET /actuator/health/readiness` respondem corretamente
- Dashboard Eureka em `http://localhost:8761/` renderiza

#### Riscos
- Version drift Spring Cloud x Spring Boot 3.5.x (pin do release train obrigatorio)

#### Instrucao para implementador
Nao escrever codigo de negocio. Comportamento vem de configuracao
(`application.yml`) + starter Eureka. Seguir o layout de pacotes minimo de
`04-backend-architecture-direction.md` (`com.neuroassist.serviceregistry`).

### Prioridade 2 -- Seguranca, observabilidade e sinal de operador
Status: pendente

#### Objetivo / Resultado esperado
Superficie de registro/discovery confirmadamente interna (sem auth, sem leitura
de tenant); logs estruturados de ciclo de vida e metricas Prometheus expostos;
self-preservation visivel como sinal de operador.

#### Issues vinculadas
- REG-004, REG-005, REG-006, INT-REG-001

#### Specs cobertas
- SPEC-03, SPEC-06, SPEC-07

#### Sequencia de execucao
- Fase 1: REG-004 (security -- sem auth, sem tenant context)
- Fase 2: REG-005 (logs estruturados + metricas)
- Fase 3: REG-006 (liveness/readiness/self-preservation como grupos Actuator distintos)
- Fase 4: INT-REG-001 (validacao com GWY, PAC, EVNT)

#### Criterios de aceite
- `GET /eureka/apps` e `GET /` (dashboard) respondem 200 sem `Authorization`
- `GET /actuator/prometheus` expoe `eureka_registered_apps_count` e `eureka_self_preservation_active`
- GWY resolve `lb://PAC-SERVICE` e `lb://EVNT-SERVICE` via REG

#### Riscos
- Self-preservation false positives em frota pequena (calibrar threshold com cuidado)

#### Instrucao para implementador
Nenhum filtro deve ler ou propagar `X-Empresa-Id`. Logs nunca contem dado
pessoal de paciente/cuidador -- apenas metadados operacionais (nome do
servico, host, porta, timestamps de lease).

### Prioridade 3 -- Testes, BDD e infraestrutura local
Status: pendente

#### Objetivo / Resultado esperado
Cobertura de testes de bootstrap/config, cenarios Gherkin executaveis para as
tres areas funcionais (registro, eviction/self-preservation, discovery), e
REG disponivel via Docker Compose com healthcheck confiavel para os demais
servicos dependerem.

#### Issues vinculadas
- REG-007, REG-008, REG-009, INT-REG-002

#### Specs cobertas
- SPEC-05, SPEC-08

#### Sequencia de execucao
- Fase 1: REG-007 (SpringBootTest de bootstrap/config)
- Fase 2: REG-008 (step definitions ligados as features ja geradas em `tests/cucumber/`)
- Fase 3: REG-009 (Docker Compose + Dockerfile + healthcheck)
- Fase 4: INT-REG-002 (validacao end-to-end da observabilidade)

#### Criterios de aceite
- `./gradlew :neuroassist-ai-service-registry:test` e `:cucumber` passam
- `docker compose up neuroassist-ai-service-registry` fica `healthy`
- Pipeline de observabilidade indisponivel nao bloqueia registro/discovery

#### Riscos
- Step definitions duplicando cobertura ja feita em testes unitarios (evitar)

#### Instrucao para implementador
Reutilizar os steps catalogados em SPEC-08 §Steps reutilizaveis; nao inventar
vocabulario Gherkin novo sem necessidade.

### Prioridade 4 -- Hardening e validacao da frota completa
Status: pendente

#### Objetivo / Resultado esperado
Perfil de producao calibrado para a frota real (9 servicos), runbook de
operacao documentado, e confirmacao de que todos os 9 clientes Eureka
registram-se corretamente.

#### Issues vinculadas
- REG-010, INT-REG-003

#### Specs cobertas
- SPEC-03, SPEC-06, SPEC-07, SPEC-10

#### Sequencia de execucao
- Fase 1: REG-010 (application-prod.yml + runbook.md)
- Fase 2: INT-REG-003 (validacao da frota completa -- bloqueada ate os demais scaffolds existirem)

#### Criterios de aceite
- Runbook cobre membership check, override de status e diagnostico de self-preservation
- `GET /eureka/apps` retorna os 9 logical ids apos todos os scaffolds existirem

#### Riscos
- INT-REG-003 fica bloqueada ate GEST/EVNT/PAC/ACS/HIST/DASH/EDGE/GWY/MIG terem scaffold proprio

#### Instrucao para implementador
Se os demais scaffolds ainda nao existirem quando esta prioridade for
alcancada, marcar INT-REG-003 como DEFERIDO no plano (nao bloquear REG-010).

## 3. Dependencias externas
Nenhuma. REG nao depende de nenhum outro servico para subir (e o alvo de
dependencia da plataforma, nao um dependente).

## 4. Restricoes arquiteturais
- ArchUnit RULE-01..08 (CLAUDE.md) -- **nao se aplicam ao dominio de negocio**:
  REG nao possui camadas hexagonais (api/application/domain/infrastructure);
  ver `02-architecture-blueprint.md` §Internal Boundaries.
- Sem `@Transactional` -- REG nao possui persistencia.
- Nenhum controller de negocio -- a superficie e o protocolo Eureka padrao.

## 5. Referencias
- docs/architecture/backend/services/neuroassist-ai-service-registry/_brief.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/01-architectural-vision.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/02-architecture-blueprint.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/06-risks-gaps-assumptions.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/adr/ADR-001-eureka-as-service-registry.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/adr/ADR-002-standalone-vs-ha-cluster.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/neuroassist-ai-service-registry-spec.md
- docs/architecture/backend/services/neuroassist-ai-service-registry/spec/backend/SPEC-01..10.md
- docs/architecture/backend/issues/REG-001..010.md, INT-REG-001..003.md, issues_map-REG.md
