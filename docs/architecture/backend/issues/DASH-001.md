# DASH-001 -- Create dash-service Gradle subproject (Spring Boot, no-DB, hexagonal skeleton)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

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

- [ ] Criar modulo Gradle `:dash-service` em `backend/settings.gradle.kts`
- [ ] Estrutura de pacotes: `com.neuroassist.dash.{api,application,domain,infrastructure}`
- [ ] `build.gradle.kts`: dependencias Spring Boot Web, Spring Boot Actuator, Spring Security OAuth2 Resource Server, Spring Cloud Netflix Eureka Client, Feign. Sem JPA, sem datasource, sem Flyway.
- [ ] `application.yml` com `server.port=8090`, Eureka client URL, JWK-set-uri, timeouts e URLs dos upstreams (via env vars conforme setup-local.md)
- [ ] Classe principal `DashServiceApplication` com `@SpringBootApplication`

## Resultado esperado

`:dash-service:bootRun` inicia e responde `200` em `/actuator/health` sem conexao de banco.

## Criterios de aceite

- [ ] `./gradlew :dash-service:build` passa sem erros
- [ ] `/actuator/health` retorna `200 {"status":"UP"}` com Eureka em modo `prefer-ip-address`
- [ ] Nenhuma dependencia `spring-boot-starter-data-jpa`, `flyway-core` ou `spring-datasource` no classpath
- [ ] Pacote root `com.neuroassist.dash` com subpacotes `api`, `application`, `domain`, `infrastructure`
- [ ] Variaveis de ambiente de setup-local.md documentadas em `application.yml` com `${ENV_VAR:default}`

## Dependencias

Nenhuma

## Estimativa

M

## Prioridade

P1
