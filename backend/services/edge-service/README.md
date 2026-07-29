# edge-service

Edge — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud EDGE (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :edge-service:build
./gradlew :edge-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :edge-service:test              # unitários (sem Docker)
./gradlew :edge-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :edge-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8087`

## Referências

- [Setup Local](../../docs/architecture/tools/edge-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\edge-service\edge-service-spec.md)
- [ADRs](../../docs/architecture/tools/edge-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
