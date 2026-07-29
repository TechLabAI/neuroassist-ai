# acs-service — Local Setup

> Module: **ACS — Cuidadores e Acesso** · Service: `acs-service`
> Port: **8088** (ACS reserved in NeuroAssist port allocation)
> Role: OAuth2 Authorization Server + Resource Server (dual-mode), tenant provisioning authority.

## 1. Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 | OpenJDK or GraalVM |
| Gradle | 8.x (wrapper) | `./gradlew` in the project root |
| PostgreSQL | 16 | Local or Docker (see §2) — ACS needs both `neuroassist_global` + per-tenant schemas |
| Docker Compose | 2.x | Recommended — for infra stack |

**Hub services needed locally:**
- `neuroassist-ai-service-registry` (REG) — default port `8761`
- `neuroassist-ai-migrations` (MIG) — port `8095` — ACS calls MIG for tenant schema provisioning

**Hub services optional locally (can be mocked):**
- `pac-service` — for `PacPatientResolvePort` on link creation; mock with `MockBean` in tests
- `hist-service` — for `HistAuditPort`; best-effort, mock in tests
- `neuroassist-ai-api-gateway` — not needed for direct local calls

## 2. Infra stack (Docker Compose)

```bash
# From techlab-infra (sibling clone)
docker compose -f ../techlab-infra/docker-compose.techlab.yml up -d postgres

# Verify PostgreSQL is up
docker compose -f ../techlab-infra/docker-compose.techlab.yml ps
```

PostgreSQL is exposed on port `5432` (or `5435` if running alongside pgVector).

ACS requires both schemas:
- `neuroassist_global` — for `tenant` and `signing_key` tables
- `t_<tenantId>` — per-tenant schemas provisioned at runtime via MIG

## 3. Environment variables

Copy `.env.example` to `.env` in `backend/services/acs-service/` and set:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/neuroassist
SPRING_DATASOURCE_USERNAME=neuroassist
SPRING_DATASOURCE_PASSWORD=<local-password>

# Global schema (tenant directory + signing keys)
NEUROASSIST_GLOBAL_SCHEMA=neuroassist_global

# Service Registry
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# OAuth2 — ACS is its own issuer (self-signed JWTs, RS256)
# No external Keycloak needed: ACS IS the authorization server
SERVER_PORT=8088

# Multi-tenancy (schema prefix)
NEUROASSIST_TENANT_SCHEMA_PREFIX=t_

# Upstream services
NEUROASSIST_MIG_URL=http://localhost:8095
NEUROASSIST_PAC_URL=http://localhost:8089
NEUROASSIST_HIST_URL=http://localhost:8091
```

## 4. Apply ACS migrations

ACS does not run Flyway locally — it contributes DDL to MIG. For local development, apply manually:

```bash
# Apply global schema DDL (tenant + signing_key tables)
psql -h localhost -U neuroassist -d neuroassist \
  -f backend/services/acs-service/src/main/resources/db/migration/global/V0200__acs_global_schema.sql

# Create a test tenant schema (empresa)
psql -h localhost -U neuroassist -d neuroassist \
  -c "CREATE SCHEMA IF NOT EXISTS t_empresa_test;"
psql -h localhost -U neuroassist -d neuroassist \
  -f backend/services/acs-service/src/main/resources/db/migration/empresa/V0250__acs_empresa_schema.sql
```

Or run integration tests — they use Testcontainers with a fresh PostgreSQL container.

## 5. Run the service

```bash
cd backend/services/acs-service

# Build and run
./gradlew :services:acs-service:bootRun

# Or build the JAR
./gradlew :services:acs-service:bootJar
java -jar build/libs/acs-service-*.jar
```

Service starts on `http://localhost:8088`.

## 6. Verify

```bash
# Health check
curl http://localhost:8088/actuator/health

# JWK set (public — resource servers resolve this endpoint)
curl http://localhost:8088/oauth2/jwks

# Authenticate (password grant)
curl -X POST http://localhost:8088/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{"grantType":"password","login":"jamilly@familia-amaral","password":"test"}'

# List caregivers (requires JWT)
curl -H "Authorization: Bearer <jwt>" \
     -H "X-Correlation-Id: local-test-001" \
     http://localhost:8088/api/v1/acs/caregivers
```

## 7. Run BDD tests (post-scaffold)

```bash
# BDD gate (Cucumber integration tests — requires Testcontainers)
./gradlew :services:acs-service:test -Dcucumber.filter.tags="@integration"

# Or replay deferred gate after scaffold
python scripts/be-gates-replay.py --run --update --service acs-service --gate bddGate
```

## 8. OAuth2 notes (dual-mode AS + RS)

ACS acts as both:
- **Authorization Server**: mints RS256 JWTs at `POST /oauth2/token`
- **Resource Server**: validates its own JWTs on admin/directory endpoints

No external Keycloak is needed. The JWK set is served at `GET /oauth2/jwks` and cached by all peer
resource servers (GEST, EDGE, EVNT, DASH, HIST, PAC) via their `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
pointing at ACS.

In tests, use `application-test.yml` with `jwk-set-uri` (not `issuer-uri`) to avoid OIDC discovery
at boot time (Keycloak/issuer not up in test context — known pitfall D-492).
