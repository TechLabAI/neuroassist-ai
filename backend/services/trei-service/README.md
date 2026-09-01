# trei-service

Trei — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
-  (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud ACS (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :trei-service:build
./gradlew :trei-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :trei-service:test              # unitários (sem Docker)
./gradlew :trei-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :trei-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8095`

## Referências

- [Setup Local](../../docs/architecture/tools/trei-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\trei-service\trei-service-spec.md)
- [ADRs](../../docs/architecture/tools/trei-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
