# Plano -- MIG (neuroassist-ai-migrations)

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

## 0. Leitura executiva

### Papel do servico (extraido de 01-architectural-vision.md)
MIG e o unico dono do ciclo de vida do schema da NeuroAssist AI: um servico
Spring Boot Flyway que centraliza Flyway sobre o schema global
(`neuroassist_global`) e todo schema de tenant (`t_<tenantId>`, um por
empresa). Nenhum servico de negocio migra o proprio schema. E infraestrutura
pura -- sem UI, sem agregados de dominio, sem broker.

### MVP scope (extraido de 01)
- Servico Flyway centralizado dono de **todos** os schemas (global + tenant).
- Baseline versionado do schema global (`neuroassist_global`).
- Template canonico de tenant aplicado identicamente a cada empresa.
- Provisionamento de tenant deterministico e idempotente (criar schema +
  aplicar historico completo), invocado sincronamente por ACS.
- Modos migrate-on-startup e on-demand; fail-fast em checksum drift.
- Status de migracao legivel por operador (Flyway `info`).
- Fora de escopo: logica de dominio, API REST publica, deprovisioning
  automatizado, zero-downtime/online schema change, sharding.

### Diagnostico inicial (gaps mapeados em 06)
Nenhum codigo existe ainda -- este e o backlog inicial. Gaps conhecidos a
observar durante a implementacao (06-risks-gaps-assumptions.md): G4
(contrato exato do gatilho de ACS) foi resolvido nas specs (SPEC-03,
api-contract §1.1); G1 (deprovisioning) permanece fora do MVP por decisao
(guardrail contra DDL destrutiva automatica); G5 (fronteira de
purge/anonimizacao LGPD com HIST) fica para quando HIST for especificado.
Riscos a mitigar durante a implementacao: R1 (DDL destrutiva -- mitigada por
nunca gerar drop/rename automatico), R2 (fan-out linear -- mitigada por
isolamento por schema + fail-fast por schema), R3 (privilegio elevado --
role dedicada), R4 (drift silencioso -- validateOnMigrate), R5 (concorrencia
-- advisory lock).

## 1. Estrategia geral

### Objetivo macro
Entregar um runner Flyway centralizado, sincrono e fail-fast que garanta que
todo schema da plataforma (global + cada tenant) convirja de forma
determinística e auditavel, e que sirva de gate obrigatorio do pipeline de
deploy antes que qualquer servico de negocio suba.

### Ordem das prioridades (tabela: P | Titulo | Motivo)

| P | Titulo | Motivo |
|---|---|---|
| P1 | API surface (job dispatch + HTTP interno) | Sem o dispatcher de `--mode` e a face HTTP nada mais pode ser invocado |
| P2 | Orquestracao de migracao (golden path: Schema migrado) | O evento-ancora da doc 01/_brief -- ordenacao global->tenant + fan-out + run report |
| P3 | Data model + migrations (Flyway locations) | Baseline global + tenant-template sao pre-requisito de qualquer fan-out real |
| P4 | Provisionamento de tenant (golden path: Empresa provisionada) | Segundo evento-ancora -- depende de P2/P3 existirem |
| P5 | Idempotencia + concorrencia | Correcao estrutural (re-run, advisory lock, checksum drift) antes de qualquer uso em producao |
| P6 | Security | Isolamento de privilegio e escopos OAuth2 antes de expor a API interna a ACS |
| P7 | Observability + health | Correlation id, trilha de atividade (D-423) e readiness |
| P8 | BDD end-to-end | Fecha a cobertura Gherkin ja gerada em `tests/cucumber/` |
| P9 | Hardening (guardrail de mensageria) | Confirma e trava a decisao "sem broker" (SPEC-09) |

## 2. Plano por prioridade

### Prioridade 1 -- API surface (job dispatch + HTTP interno)
Status: pendente

#### Objetivo / Resultado esperado
MIG aceita exatamente um `--mode` por invocacao com exit codes estaveis, e
expoe a face HTTP interna (provisioning, status, run) protegida por escopo
OAuth2, sem nenhuma rota publica ou via GWY.

#### Issues vinculadas
- MIG-001, MIG-002, MIG-003

#### Specs cobertas
- SPEC-01

#### Sequencia de execucao
- Fase 1: MIG-001 (dispatcher `--mode`, exit codes 0/1/2/3)
- Fase 2: MIG-002 (`InternalMigrationsController` + escopos OAuth2)
- Fase 3: MIG-003 (`ProblemDetailMapper` RFC 7807)

#### Criterios de aceite
- Invocacao sem `--mode` sai com codigo != 0 e lista os modos validos
- Nenhum endpoint de migracao e alcancavel via `/api/**` nem via GWY
- Toda resposta de erro e `application/problem+json` com `code` estavel

#### Riscos
- Confundir a face HTTP interna com uma API de negocio (mitigacao: revisao
  contra api-contract.md §5 "O que a MIG NAO expoe")

#### Instrucao para implementador
Nao criar nenhum recurso REST de dominio. A face HTTP e estritamente
provisioning/status/run; toda logica de orquestracao vive em `application`
(Prioridade 2), nao no controller.

### Prioridade 2 -- Orquestracao de migracao (golden path: Schema migrado)
Status: pendente

#### Objetivo / Resultado esperado
`migrate-all` migra o schema global antes de qualquer schema de tenant, faz
fan-out sobre o registry de tenants, e emite um run report cujo `exitCode`
reflete o pior resultado por schema.

#### Issues vinculadas
- MIG-004, MIG-005, MIG-006

#### Specs cobertas
- SPEC-02

#### Sequencia de execucao
- Fase 1: MIG-004 (`MigrationPlan` + ordenacao global->tenant)
- Fase 2: MIG-005 (fan-out via `TenantRegistryReader`)
- Fase 3: MIG-006 (run report + mapeamento de exit code)

#### Criterios de aceite
- Schema global sempre migrado antes de qualquer schema de tenant na mesma execucao
- Um registry vazio ainda migra o global e sai 0
- Falha em um tenant nao interrompe a tentativa dos demais; exit code final = 2

#### Riscos
- Fan-out linear com o numero de empresas gerar janelas de execucao longas
  (R2) -- aceito no MVP; revisitar paralelismo se o numero de empresas crescer

#### Instrucao para implementador
`domain` (`SchemaTarget`, `MigrationPlan`, `RunReport`) deve ser puro -- sem
Spring nem JDBC -- para permitir teste unitario de ordenacao isolado da
infraestrutura.

### Prioridade 3 -- Data model + migrations (Flyway locations)
Status: pendente

#### Objetivo / Resultado esperado
As duas localizacoes Flyway (`global/`, `empresa/`) existem com a baseline
real: extensao + tabela `tenant` no global; paciente/cuidador/tipo_gesto/
comando/evento/dispositivo_edge no tenant-template, identicos para
`familia` e `clinica`.

#### Issues vinculadas
- MIG-007, MIG-008, MIG-009

#### Specs cobertas
- SPEC-04

#### Sequencia de execucao
- Fase 1: MIG-007 (baseline global: extensao + `tenant` registry)
- Fase 2: MIG-008 (baseline do tenant-template)
- Fase 3: MIG-009 (`SchemaResolver` + `FlywayAdapter` parametrizado)

#### Criterios de aceite
- `db/migration/global/` aplica-se apenas a `neuroassist_global`
- `db/migration/empresa/` aplica-se apenas a `t_<tenantId>`
- `tenant.schema_name` e unico; colunas enum-like usam `CHECK`

#### Riscos
- Divergencia futura entre `familia` e `clinica` no template (A3, doc 06) --
  a validar com PAC/ACS quando especificados; MIG mantem o template unico
  ate entao

#### Instrucao para implementador
Nao inventar tabelas alem das listadas em data-model.md §3. Uma vez que
PAC/ACS/HIST/EVNT/EDGE tenham suas proprias specs, suas migracoes futuras
substituem/estendem esta baseline (ADR-002) -- nao sao autoradas aqui de
novo.

### Prioridade 4 -- Provisionamento de tenant (golden path: Empresa provisionada)
Status: pendente

#### Objetivo / Resultado esperado
ACS provisiona um novo tenant de forma sincrona, idempotente e
deterministica: schema criado (se ausente) e migrado ate o head antes da
resposta, com o global garantidamente em head primeiro.

#### Issues vinculadas
- MIG-010, MIG-011

#### Specs cobertas
- SPEC-03

#### Sequencia de execucao
- Fase 1: MIG-010 (`ProvisionTenantSchema` use case)
- Fase 2: MIG-011 (idempotencia de re-provisionamento)

#### Criterios de aceite
- Provisionar um `tenantId` valido cria+migra o schema ate o head antes de responder
- Reinvocar para um tenant ja corrente e no-op sem DDL
- `tenantId` fora do registry e rejeitado com `invalid-tenant-id` sem DDL

#### Riscos
- Chamada sincrona acopla a latencia do onboarding ao tempo de migracao do
  template (aceito no MVP; revisitar broker se volume crescer -- doc 06 A4)

#### Instrucao para implementador
Depende de MIG-005 (leitura do registry) e MIG-009 (Flyway parametrizado)
ja existirem. Reusar o mesmo `MigrationPlan`/`FlywayAdapter` da Prioridade 2
-- nao duplicar logica de aplicacao de migracao.

### Prioridade 5 -- Idempotencia e concorrencia
Status: pendente

#### Objetivo / Resultado esperado
Re-executar `migrate-all` num schema ja no head nao tem efeito; duas
execucoes concorrentes sobre o mesmo schema sao serializadas por advisory
lock; um checksum divergente aborta sem auto-repair.

#### Issues vinculadas
- MIG-012, MIG-013

#### Specs cobertas
- SPEC-05

#### Sequencia de execucao
- Fase 1: MIG-012 (advisory lock por schema)
- Fase 2: MIG-013 (checksum drift fail-fast)

#### Criterios de aceite
- Segunda execucao concorrente sobre o mesmo schema aguarda o lock da primeira
- Checksum divergente aborta apenas aquele schema, sem auto-repair
- Re-run apos convergencia aplica zero migracoes novas e sai 0

#### Riscos
- Lock mal liberado em caso de crash (mitigacao: `MigrationLock` deve
  liberar mesmo em caminho de excecao -- try/finally)

#### Instrucao para implementador
Cobrir explicitamente o caso de crash-mid-apply nos testes (MIG-019): uma
linha de historico `success=false` deve bloquear reaplicacao ate reparo
manual, nunca auto-reparar.

### Prioridade 6 -- Security
Status: pendente

#### Objetivo / Resultado esperado
A role de DDL da MIG e isolada das roles DML dos servicos de negocio; toda
rota interna exige escopo OAuth2 valido (`migrations:internal` ou
`migrations:admin`); nada e alcancavel por token de usuario final.

#### Issues vinculadas
- MIG-014, MIG-015, INT-MIG-004, INT-MIG-005

#### Specs cobertas
- SPEC-06, SPEC-10

#### Sequencia de execucao
- Fase 1: MIG-014 (role privilegiada isolada)
- Fase 2: MIG-015 (enforcement de escopo OAuth2)
- Fase 3: INT-MIG-004 (registro no Eureka da face HTTP interna)
- Fase 4: INT-MIG-005 (guard de nao-dependencia do TechLab AI)

#### Criterios de aceite
- Requisicao sem escopo valido recebe 401; escopo errado (internal em rota admin) recebe 403
- Nenhuma role de servico de negocio detem `CREATE SCHEMA`/DDL cross-schema
- Build falha se uma dependencia `techlab-ai`/Feign for adicionada a este servico

#### Riscos
- Nenhum registrado alem dos ja cobertos em R3 (doc 06)

#### Instrucao para implementador
`techlab-ai-integration.md` e N/A por decisao registrada -- INT-MIG-005
existe justamente para impedir regressao silenciosa dessa decisao.

### Prioridade 7 -- Observability + health
Status: pendente

#### Objetivo / Resultado esperado
Toda execucao carrega um correlationId propagado/gerado; fatos de ciclo de
vida (schema migrado, empresa provisionada, migracao falhou) sao
registrados na trilha de atividade (D-423) em modo best-effort; a face HTTP
reporta readiness real.

#### Issues vinculadas
- MIG-016, MIG-017, MIG-018

#### Specs cobertas
- SPEC-07

#### Sequencia de execucao
- Fase 1: MIG-016 (correlation id)
- Fase 2: MIG-017 (`ActivityTrailReporter` best-effort)
- Fase 3: MIG-018 (health/readiness)

#### Criterios de aceite
- Correlation id presente em toda resposta e no registro de atividade
- Indisponibilidade do sink de atividade nunca falha uma execucao
- `/actuator/health/readiness` reflete a prontidao da face HTTP, nao o resultado do ultimo run

#### Riscos
- Acoplar acidentalmente o sucesso do run ao sucesso do registro de
  atividade (mitigacao: MIG-017 deve sempre engolir falhas do sink)

#### Instrucao para implementador
Nunca lancar excecao a partir do `ActivityTrailReporter` para dentro do
fluxo de migracao -- log em WARN e seguir.

### Prioridade 8 -- BDD end-to-end
Status: pendente

#### Objetivo / Resultado esperado
Os tres arquivos `.feature` ja gerados (`migration-orchestration`,
`tenant-provisioning`, `idempotency-and-drift`) executam contra um
PostgreSQL Testcontainers isolado, fechando a cobertura de SPEC-02/03/05.

#### Issues vinculadas
- MIG-019

#### Specs cobertas
- SPEC-08

#### Sequencia de execucao
- Fase 1: MIG-019 (step definitions + runner Cucumber + Testcontainers)

#### Criterios de aceite
- Os tres `.feature` passam contra um PostgreSQL efemero por execucao
- Nenhum cenario roda contra um banco compartilhado/persistente

#### Riscos
- Step definitions duplicando cobertura ja feita em testes unitarios (evitar)

#### Instrucao para implementador
Reutilizar o vocabulario de steps catalogado em SPEC-08 §Reusable step
vocabulary; nao inventar vocabulario Gherkin novo sem necessidade.

### Prioridade 9 -- Hardening (guardrail de mensageria)
Status: pendente

#### Objetivo / Resultado esperado
Um teste de arquitetura garante que nenhuma dependencia de broker seja
introduzida enquanto `stack.messaging.enabled=false`, tornando a decisao de
SPEC-09 auto-verificavel.

#### Issues vinculadas
- MIG-020, INT-MIG-001, INT-MIG-002, INT-MIG-003

#### Specs cobertas
- SPEC-09, SPEC-10, SPEC-02, SPEC-03

#### Sequencia de execucao
- Fase 1: MIG-020 (guardrail arquitetural de nao-mensageria)
- Fase 2: INT-MIG-001 (pre-flight check PostgreSQL -> exit 3)
- Fase 3: INT-MIG-002 (gate do pipeline de deploy)
- Fase 4: INT-MIG-003 (contract test do chamador ACS)

#### Criterios de aceite
- Build falha se Kafka/RabbitMQ/Artemis for adicionado ao classpath
- Falha de conectividade/role/registry resulta em exit 3 antes de qualquer DDL
- Pipeline de deploy interrompe o rollout de negocio quando MIG sai != 0
- Contract test cobre sucesso e erro do endpoint de provisioning consumido por ACS

#### Riscos
- INT-MIG-003 depende do contrato de ACS nao mudar antes de ACS ter sua
  propria especificacao -- se divergir, retornar a `/be-architect-specs`

#### Instrucao para implementador
Se ACS ainda nao tiver specs proprias quando esta prioridade for alcancada,
tratar INT-MIG-003 como bloqueada e documentar no plano em vez de inventar
o contrato do lado de ACS.

## 3. Dependencias externas
- **PostgreSQL** disponivel com role de DDL de alto privilegio (R3).
- **REG (Eureka)** para registro da face HTTP interna (nao bloqueia o job core).
- **ACS** como origem do gatilho de provisionamento -- contrato ja definido
  em api-contract.md §1.1, mas ACS ainda nao passou por `/be-architect-specs`;
  INT-MIG-003 pode ficar bloqueada ate la (ver Prioridade 9).

## 4. Restricoes arquiteturais
- ArchUnit RULE-01..08 (CLAUDE.md) aplicam-se de forma restrita: MIG possui
  camadas hexagonais (api/application/domain/infrastructure -- doc 04) mas
  **nao possui agregados de dominio de negocio** (ADR-002); as tabelas do
  tenant-template em data-model.md §3 sao DDL que a MIG executa, nao
  entidades JPA que a MIG possui.
- `@Transactional` nao se aplica a orquestracao Flyway (Flyway gerencia sua
  propria transacao por migracao); reservar `@Transactional` apenas se
  alguma leitura do registry via JPA vier a existir.
- Nenhuma dependencia de messaging (Kafka/RabbitMQ/Artemis) enquanto
  `stack.messaging.enabled=false` (SPEC-09, guardrail MIG-020).
- Nenhuma dependencia `techlab-ai`/Feign (`ai.hasAi=false`, guardrail INT-MIG-005).

## 5. Referencias
- docs/architecture/backend/services/neuroassist-ai-migrations/_brief.md
- docs/architecture/backend/services/neuroassist-ai-migrations/01-architectural-vision.md
- docs/architecture/backend/services/neuroassist-ai-migrations/04-backend-architecture-direction.md
- docs/architecture/backend/services/neuroassist-ai-migrations/05-persistence-model.md
- docs/architecture/backend/services/neuroassist-ai-migrations/06-risks-gaps-assumptions.md
- docs/architecture/backend/services/neuroassist-ai-migrations/adr/ADR-002-centralized-migrations.md
- docs/architecture/backend/services/neuroassist-ai-migrations/adr/ADR-005-tenant-schema-provisioning.md
- docs/architecture/backend/services/neuroassist-ai-migrations/neuroassist-ai-migrations-spec.md
- docs/architecture/backend/services/neuroassist-ai-migrations/spec/backend/SPEC-01..10.md
- docs/architecture/backend/services/neuroassist-ai-migrations/generator-config.yml
- docs/architecture/backend/issues/MIG-001..020.md, INT-MIG-001..005.md, issues_map-MIG.md
