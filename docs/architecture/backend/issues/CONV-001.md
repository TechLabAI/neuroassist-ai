# CONV-001 -- Criar estrutura Gradle + pacotes hexagonais do conv-service

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Primeira issue executável do CONV. Materializa o microserviço `conv-service` no monorepo Gradle,
criando o módulo Gradle com as camadas hexagonais canônicas (`ADR-001-hexagonal-architecture.md`):
`api`, `application`, `domain`, `infrastructure`. O serviço é Java 21 / Spring Boot 3.5.x; porta
local **8090** (setup-local.md). Nenhum código de negócio ainda — apenas estrutura compilável que
serve de base para CONV-002 em diante.

## Spec de referencia

- SPEC-01 (api-surface — declaração do módulo Gradle e convenções)
- `ADR-001-hexagonal-architecture.md` (camadas hexagonais, ArchUnit rules)

## Escopo

- [ ] Criar `backend/conv-service/build.gradle.kts` com deps Spring Boot (web, data-jpa, security, oauth2-resource-server, actuator, feign), Flyway (runtime-only), PostgreSQL driver, elasticsearch-java client via `elastic-stack-client-lib`, Cucumber JVM, WireMock (test)
- [ ] Criar `backend/conv-service/src/main/java/com/neuroassist/conv/` com subpacotes `api/`, `application/`, `domain/`, `infrastructure/`
- [ ] Criar `backend/conv-service/src/main/resources/application.yml` com `server.port: 8090`, `spring.application.name: conv-service`, datasource placeholder, Eureka client config e `CONV_AUDIO_ROOT` env binding
- [ ] Criar `backend/conv-service/src/test/resources/application-test.yml` com `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: <static-test-jwks>` (Learning D-492 — NUNCA `issuer-uri`)
- [ ] Criar `ConvServiceApplication.java` em `com.neuroassist.conv`
- [ ] Adicionar `:conv-service` a `backend/settings.gradle.kts`
- [ ] Verificar `./gradlew :conv-service:compileJava` com saída limpa

## Resultado esperado

Módulo Gradle compilável; `ConvServiceApplication` sobe localmente (com datasource mockada/H2 para teste rápido); estrutura de pacotes canônica do framework hexagonal presente e vazia.

## Criterios de aceite

- [ ] `./gradlew :conv-service:compileJava` retorna BUILD SUCCESS sem warnings de deprecação
- [ ] Pacotes `com.neuroassist.conv.{api,application,domain,infrastructure}` existem e são reconhecidos pelo Gradle
- [ ] `application-test.yml` contém `jwk-set-uri` (não `issuer-uri`) — verificável via grep no arquivo
- [ ] Módulo aparece em `./gradlew projects` como `:conv-service`

## Dependencias

Nenhuma.

## Estimativa

M

## Prioridade

P1
