# HIST — Integration Contracts (`hist-service`)

> Module: **HIST — Histórico e Auditoria** · Service: `hist-service` · Package: `com.neuroassist.hist`
> Traces ADR-005 (sync ingestion) and vision doc 04 (external integrations). No TechLab AI / hub
> dependency (`ai.hasAi = false`).

HIST has **one outbound runtime dependency** (ACS) and a set of **inbound REST callers** (producers +
DASH). All calls are synchronous REST via GWY, discovered via REG. There is no broker, no third-party
SDK, and no biometric/edge/video integration — HIST never touches biometric data.

---

## 1. Inbound integrations

| Caller | Purpose | Endpoint | Scope |
|---|---|---|---|
| EVNT | submit event-verdict / feedback audit facts | `POST /api/v1/hist/audit-facts` | `hist:write` |
| PAC | submit command edit/recalibration facts | `POST /api/v1/hist/audit-facts` | `hist:write` |
| ACS | submit access-granted/revoked facts | `POST /api/v1/hist/audit-facts` | `hist:write` |
| EDGE | submit device paired/unpaired facts | `POST /api/v1/hist/audit-facts` | `hist:write` |
| DASH | render timeline + verification badge | `GET /api/v1/hist/history*` | `hist:read` / `hist:read:all` |
| `@neuroassist/hist` FE (via GWY) | `/historico` timeline + chain badge | `GET /api/v1/hist/history*` | `hist:read` |

Contract for all inbound calls is `api-contract.md`. Producers own their outbox/retry (ADR-005).

---

## 2. Outbound integration — ACS `ActorDirectory`

HIST's only outbound dependency. Used **only on caregiver-scoped reads** to resolve the caller's patient
scope (so a caregiver sees only their patients' history, `api-contract.md §6/§7`). It is **not** on the
ingestion path — `actorRef` is a producer-carried snapshot, so an append never blocks on ACS (ADR-005).

| Attribute | Value |
|---|---|
| Adapter | `AcsActorDirectoryClient` (Feign / `RestClient`) behind `ActorDirectoryPort` |
| Service | `acs-service` (via GWY/REG; `neuroassist.acs.url`) |
| Operation | `GET /api/v1/acs/directory/caregivers/{id}/patient-scope` → list of `patientId` in the caller's tenant |
| Auth | service credential (client_credentials JWT) with the directory scope |
| Timeout | 2s connect+read |
| Circuit breaker | Resilience4j; OPEN after failure threshold |
| Fallback | **fail-closed** on `hist:read` (never widen an unverifiable scope) |

**EARS — ACS integration**
- WHEN a caregiver-scoped read needs a patient scope and ACS responds within timeout, THE SYSTEM SHALL
  filter results to the returned `patientId` set.
- IF the ACS call times out, errors, or the circuit is OPEN, THEN THE SYSTEM SHALL respond `503`
  (`type: /errors/scope-unavailable`) for `hist:read` callers and SHALL NOT return unscoped results.
- WHERE the caller holds `hist:read:all` (coordinator/DPO), THE SYSTEM SHALL NOT call ACS and SHALL serve
  the full tenant scope directly.
- THE SYSTEM SHALL never call ACS on the `POST /audit-facts` path (ingestion is ACS-independent, ADR-005).

---

## 3. Infrastructure integrations

| Dependency | Role | Notes |
|---|---|---|
| PostgreSQL (`t_<empresa>`) | ledger store | operationally owned; DDL via MIG (ADR-003 shared) |
| MIG (`neuroassist-ai-migrations`) | schema provisioning + migrations | HIST authors `V<n>__hist_*.sql`; MIG applies. No boot-time DDL. |
| REG (`neuroassist-ai-service-registry`) | Eureka discovery | HIST registers; resolves ACS |
| GWY (`neuroassist-ai-api-gateway`) | edge routing | all inbound traffic |
| ACS (JWK set) | token validation | HIST validates JWTs locally against ACS JWK set (no per-request callback) |

**EARS**
- THE SYSTEM SHALL author its schema changes as MIG-applied migrations and SHALL NOT run DDL at boot.
- THE SYSTEM SHALL validate inbound JWTs against the ACS JWK set cached locally and refreshed on rotation.

---

## 4. Not integrated (explicit non-dependencies)

- **No TechLab AI / hub** bounded context (`ai.hasAi = false`) — no orchestration, knowledge,
  document_composition, or analytics.
- **No broker** (`messaging.enabled = false`).
- **No edge/video/model/biometric** channel — HIST never receives frames, landmarks, or embeddings.
- **No third-party SaaS/API/SDK.**
