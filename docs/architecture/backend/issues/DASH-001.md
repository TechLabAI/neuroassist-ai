# DASH-001 -- Create dash-service Gradle subproject (Spring Boot, no-DB, hexagonal skeleton)

**Status:** DONE -- implementado 2026-08-20
**Maturidade:** 100 -- Maduro
**Done/Total:** 10/10 itens

## Contexto

`dash-service` e o BFF (Backend-for-Frontend) do painel do cuidador na NeuroAssist AI.
Opera na porta 8090, sem banco de dados proprio no MVP (ADR-004: zero tabelas DASH).
Toda persistencia de dominio vive nos servicos donos (ACS, PAC, EVNT, HIST, EDGE).
O servico precisa de um esqueleto Spring Boot com layout hexagonal (api / application /
domain / infrastructure) e sem nenhuma dependencia JPA, Flyway ou datasource.

## Spec de referencia

- SPEC-01 (superficie REST + SSE)
- SPEC-04 (data model e postura de persistencia: zero tabelas MVP)

## Escopo

- [x] Modulo Gradle :services:dash-service incluido em backend/settings.gradle.kts (include presente e verificado)
- [x] Estrutura de pacotes com.neuroassist.dash api, application, domain, infrastructure
- [x] Dependencias Spring Boot Web, Actuator, OAuth2 Resource Server, Eureka Client, Feign declaradas em `backend/services/dash-service/build.gradle.kts`. Sem JPA, sem datasource, sem Flyway.
- [x] Configuracao em `backend/services/dash-service/src/main/resources/application.yml` com server.port=8090, Eureka, JWK-set-uri, timeouts e URLs dos upstreams.
- [x] Classe principal em `backend/services/dash-service/src/main/java/com/neuroassist/dash/DashApplication.java` com @SpringBootApplication

## Resultado esperado

`:dash-service:bootRun` inicia e responde `200` em `/actuator/health` sem conexao de banco.

## Criterios de aceite

- [x] Gradle :services:dash-service:build passa sem erros (BUILD SUCCESSFUL verificado)
- [x] /actuator/health retorna 200 UP com Eureka em modo prefer-ip-address
- [x] Nenhuma dependencia spring-boot-starter-data-jpa, flyway-core ou spring-datasource no classpath (ADR-004 compliant)
- [x] Pacote root com.neuroassist.dash com subpacotes api, application, domain, infrastructure presentes e compilando
- [x] Variaveis de ambiente documentadas em `backend/services/dash-service/src/main/resources/application.yml` com ${ENV_VAR:default}

## Dependencias

Nenhuma

## Estimativa

M

## Prioridade

P1
