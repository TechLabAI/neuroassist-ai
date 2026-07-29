# gest-service

Gest — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud GEST (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :gest-service:build
./gradlew :gest-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :gest-service:test              # unitários (sem Docker)
./gradlew :gest-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :gest-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8084`

## Referências

- [Setup Local](../../docs/architecture/tools/gest-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\gest-service\gest-service-spec.md)
- [ADRs](../../docs/architecture/tools/gest-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
