# Plano — GWY (API Gateway)

## Status das prioridades
- P1 — pendente
- P2 — pendente
- P3 — pendente
- P4 — pendente
- P5 — pendente
- P6 — pendente
- P7 — pendente
- P8 — pendente
- P9 — pendente

## 0. Leitura executiva

### Papel do servico (extraido de 01-architectural-vision.md)
GWY e a **unica porta de ingresso** publica do backend NeuroAssist AI. Autentica o
chamador (cuidador via JWT, agente edge via credencial de dispositivo), resolve o
servico-alvo via Service Registry (Eureka), extrai e propaga o contexto de `empresa`
(`X-Empresa-Id`), aplica protecoes de borda (rate limiting, CORS) e blinda os servicos
internos da rede publica. E um servico **de infraestrutura**: nao carrega dominio de
negocio nem persiste estado de negocio (SPEC-04).

### MVP scope (extraido de 01)
1. Roteamento dinamico via Eureka (`lb://<service>`, sem host fixo).
2. Autenticacao na borda (JWT de cuidador + credencial de dispositivo edge).
3. Propagacao de tenant (`X-Empresa-Id` derivado do principal, nunca do cliente).
4. Propagacao de `X-Correlation-Id` (honrar/gerar/ecoar).
5. Rate limiting por rota (edge-ingest, login).
6. Politica CORS para a origem do dashboard.
7. Erros tipados RFC 7807 (`application/problem+json`).

### Diagnostico inicial (gaps mapeados em 06)
- **G-1 (critico):** esquema de credencial de dispositivo ainda **proposta** (ADR-005) —
  toda a logica de validacao de dispositivo deve ficar isolada atras de um port
  trocavel (`DeviceCredentialValidator`), ver GWY-010/INT-GWY-003.
- **G-2:** split coarse/fine de autorizacao — apenas o coarse (por rota) entra no MVP
  do gateway (GWY-013).
- **G-3:** valores concretos de rate-limit ainda nao quantificados — a issue GWY-016
  implementa o mecanismo; os valores exatos por rota sao um TODO de configuracao, nao
  bloqueiam a implementacao do filtro.
- **G-4:** ponto de terminacao TLS e decisao de infra, fora do escopo deste backlog.
- **R-3 aceito:** rate limit in-memory e por replica (limite efetivo N× em N replicas);
  documentado, nao corrigido no MVP.

## 1. Estrategia geral

### Objetivo macro
Entregar o gateway como o unico ingresso publico, com pipeline de filtros normativa
(SPEC-02) completa, autenticacao dupla (cuidador + dispositivo), propagacao de tenant e
correlacao, rate limiting, CORS, error model RFC 7807, observabilidade e cobertura BDD —
sem nenhuma dependencia de persistencia ou mensageria (SPEC-04/SPEC-09).

### Ordem das prioridades

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + contratos minimos | base de compilacao/execucao; nada mais funciona sem isso |
| P2 | Orquestracao do dominio (filter pipeline) | golden path de correlacao + propagacao de tenant, core do gateway (SPEC-02) |
| P3 | Persistencia (ausencia) | guard-rail explicito antes de qualquer integracao externa poder introduzir JDBC transitivo |
| P4 | Integracoes externas | autenticacao (JWK, JWT, device), discovery (Eureka) — pre-requisito de seguranca (P5) |
| P5 | Security + multi-tenancy | CORS + autorizacao coarse, fecha o modelo de acesso |
| P6 | Messaging (ausencia) | guard-rail explicito, baixo risco/baixo esforco |
| P7 | Observability + health | rate-limiter, logging estruturado e endpoints operacionais — depende da pipeline (P2) e das integracoes (P4) |
| P8 | BDD end-to-end | valida o comportamento fim-a-fim ja implementado (P1-P7) |
| P9 | Hardening | retry policy — refinamento final sobre a integracao de roteamento (P4) |

## 2. Plano por prioridade

### Prioridade 1 — Scaffold + contratos minimos
Status: pendente

#### Objetivo / Resultado esperado
Servico Spring Cloud Gateway reativo publicavel, sem persistencia, com rotas e error
model minimos configurados.

#### Issues vinculadas
- GWY-001, GWY-002, GWY-003

#### Specs cobertas
- SPEC-01, SPEC-04

#### Sequencia de execucao
- Fase 1: bootstrap do servico (GWY-001)
- Fase 2: tabela de rotas (GWY-002)
- Fase 3: error model RFC 7807 (GWY-003)

#### Criterios de aceite
- Servico sobe e responde `/actuator/health`.
- Todas as 9 rotas de `api-contract.md` §3 roteiam corretamente.
- Erros seguem `application/problem+json` com `correlationId`.

#### Riscos
- R-5 (reactive/servlet skill split) — atencao a nao introduzir chamada bloqueante.

#### Instrucao para implementador
Seguir `api-contract.md` §3/§5 literalmente para nomes de rota e tipos de erro; nao
inventar rotas ou status alem da tabela.

---

### Prioridade 2 — Orquestracao do dominio (filter pipeline)
Status: pendente

#### Objetivo / Resultado esperado
Pipeline de filtros na ordem normativa (SPEC-02) com correlacao e propagacao de tenant
funcionando de ponta a ponta.

#### Issues vinculadas
- GWY-004, GWY-005, GWY-006

#### Specs cobertas
- SPEC-02, SPEC-06, SPEC-07

#### Sequencia de execucao
- Fase 1: CorrelationIdFilter (GWY-004)
- Fase 2: ordenacao explicita dos filtros (GWY-005)
- Fase 3: TenantContextPropagationFilter (GWY-006, depende de auth — P4)

#### Criterios de aceite
- Ordem `CORS -> CorrelationId -> Authentication -> TenantContextPropagation -> RateLimiter -> Route -> ProblemDetailError` verificavel.
- `X-Empresa-Id` nunca vem do cliente (INV-2).

#### Riscos
- R-2 (tenant spoofing) — mitigado por esta prioridade; cobrir com teste de seguranca.

#### Instrucao para implementador
GWY-006 so pode ser finalizada apos GWY-009/GWY-010 (autenticacao) existirem, pois
depende do `GatewayPrincipal` resolvido. Implementar o filtro e possivel antes; o teste
de integracao completo depende de P4.

---

### Prioridade 3 — Persistencia (ausencia, guard-rail)
Status: pendente

#### Objetivo / Resultado esperado
Confirmacao explicita e testada de que o gateway nao abre conexao de banco.

#### Issues vinculadas
- GWY-007

#### Specs cobertas
- SPEC-04

#### Sequencia de execucao
- Fase unica: exclusao de auto-config + smoke test.

#### Criterios de aceite
- Smoke test falha se um bean `DataSource` existir.

#### Riscos
- Nenhum risco material; issue de baixo esforco.

#### Instrucao para implementador
Rodar `be-migration-generator.py` para confirmar zero migracoes contribuidas por este
servico (nao ha blocos `sql` em `data-model.md`).

---

### Prioridade 4 — Integracoes externas
Status: pendente

#### Objetivo / Resultado esperado
Autenticacao de cuidador e de dispositivo funcionando contra o JWK do ACS, com discovery
Eureka wireado e health indicators de prontidao.

#### Issues vinculadas
- GWY-008, GWY-009, GWY-010, GWY-011
- INT-GWY-001, INT-GWY-002, INT-GWY-003

#### Specs cobertas
- SPEC-03, SPEC-06, SPEC-10

#### Sequencia de execucao
- Fase 1: JwkClient com cache (GWY-008)
- Fase 2: JwtAuthenticationFilter — cuidador (GWY-009)
- Fase 3: DeviceCredentialValidator + filtro — dispositivo (GWY-010)
- Fase 4: Eureka client + health indicator (GWY-011)
- Fase 5: validacao de integracao fim-a-fim (INT-GWY-001/002/003)

#### Criterios de aceite
- JWT valido de cuidador autentica; token de dispositivo valido autentica; falhas
  resultam em `401` fail-closed.
- Readiness `DOWN` sem registro no Eureka ou com JWK indisponivel + cache frio.

#### Riscos
- G-1 (esquema de credencial de dispositivo ainda proposta) — mitigado pelo port
  trocavel `DeviceCredentialValidator` (INT-GWY-003 testa exatamente essa trocabilidade).
- R-4 (acoplamento de disponibilidade do JWK) — mitigado por cache + stale-while-revalidate.

#### Instrucao para implementador
Nao implementar o esquema final de credencial de dispositivo como definitivo — ele e
"proposta" (ADR-005). Isolar toda logica atras do port; qualquer decisao futura (mTLS,
API key) deve trocar apenas a implementacao.

---

### Prioridade 5 — Security + multi-tenancy
Status: pendente

#### Objetivo / Resultado esperado
CORS restrito a origens configuradas e autorizacao coarse por rota (kind de principal
exigido).

#### Issues vinculadas
- GWY-012, GWY-013

#### Specs cobertas
- SPEC-01 §1/§6, SPEC-06

#### Sequencia de execucao
- Fase 1: CorsConfig (GWY-012)
- Fase 2: SecurityConfig com matchers por rota (GWY-013, depende de P4)

#### Criterios de aceite
- Pre-flight respondido no gateway sem contatar servico.
- Rota com `kind` incompativel responde `403`.

#### Riscos
- G-2 (split coarse/fine) — apenas coarse entra aqui; documentar a fronteira para os
  servicos de dominio.

#### Instrucao para implementador
Usar a coluna "Auth" da tabela §3 do `api-contract.md` como fonte literal do `kind`
exigido por rota.

---

### Prioridade 6 — Messaging (ausencia, guard-rail)
Status: pendente

#### Objetivo / Resultado esperado
Confirmacao de que nenhum client de broker existe no build; back-pressure sincrona
documentada.

#### Issues vinculadas
- GWY-014

#### Specs cobertas
- SPEC-09

#### Sequencia de execucao
- Fase unica.

#### Criterios de aceite
- Smoke test de build sem dependencia de broker.

#### Riscos
- Nenhum risco material.

#### Instrucao para implementador
Nao introduzir nenhuma dependencia `spring-*-stream`/`spring-*-amqp`/Kafka mesmo que
transitiva.

---

### Prioridade 7 — Observability + health
Status: pendente

#### Objetivo / Resultado esperado
Rate limiting funcional (429/Retry-After + mapeamento 502/504), logging estruturado sem
PII, e endpoints operacionais completos.

#### Issues vinculadas
- GWY-015, GWY-016, GWY-017
- INT-GWY-004, INT-GWY-005

#### Specs cobertas
- SPEC-01 §4, SPEC-05, SPEC-07, SPEC-10

#### Sequencia de execucao
- Fase 1: StructuredAccessLogger (GWY-015)
- Fase 2: RateLimiterFilter (GWY-016)
- Fase 3: actuator completo + acsJwk health indicator (GWY-017)
- Fase 4: validacao de integracao de roteamento e de health probe (INT-GWY-004/005)

#### Criterios de aceite
- `request.routed`/`request.rejected` emitidos sem corpo/PII (LGPD, R-6).
- `429` com `Retry-After`; `502`/`504` mapeados corretamente.
- `/actuator/health` publico; `/actuator/health/readiness` composto (Eureka + JWK).

#### Riscos
- R-6 (LGPD em logs) — mitigado por contrato explicito do `StructuredAccessLogger`.
- R-3 (rate limit por replica) — aceito, apenas documentado.

#### Instrucao para implementador
Nao logar corpo de request/response em nenhuma circunstancia, mesmo em debug.

---

### Prioridade 8 — BDD end-to-end
Status: pendente

#### Objetivo / Resultado esperado
As 3 features Cucumber de SPEC-08 implementadas e passando, cobrindo os invariantes
INV-1..INV-5.

#### Issues vinculadas
- GWY-018, GWY-019, GWY-020

#### Specs cobertas
- SPEC-08

#### Sequencia de execucao
- Fase 1: routing-and-authentication.feature (GWY-018, depende de P1/P4)
- Fase 2: tenant-and-correlation-propagation.feature (GWY-019, depende de P2)
- Fase 3: rate-limiting-and-cors.feature (GWY-020, depende de P5/P7)

#### Criterios de aceite
- Todos os cenarios passam com WireMock/discovery estatico, sem container de banco/broker.

#### Riscos
- Nenhum risco material; depende apenas da conclusao das prioridades anteriores.

#### Instrucao para implementador
Usar exatamente os dados de exemplo de SPEC-08 (`emp_familia_amaral`,
`emp_clinica_norte`, bucket `edge-ingest` 2 req/s) para determinismo.

---

### Prioridade 9 — Hardening
Status: pendente

#### Objetivo / Resultado esperado
Politica de retry segura (GET-only) implementada e validada.

#### Issues vinculadas
- GWY-021

#### Specs cobertas
- SPEC-05

#### Sequencia de execucao
- Fase unica.

#### Criterios de aceite
- Retry automatico apenas em `GET` com erro de conexao; nunca em metodo nao-idempotente.

#### Riscos
- Nenhum risco material.

#### Instrucao para implementador
Confirmar que a politica de retry esta configurada por rota, nao globalmente, para
permitir excecoes futuras sem regressao.

## 3. Dependencias externas
- Service Registry (REG/Eureka) — dependencia hard de runtime (routing + readiness).
- ACS (Cuidadores e Acesso) — JWK/issuer para JWT de cuidador e autoridade de
  credencial de dispositivo (com EDGE).
- Load balancer externo / DNS (infra) — fronteia as replicas do gateway.

## 4. Restricoes arquiteturais
- ArchUnit RULE-01..08 (CLAUDE.md do projeto).
- Camadas hexagonais (api/application/domain/infrastructure) — adaptadas: pipeline de
  filtros e a "borda"; ports de saida sao `JwkClient`, discovery e `RateLimitStore`
  (SPEC-02).
- `@Transactional` nao se aplica — o gateway nao possui camada de persistencia (SPEC-04).
- Nenhuma chamada bloqueante em filtro reativo (R-5).

## 5. Referencias
- docs/architecture/backend/services/neuroassist-ai-api-gateway/contracts/api-contract.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/contracts/data-model.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/contracts/event-contracts.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/contracts/hub-integration.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/contracts/integration-contracts.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/spec/backend/SPEC-01..10
- docs/architecture/backend/services/neuroassist-ai-api-gateway/events/event-catalog.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/01-architectural-vision.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/06-risks-gaps-assumptions.md
- docs/architecture/backend/services/neuroassist-ai-api-gateway/generator-config.yml
