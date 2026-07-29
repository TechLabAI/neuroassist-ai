# neuroassist-ai-api-gateway

NeuroassistAiApiGateway — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud GWY (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :neuroassist-ai-api-gateway:build
./gradlew :neuroassist-ai-api-gateway:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :neuroassist-ai-api-gateway:test              # unitários (sem Docker)
./gradlew :neuroassist-ai-api-gateway:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :neuroassist-ai-api-gateway:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8080`

## Referências

- [Setup Local](../../docs/architecture/tools/neuroassist-ai-api-gateway/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\neuroassist-ai-api-gateway\neuroassist-ai-api-gateway-spec.md)
- [ADRs](../../docs/architecture/tools/neuroassist-ai-api-gateway/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
