# HIST-001 -- Hexagonal scaffold: package tree + Spring Boot entry point

## Contexto

`hist-service` segue arquitetura hexagonal (layers: `api`, `application`, `domain`,
`infrastructure`). Este issue materializa a estrutura de pacotes Gradle conforme
`generator-config.yml` e `hist-service-spec.md`, sem nenhuma lógica de negócio.
É o pré-requisito de todos os demais issues.

## Spec de referencia

- SPEC-01 (API Surface — lista de endpoints e estrutura do serviço)
- `docs/architecture/backend/services/hist-service/generator-config.yml`
- `docs/architecture/backend/services/hist-service/hist-service-spec.md`

## Escopo

- Módulo Gradle `hist-service` no `settings.gradle.kts` raiz.
- `build.gradle.kts` com dependências mínimas: `spring-boot-starter-web`,
  `spring-boot-starter-data-jpa`, `spring-boot-starter-oauth2-resource-server`,
  `spring-boot-starter-actuator`, `spring-cloud-starter-netflix-eureka-client`,
  `micrometer-registry-prometheus`, `postgresql`, `flyway-core`.
- `HistServiceApplication.kt` com `@SpringBootApplication`.
- Pacotes vazios: `com.neuroassist.hist.{domain,application,infrastructure,api}`.
- `application.yml` com porta `8089`, registro Eureka, datasource placeholder.
- `application-test.yml` com Testcontainers / embedded DB config.

## Resultado esperado

O módulo compila (`./gradlew :hist-service:build -x test`), sobe e expõe
`/actuator/health` respondendo `200` com DB placeholder configurado.

## Criterios de aceite

- [ ] `./gradlew :hist-service:compileKotlin` sai com código 0.
- [ ] `./gradlew :hist-service:bootRun` (com datasource de teste) inicia sem exceção.
- [ ] `GET /actuator/health` retorna `200 {"status":"UP"}`.
- [ ] Pacotes `domain`, `application`, `infrastructure`, `api` existem sob `com.neuroassist.hist`.
- [ ] Nenhuma dependência `spring-boot-starter-messaging` ou broker declarada.

## Dependencias

Nenhuma (issue raiz).

## Estimativa

M

## Prioridade

P1
