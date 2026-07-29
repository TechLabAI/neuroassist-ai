# pac-service

Pac — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud EVNT (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :pac-service:build
./gradlew :pac-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :pac-service:test              # unitários (sem Docker)
./gradlew :pac-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :pac-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8082`

## Referências

- [Setup Local](../../docs/architecture/tools/pac-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\pac-service\pac-service-spec.md)
- [ADRs](../../docs/architecture/tools/pac-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
