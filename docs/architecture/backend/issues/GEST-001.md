# GEST-001 -- Create gest-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton)

**Status:** EM ANDAMENTO -- reconciliado 2026-08-10
**Maturidade:** 2 -- Estrutura presente
**Done/Total:** 7/10 itens

## Contexto
O `gest-service` (porta 8084) precisa de um modulo Gradle compilavel com Spring Boot 3 / Java 21, estrutura hexagonal de pacotes (`api`, `application`, `domain`, `infrastructure`) e configuracoes minimas de Eureka + datasource para que as demais issues consigam compilar e rodar.

## Spec de referencia
- SPEC-01, SPEC-04, setup-local.md

## Escopo
- [x] implementar `backend/services/gest-service/build.gradle.kts` (spring-boot-starter-web, spring-cloud-starter-netflix-eureka-client, spring-boot-starter-data-jpa, spring-boot-starter-oauth2-resource-server, openfeign, resilience4j, spring-boot-starter-aop)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/GestApplication.java` com `@SpringBootApplication @EnableFeignClients` (Eureka auto-configurado pelo spring-cloud-starter-netflix-eureka-client; arquivo nomeado GestApplication, nao GestServiceApplication)
- [x] implementar `backend/services/gest-service/src/main/resources/application.yml` (porta 8084, eureka defaultZone, spring.application.name=gest-service, spring.jpa.open-in-view=false, ddl-auto=validate; usa issuer-uri para OAuth2 resource server)
- [x] criar pacotes hexagonais: `api/controller`, `api/dto`, `api/exception`, `application/port/in`, `application/port/out`, `application/service`, `domain/model`, `domain/service`, `infrastructure/clients`, `infrastructure/config`, `infrastructure/messaging/producer`, `infrastructure/persistence/{adapter,entity,repository}`, `infrastructure/security`, `shared/{audit,error,messaging,security}`
- [ ] adicionar `.env.example` com todas as variaveis de setup-local.md
- [x] registrar modulo em `backend/settings.gradle.kts` (include ':services:gest-service')

## Resultado esperado
`./gradlew :services:gest-service:bootRun` sobe na porta 8084, registra-se no Eureka e responde `GET /actuator/health` com `200 {status:UP}`.

## Criterios de aceite
- [ ] `./gradlew :services:gest-service:compileJava` sai com exit 0 (requer execucao -- nao verificado ainda)
- [ ] `GET /actuator/health` retorna `200 {status:UP}` com o servico no ar (requer execucao -- nao verificado ainda)
- [x] datasource configurado sem auto-create: `spring.jpa.hibernate.ddl-auto=validate` em `application.yml`
- [x] pacotes hexagonais (`api/controller`, `api/dto`, `api/exception`, `application/port/{in,out}`, `application/service`, `domain/model`, `domain/service`, `infrastructure/*`) existem no classpath

## Dependencias
Nenhuma.

## Estimativa
S

## Prioridade
P1
