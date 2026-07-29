# edge-service — Local Setup

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service`
> Port: **8087** (EDGE reserved in NeuroAssist port allocation; already referenced by GEST setup as `EDGE_SERVICE_URL`)

## 1. Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 | OpenJDK or GraalVM |
| Gradle | 8.x (wrapper) | `./gradlew` in the project root |
| PostgreSQL | 16 | Local or Docker (see §2) |
| Docker Compose | 2.x | Optional — for infra stack |

**Hub services that must be reachable:**
- `neuroassist-ai-service-registry` (REG) — default port `8761`
- `acs-service` — for `jwk-set-uri` and the device `client_credentials` token endpoint (can use a stub; §3)

**Hub services that are optional locally (can be mocked):**
- `gest-service` — detection-profile pointer; mock with `@MockBean DetectionProfileClient` in tests
- `neuroassist-ai-api-gateway` — not needed for direct local calls

## 2. Infra stack (Docker Compose)

```bash
# From techlab-infra (sibling clone)
docker compose -f ../techlab-infra/docker-compose.techlab.yml up -d postgres

# Verify PostgreSQL is up
docker compose -f ../techlab-infra/docker-compose.techlab.yml ps
```

PostgreSQL is exposed on port `5432` (or `5435` if running alongside pgVector — check compose file).

## 3. Environment variables

Copy `.env.example` to `.env` in `backend/services/edge-service/` and set:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/neuroassist
SPRING_DATASOURCE_USERNAME=neuroassist
SPRING_DATASOURCE_PASSWORD=<local-password>

# Service Registry
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# OAuth2 (jwk-set-uri — use acs-service or a local Keycloak)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:8080/oauth2/jwks

# GEST service (detection-profile pointer) — set to a stub or skip with MockBean in tests
NEUROASSIST_GEST_URL=http://localhost:8084

# Heartbeat / liveness parameters (api-contract §5)
NEUROASSIST_EDGE_HEARTBEAT_INTERVAL_SECONDS=30
NEUROASSIST_EDGE_HEARTBEAT_STALENESS_FACTOR=3
NEUROASSIST_EDGE_LIVENESS_POLL_HINT_SECONDS=15
NEUROASSIST_EDGE_ENROLLMENT_TTL_SECONDS=900

# Service port
SERVER_PORT=8087

# Multi-tenancy (schema prefix)
NEUROASSIST_TENANT_SCHEMA_PREFIX=t_
```

## 4. Apply EDGE migrations

EDGE does not run Flyway locally — it contributes to MIG. For local development, apply the EDGE migration
scripts manually to a test schema:

```bash
# Generate the migration SQL from the data model
python scripts/be-migration-generator.py --service edge-service

# Create a test tenant schema and apply
psql -h localhost -U neuroassist -d neuroassist -c "CREATE SCHEMA IF NOT EXISTS t_empresa_test;"
psql -h localhost -U neuroassist -d neuroassist -f backend/services/edge-service/src/main/resources/db/migration/empresa/V0700__edge_initial_schema.sql
```

Or run the `edge-service` integration tests — they use Testcontainers with a fresh PostgreSQL container
and apply the migration automatically.

## 5. Run the service

```bash
cd backend/services/edge-service

# Build and run
./gradlew :services:edge-service:bootRun

# Or build the JAR and run
./gradlew :services:edge-service:bootJar
java -jar build/libs/edge-service-*.jar
```

Service starts on `http://localhost:8087`.

## 6. Verify

```bash
# Health check
curl http://localhost:8087/actuator/health

# Register a device (conductor token, edge.manage) — returns a one-time enrollment token
curl -X POST -H "Authorization: Bearer <jwt>" \
     -H "Content-Type: application/json" \
     -H "X-Correlation-Id: local-test-001" \
     -d '{"label":"Camera quarto do Marcio"}' \
     http://localhost:8087/api/v1/edge/devices

# Poll device liveness (caregiver token, edge.read)
curl -H "Authorization: Bearer <jwt>" \
     http://localhost:8087/api/v1/edge/devices/<deviceId>/liveness
```

## 7. Run BDD tests (post-scaffold)

```bash
# BDD gate (Cucumber integration tests — requires Testcontainers)
./gradlew :services:edge-service:test -Dcucumber.filter.tags="@integration"

# Or replay deferred gate after scaffold
python scripts/be-gates-replay.py --run --update --service edge-service --gate bddGate
```
