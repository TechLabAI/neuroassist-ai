# EDGE-001 -- Create edge-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton)

**Status:** PARTIAL
**Maturidade:** 85 -- Maduro
**Done/Total:** 4/6 itens

## Contexto
O `edge-service` (porta 8087) precisa de um modulo Gradle compilavel com Spring Boot 3 / Java 21, estrutura hexagonal de pacotes (`api`, `application`, `domain`, `infrastructure`) e configuracoes minimas de Eureka + datasource + OAuth2 Resource Server para que as demais issues consigam compilar e rodar.

## Spec de referencia
- SPEC-01, setup-local.md

## Escopo
- [x] implementar `backend/services/edge-service/build.gradle.kts` (spring-boot-starter-web, spring-cloud-starter-netflix-eureka-client, spring-boot-starter-data-jpa, spring-boot-starter-oauth2-resource-server, spring-cloud-starter-openfeign, resilience4j, spring-security-test)
- [x] implementar `EdgeServiceApplication.java` com `@SpringBootApplication @EnableDiscoveryClient`
- [x] criar `application.yml` (porta 8087, eureka defaultZone, jwk-set-uri placeholder, spring.application.name=edge-service, schema-prefix=t_)
- [x] criar pacotes vazios: `api`, `application/service`, `application/listener`, `domain/model`, `domain/port`, `domain/event`, `infrastructure/config`, `infrastructure/persistence`, `infrastructure/integration`, `infrastructure/filter`, `infrastructure/metrics`
- [ ] adicionar `.env.example` com todas as variaveis de setup-local.md
- [x] registrar modulo em `backend/settings.gradle.kts` (include ':services:edge-service')

## Resultado esperado
`./gradlew :services:edge-service:bootRun` sobe na porta 8087, registra-se no Eureka e responde `GET /actuator/health` com `200 {status:UP}`.

## Criterios de aceite
- [ ] `./gradlew :services:edge-service:compileJava` sai com exit 0
- [ ] `GET /actuator/health` retorna `200 {status:UP}` com o servico no ar
- [ ] nenhuma dependencia JDBC ausente (datasource configurado)
- [ ] pacotes hexagonais (`api/application/domain/infrastructure`) existem no classpath

## Dependencias
Nenhuma.

## Estimativa
S

## Prioridade
P1
