# neuroassist-ai-service-registry

NeuroassistAiServiceRegistry — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud REG (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :neuroassist-ai-service-registry:build
./gradlew :neuroassist-ai-service-registry:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :neuroassist-ai-service-registry:test              # unitários (sem Docker)
./gradlew :neuroassist-ai-service-registry:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :neuroassist-ai-service-registry:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8761`

## Referências

- [Setup Local](../../docs/architecture/tools/neuroassist-ai-service-registry/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\neuroassist-ai-service-registry\neuroassist-ai-service-registry-spec.md)
- [ADRs](../../docs/architecture/tools/neuroassist-ai-service-registry/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
