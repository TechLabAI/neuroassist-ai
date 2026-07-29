# GEST-001 -- Create gest-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
O `gest-service` (porta 8084) precisa de um modulo Gradle compilavel com Spring Boot 3 / Java 21, estrutura hexagonal de pacotes (`api`, `application`, `domain`, `infrastructure`) e configuracoes minimas de Eureka + datasource para que as demais issues consigam compilar e rodar.

## Spec de referencia
- SPEC-01, SPEC-04, setup-local.md

## Escopo
- [ ] implementar `backend/services/gest-service/build.gradle.kts` (spring-boot-starter-web, spring-cloud-starter-netflix-eureka-client, spring-boot-starter-data-jpa, spring-boot-starter-oauth2-resource-server, openfeign, resilience4j)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/GestServiceApplication.java` com `@SpringBootApplication @EnableDiscoveryClient`
- [ ] implementar `backend/services/gest-service/src/main/resources/application.yml` (porta 8084, eureka defaultZone, jwk-set-uri placeholder, schema-prefix `t_`, spring.application.name=gest-service)
- [ ] criar pacotes vazios `api`, `application/service`, `application/listener`, `domain/model`, `domain/port`, `domain/event`, `infrastructure/config`, `infrastructure/persistence`, `infrastructure/integration`, `infrastructure/filter`, `infrastructure/metrics`
- [ ] adicionar `.env.example` com todas as variaveis de setup-local.md
- [ ] registrar modulo em `backend/settings.gradle.kts` (include ':services:gest-service')

## Resultado esperado
`./gradlew :services:gest-service:bootRun` sobe na porta 8084, registra-se no Eureka e responde `GET /actuator/health` com `200 {status:UP}`.

## Criterios de aceite
- [ ] `./gradlew :services:gest-service:compileJava` sai com exit 0
- [ ] `GET /actuator/health` retorna `200 {status:UP}` com o servico no ar
- [ ] nenhuma dependencia JDBC/R2DBC ausente (datasource configurado mas sem auto-create)
- [ ] pacotes hexagonais (`api/application/domain/infrastructure`) existem no classpath

## Dependencias
Nenhuma.

## Estimativa
S

## Prioridade
P1
