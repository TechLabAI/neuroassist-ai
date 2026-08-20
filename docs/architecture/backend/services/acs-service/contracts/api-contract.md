# ACS — API Contract (`acs-service`)

> Module: **ACS — Cuidadores e Acesso** · Service: `acs-service` · Package: `com.neuroassist.acs`
> Project: NeuroAssist AI · Multi-tenant: schema-per-empresa · Messaging: disabled (sync REST only)
> **Barrier artifact** — this is the source of truth for the FE (`@neuroassist` auth/onboarding libs)
> and for every peer service that validates ACS-issued JWTs as an OAuth2 resource server.
> EARS notation (D-057) governs every endpoint invariant. Guide: `docs/architecture/backend/standards/ears-notation.md`.
> Traces ADR-004 (tenant/identity provisioning authority), ADR-005 (auth + RBAC authority model),
> ADR-006 (caregiver↔patient linkage drives data-scope).

`acs-service` exposes **three API surfaces**:

1. **Auth surface** (`/oauth2/**`) — login → token, and the public JWK set. Consumed by clients and,
   for the JWK set, by *every* service (resource servers).
2. **Admin surface** (`/api/v1/acs/**`) — tenant provisioning, caregiver accounts, caregiver↔patient
   linkage. Consumed by DASH / the onboarding wizard through GWY.
3. **Directory surface** (`/api/v1/acs/directory/**`) — read-only resolves consumed service-to-service
   by PAC (`AcsDirectoryPort`), EVNT (`CaregiverRoutingClient`), EDGE, DASH, HIST.

---

## 1. Conventions

- **Base paths:** `/oauth2` (auth surface) and `/api/v1/acs` (admin + directory), routed through GWY,
  discovered via REG.
- **Media type:** `application/json`. Errors use `application/problem+json` (RFC 7807, §7).
- **Versioning:** URI-versioned (`/api/v1`). The auth surface follows OAuth2 conventions (unversioned
  `/oauth2/token`, `/oauth2/jwks`).
- **Identifiers:** all resource ids are `uuid` (string form, RFC 4122; `uuid_generate_v7`, PS.001).
- **Timestamps:** ISO-8601 UTC (`2026-07-29T14:00:00Z`).
- **Pagination:** list endpoints accept `?page` (0-based) and `?size` (default 50, max 200); responses
  wrap items in `{ "items": [...], "page": n, "size": n, "total": n }`.

### 1.1 Mandatory headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | OAuth2 access token (see §6). Not required on `/oauth2/token` (login) or `/oauth2/jwks` (public). |
| `X-Correlation-Id` | request/response | Trace correlation; echoed back. Generated at GWY if absent. |
| `X-Empresa-Id` | internal only | Never trusted from the client on tenant-scoped writes; the JWT `tenant` claim is authoritative. On directory (service-to-service) reads it names the *target* tenant and is validated against the caller's service credential. |

**EARS — headers & tenant scoping**
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it in the response.
- THE SYSTEM SHALL derive the acting `tenant` exclusively from the JWT `tenant` claim on the admin
  surface and SHALL NOT accept a tenant id from the request body, query, or path.
- IF a request body carries a `tenantId` field that differs from the JWT `tenant` claim, THEN THE SYSTEM
  SHALL reject the request with `403 Forbidden` (`type: /errors/tenant-mismatch`).
- THE SYSTEM SHALL treat the tenant directory (`/tenants`) as the single **global-scoped** write surface;
  every caregiver/linkage write SHALL be confined to the caller's tenant schema.

---

## 2. Auth surface — `/oauth2`

ACS is the platform **OAuth2 authorization server** (ADR-005). It authenticates a `Cuidador` by
credential and issues a short-TTL JWT; peers validate locally against the JWK set with no callback.

### 2.1 `POST /oauth2/token` — authenticate & issue token

Resource-owner password grant (MVP; no self-service sign-up). Public endpoint (no bearer required).

- Request:

```json
{ "grantType": "password", "login": "jamilly@familia-amaral", "password": "••••••••" }
```

- Response `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6…",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "tenant": "t_0f8a…",
  "sub": "5c1a…",
  "papel": "cuidadora",
  "authorities": ["acs.cuidador.read", "acs.directory.read", "evnt.read", "evnt.confirm"]
}
```

- WHEN valid credentials are presented for an `active` caregiver, THE SYSTEM SHALL return `200` with a
  signed JWT access token whose claims include `sub = cuidadorId`, `tenant = tenantId`,
  `authorities = roleToAuthorities(papel)`, `token_type = "access"`, `exp = now + 900s`,
  `iss = acs-service`, and `kid` referencing the active signing key.
- THE SYSTEM SHALL also issue a refresh token (`token_type = "refresh"`, TTL 7 days, unique `jti`)
  and return it in `refreshToken` field. The refresh token is a signed RS256 JWT (not opaque).
- IF the login is unknown, the password does not match, or the caregiver `status = inactive`, THEN THE
  SYSTEM SHALL respond `401 Unauthorized` (`type: /errors/invalid-credentials`) with a body that does
  **not** distinguish which of those conditions failed (no user enumeration).
- IF the caregiver's `Tenant` is not `active` (e.g. `suspended`), THEN THE SYSTEM SHALL respond
  `401 Unauthorized` (`type: /errors/invalid-credentials`).
- WHEN authentication succeeds or fails, THE SYSTEM SHALL send an audit fact
  (`AutenticacaoRealizada` / `AutenticacaoFalhou`) to HIST asynchronously-best-effort, never blocking
  the token response on HIST.
- THE SYSTEM SHALL never return, log, or echo the raw password in any response, error body, or log line.

### 2.2 `POST /oauth2/token/refresh` — refresh access token (token rotation)

Exchanges a valid refresh token for a new access + refresh token pair (stateless sliding window).
Public endpoint — no bearer required (the refresh token itself is the credential).

- Request:

```json
{ "refreshToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6…" }
```

- Response `200 OK`: same shape as `POST /oauth2/token` — new access token + new refresh token.
- WHEN a valid refresh token (`token_type = "refresh"`, signature valid, not expired) is presented,
  THE SYSTEM SHALL issue a new access token (TTL 900s) and a new refresh token (TTL 7 days, new `jti`).
- IF the refresh token is expired or the signature is invalid, THE SYSTEM SHALL respond `401 Unauthorized`
  (`type: /errors/invalid-credentials`).
- IF the refresh token has `token_type != "refresh"` (e.g. an access token submitted), THE SYSTEM SHALL
  respond `400 Bad Request` (`type: /errors/invalid-token-type`).
- IF the `sub` claim does not resolve to an existing caregiver, THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/caregiver-not-found`).
- THE SYSTEM SHALL implement stateless token rotation: no server-side session or blacklist is maintained.

### 2.3 `GET /oauth2/jwks` — public JWK set

The public keys peers use to validate ACS-issued tokens. Public endpoint; this is the `jwk-set-uri`
target every resource server (GEST/EDGE/EVNT/DASH/HIST/PAC) resolves via REG.

- Response `200 OK`:

```json
{
  "keys": [
    { "kty": "RSA", "use": "sig", "kid": "acs-2026-07", "alg": "RS256", "n": "…", "e": "AQAB" },
    { "kty": "RSA", "use": "sig", "kid": "acs-2026-06", "alg": "RS256", "n": "…", "e": "AQAB" }
  ]
}
```

- THE SYSTEM SHALL publish the current signing key plus every recently-retired key still inside a valid
  token's TTL, so in-flight tokens validate across a key rotation.
- THE SYSTEM SHALL expose only **public** key material at this endpoint and SHALL never expose private
  key material through any API.
- THE SYSTEM SHALL set `Cache-Control: max-age=300` so resource servers cache the JWK set and do not
  poll ACS on every request.

---

## 3. Admin surface — Tenant provisioning (`/api/v1/acs/tenants`)

ACS is the tenant provisioning authority (ADR-004). Provisioning is a fail-closed synchronous saga
against MIG: a tenant is `active` only after MIG confirms its schema.

### 3.1 `POST /api/v1/acs/tenants` — provision a tenant

Creates the `Tenant` and requests its per-tenant schema from MIG. Restricted to the bootstrap conductor
(platform authority `acs.tenant.provisionar`); it is *not* a tenant-scoped operation.

- Request:

```json
{ "nome": "Família Amaral", "tipo": "familia" }
```

- Response `201 Created` — `Location: /api/v1/acs/tenants/{id}`:

```json
{
  "id": "t_0f8a…",
  "nome": "Família Amaral",
  "tipo": "familia",
  "status": "active",
  "schemaName": "t_0f8a…",
  "criadoEm": "2026-07-29T14:00:00Z"
}
```

- WHEN a tenant is created, THE SYSTEM SHALL persist it with `status = provisioning`, request the schema
  from MIG (`MigProvisioningPort`), and only on MIG success SHALL transition it to `active` before
  responding `201`.
- IF MIG fails or times out, THEN THE SYSTEM SHALL leave the tenant in `status = provisioning`, respond
  `202 Accepted` with the `provisioning` snapshot, and SHALL NOT report `active` — the operation is
  retriable via §3.3 (never a half-open tenant).
- IF `tipo` is not one of `familia | clinica`, THEN THE SYSTEM SHALL respond `400 Bad Request`
  (`type: /errors/validation`).
- WHERE the caller lacks authority `acs.tenant.provisionar`, THE SYSTEM SHALL respond `403 Forbidden`.
- WHEN a tenant reaches `active`, THE SYSTEM SHALL emit the domain fact `TenantProvisionado` and send an
  audit fact to HIST.

### 3.2 `GET /api/v1/acs/tenants/{id}` — get tenant

- Response `200 OK` — the `Tenant` snapshot (shape as §3.1).
- IF `{id}` is unknown, THEN THE SYSTEM SHALL respond `404 Not Found` (`type: /errors/tenant-not-found`).
- WHERE the caller lacks authority `acs.tenant.provisionar` (conductor/coordinator with directory role),
  THE SYSTEM SHALL respond `403 Forbidden`.

### 3.3 `POST /api/v1/acs/tenants/{id}/provisioning:retry` — retry a stuck provisioning

- Response `200 OK` (now `active`) or `202 Accepted` (still `provisioning`).
- WHILE the tenant is in `status = provisioning`, THE SYSTEM SHALL re-attempt the MIG schema request
  idempotently and transition to `active` on success.
- IF the tenant is already `active`, THEN THE SYSTEM SHALL respond `200 OK` with the current snapshot and
  perform no MIG call (idempotent no-op).
- IF the tenant is `suspended`, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/tenant-suspended`).

---

## 4. Admin surface — Caregivers (`/api/v1/acs/caregivers`)

`Cuidador` accounts are tenant-scoped. Only `coordenador_clinica` (or the bootstrap conductor) may
create or deactivate accounts (invariant #6).

### 4.1 `POST /api/v1/acs/caregivers` — create a caregiver

- Request:

```json
{ "nome": "Jamilly Amaral", "login": "jamilly@familia-amaral", "papel": "cuidadora", "senha": "••••••••" }
```

- Response `201 Created` — `Location: /api/v1/acs/caregivers/{id}`:

```json
{
  "id": "5c1a…",
  "tenant": "t_0f8a…",
  "nome": "Jamilly Amaral",
  "login": "jamilly@familia-amaral",
  "papel": "cuidadora",
  "status": "active",
  "criadoEm": "2026-07-29T14:05:00Z"
}
```

- WHEN a caregiver is created against an `active` tenant, THE SYSTEM SHALL hash the `senha` with the
  configured `DelegatingPasswordEncoder`, persist `credential_hash`, and return the account **without**
  any credential field.
- IF the caller's tenant is not `active`, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/tenant-not-active`) — invariant #2.
- IF `login` already exists in the caller's tenant, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/login-duplicate`).
- IF `papel` is not one of `cuidadora | coordenador_clinica`, THEN THE SYSTEM SHALL respond
  `400 Bad Request` (`type: /errors/validation`).
- WHERE the caller lacks authority `acs.cuidador.criar`, THE SYSTEM SHALL respond `403 Forbidden` —
  a `cuidadora` may not self-elevate (invariant #6).
- WHEN a caregiver is created, THE SYSTEM SHALL emit `CuidadorRegistrado` and audit to HIST.

### 4.2 `GET /api/v1/acs/caregivers` — list caregivers (tenant-scoped)

- Query: `?papel=…`, `?status=active|inactive`, `?page`, `?size`.
- Response `200 OK` — paged list of caregiver snapshots (shape §4.1, no credential fields).
- THE SYSTEM SHALL return only caregivers belonging to the caller's tenant schema.
- WHERE the caller has `papel = cuidadora`, THE SYSTEM SHALL restrict the result to the caller's own
  account (a `cuidadora` has no tenant-wide directory view); a `coordenador_clinica` sees all.

### 4.3 `GET /api/v1/acs/caregivers/{id}` — get a caregiver

- Response `200 OK` — the caregiver snapshot (no credential fields).
- IF `{id}` is not in the caller's tenant, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/caregiver-not-found`).

### 4.4 `POST /api/v1/acs/caregivers/{id}/deactivation` — deactivate (soft)

- Response `200 OK` — caregiver snapshot with `status: "inactive"`.
- WHEN a caregiver is deactivated, THE SYSTEM SHALL set `status = inactive` (soft; no hard delete —
  invariant #7), emit `CuidadorDesativado`, audit to HIST, and the account SHALL no longer authenticate.
- THE SYSTEM SHALL rely on short token TTL to end the deactivated caregiver's access on the next token
  lifecycle (no server-side session store in the MVP — ADR-005; residual window documented in doc 06).
- IF the caregiver is already `inactive`, THEN THE SYSTEM SHALL respond `200 OK` (idempotent no-op).
- WHERE the caller lacks authority `acs.cuidador.desativar`, THE SYSTEM SHALL respond `403 Forbidden`.

---

## 5. Admin surface — Caregiver↔patient linkage (`/api/v1/acs/caregivers/{id}/links`)

The linkage is the authoritative routing/scope source (ADR-006). A link is valid only if caregiver and
patient are in the **same tenant** (invariant #1); `pacienteId` is a logical reference to a PAC
aggregate validated at link time via `PacPatientResolvePort`.

### 5.1 `POST /api/v1/acs/caregivers/{id}/links` — link caregiver to patient

- Request:

```json
{ "pacienteId": "p_9a3f…" }
```

- Response `201 Created` — `Location: /api/v1/acs/caregivers/{id}/links/{linkId}`:

```json
{
  "id": "lnk_71b2…",
  "tenant": "t_0f8a…",
  "cuidadorId": "5c1a…",
  "pacienteId": "p_9a3f…",
  "status": "active",
  "criadoEm": "2026-07-29T14:10:00Z"
}
```

- WHEN a link is requested, THE SYSTEM SHALL verify (a) the caregiver exists and is `active` in the
  caller's tenant and (b) the `pacienteId` resolves in the **same tenant** via `PacPatientResolvePort`,
  before writing the link.
- IF the caregiver and patient are in different tenants, THEN THE SYSTEM SHALL respond
  `422 Unprocessable Entity` (`type: /errors/cross-tenant-link`) — invariant #1.
- IF `pacienteId` does not resolve in PAC, THEN THE SYSTEM SHALL respond `422 Unprocessable Entity`
  (`type: /errors/unknown-patient`).
- IF an `active` link already exists for (`cuidadorId`, `pacienteId`), THEN THE SYSTEM SHALL respond
  `200 OK` with the existing link (idempotent) and SHALL NOT create a duplicate.
- WHERE the caller lacks authority `acs.vinculo.criar`, THE SYSTEM SHALL respond `403 Forbidden`.
- WHEN a link is created, THE SYSTEM SHALL emit **`CuidadorVinculadoAoPaciente`** (the anchor event),
  audit to HIST, and the link SHALL immediately drive routing/scope for EVNT/EDGE/DASH.
- WHILE PAC is unreachable within the timeout, THE SYSTEM SHALL reject the link with `502`/`504`
  (`type: /errors/upstream-unavailable`) rather than writing an unvalidated link (fail-closed).

### 5.2 `GET /api/v1/acs/caregivers/{id}/links` — list a caregiver's links

- Query: `?status=active|revoked`.
- Response `200 OK` — list of link snapshots (shape §5.1).

### 5.3 `DELETE /api/v1/acs/caregivers/{id}/links/{linkId}` — revoke a link (soft)

- Response `200 OK` — link snapshot with `status: "revoked"`.
- WHEN a link is revoked, THE SYSTEM SHALL set `status = revoked` (soft; audit trail preserved in HIST —
  invariant #7), emit `CuidadorDesvinculado`, and remove routing/scope on the **next** directory lookup.
- IF the link is already `revoked`, THEN THE SYSTEM SHALL respond `200 OK` (idempotent no-op).
- WHERE the caller lacks authority `acs.vinculo.revogar`, THE SYSTEM SHALL respond `403 Forbidden`.

---

## 6. Directory surface — service-to-service resolves (`/api/v1/acs/directory`)

Read-only, fail-closed resolves consumed by PAC (`AcsDirectoryPort`), EVNT (`CaregiverRoutingClient`),
EDGE, DASH, HIST. Callers authenticate with a **service credential** (OAuth2 `client_credentials`,
§7.3) or propagate the caregiver bearer; every response is tenant-validated.

### 6.1 `GET /api/v1/acs/directory/tenants/{tenantId}` — tenant validity

- Response `200 OK`:

```json
{ "tenantId": "t_0f8a…", "status": "active", "valid": true, "tipo": "familia" }
```

- THE SYSTEM SHALL return `valid: true` only WHILE the tenant `status = active`.
- IF `{tenantId}` is unknown, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/tenant-not-found`).

### 6.2 `GET /api/v1/acs/directory/patients/{pacienteId}/caregivers` — routing targets (EVNT)

Resolves "who should this patient's alarm be routed to". This is the emergency routing source (ADR-006);
callers (EVNT) treat an empty/unresolved result as a **routing failure**, never a silently dropped alarm.

- Query: `?tenant=t_0f8a…` (target tenant; validated against the caller's credential).
- Response `200 OK`:

```json
{
  "pacienteId": "p_9a3f…",
  "tenant": "t_0f8a…",
  "responsibleCaregivers": [
    { "cuidadorId": "5c1a…", "papel": "cuidadora", "status": "active" }
  ]
}
```

- THE SYSTEM SHALL return exactly the caregivers with an `active` `CaregiverPatientLink` to the patient,
  plus every `coordenador_clinica` of the tenant (tenant-wide responsibility — ADR-006 scope rule).
- WHEN no active caregiver resolves for the patient, THE SYSTEM SHALL return an empty
  `responsibleCaregivers` array with `200` (so EVNT can raise an explicit routing-failure alert), and
  SHALL NOT fabricate a target.
- IF `{pacienteId}` is unknown in the tenant, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/unknown-patient`).

### 6.3 `GET /api/v1/acs/directory/caregivers/{cuidadorId}` — role/authorities

- Response `200 OK`:

```json
{ "cuidadorId": "5c1a…", "tenant": "t_0f8a…", "papel": "cuidadora", "status": "active",
  "authorities": ["acs.cuidador.read", "acs.directory.read", "evnt.read", "evnt.confirm"] }
```

- THE SYSTEM SHALL resolve the authorities deterministically from `papel` (invariant #4, ADR-005).
- IF `{cuidadorId}` is unknown, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/caregiver-not-found`).

### 6.4 `GET /api/v1/acs/directory/caregivers/{cuidadorId}/patient-scope` — data-scope (entity-scope)

Resolves "which patients this caregiver may see" — the data-scope source consumed by DASH/HIST/PAC
Hibernate entity-scope filters (ADR-006).

- Response `200 OK`:

```json
{ "cuidadorId": "5c1a…", "papel": "cuidadora", "scope": "assigned",
  "pacienteIds": ["p_9a3f…", "p_2b71…"] }
```

- WHERE `papel = coordenador_clinica`, THE SYSTEM SHALL return `scope: "tenant"` meaning every patient of
  the tenant (the `pacienteIds` list MAY be omitted; callers grant tenant-wide scope).
- WHERE `papel = cuidadora`, THE SYSTEM SHALL return `scope: "assigned"` with exactly the `pacienteIds`
  of the caregiver's `active` links.
- WHEN a link is revoked, THE SYSTEM SHALL exclude that `pacienteId` from the next scope response.

---

## 7. Authentication & Authorization

### 7.1 OAuth2 posture

- THE SYSTEM SHALL require a valid OAuth2 Bearer JWT on every endpoint except `/oauth2/token`,
  `/oauth2/jwks`, and health/observability probes (SPEC-07).
- THE SYSTEM SHALL, as the issuer, validate its own admin/directory requests as a resource server against
  its own JWK set (self-consistent), and reject expired or malformed tokens with `401 Unauthorized`.

### 7.2 Authorities (namespace `acs.<recurso>.<acao>`)

Reconciled with `permission-catalogue.arch.json` (D-060). ACS **emits** these authorities in the JWT and
**enforces** the admin/directory subset via `@PreAuthorize`.

| Authority | Grants |
|---|---|
| `acs.tenant.provisionar` | Provision/retry/get tenants (§3) — conductor only |
| `acs.cuidador.criar` | Create caregiver (§4.1) — `coordenador_clinica` |
| `acs.cuidador.read` | Read caregiver(s) (§4.2, §4.3) |
| `acs.cuidador.desativar` | Deactivate caregiver (§4.4) — `coordenador_clinica` |
| `acs.vinculo.criar` | Link caregiver↔patient (§5.1) — `coordenador_clinica` |
| `acs.vinculo.revogar` | Revoke link (§5.3) — `coordenador_clinica` |
| `acs.directory.read` | Directory resolves (§6) — caregivers + service credentials |

**Role → authorities (total map, ADR-005):**

| `papel` | Authorities |
|---|---|
| `coordenador_clinica` | `acs.cuidador.criar`, `acs.cuidador.read`, `acs.cuidador.desativar`, `acs.vinculo.criar`, `acs.vinculo.revogar`, `acs.directory.read` (+ downstream `evnt.*`, `pac.*`, `dash.*` read/manage per catalogue) |
| `cuidadora` | `acs.cuidador.read` (self), `acs.directory.read` (self-scope), `evnt.read`, `evnt.confirm` |

- WHERE the caller's token lacks the required authority, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-authority`).
- THE SYSTEM SHALL treat the `role → authorities` map as total: every declared `papel` SHALL resolve to a
  defined, non-empty authority set (invariant #4).

### 7.3 Service (client-credentials) credential for directory reads

- THE SYSTEM SHALL authenticate directory (service-to-service) reads either with a caregiver bearer or a
  service credential (OAuth2 `client_credentials`) carrying `acs.directory.read`.
- IF a directory request targets a tenant the service credential is not scoped to, THEN THE SYSTEM SHALL
  respond `403 Forbidden` (`type: /errors/tenant-mismatch`).
- **Gap (carried to backlog):** the concrete service-credential shape (shared client secret vs. mesh
  identity) is reconciled with REG/GWY at scaffold; this contract assumes an OAuth2 `client_credentials`
  token minted by ACS for peers.

---

## 8. Errors — RFC 7807 ProblemDetail

All error responses use `application/problem+json`:

```json
{
  "type": "/errors/cross-tenant-link",
  "title": "Cross-tenant caregiver-patient link rejected",
  "status": 422,
  "detail": "Caregiver 5c1a… (tenant t_0f8a…) cannot be linked to patient p_9a3f… (tenant t_11cd…).",
  "instance": "/api/v1/acs/caregivers/5c1a…/links",
  "correlationId": "…",
  "errors": []
}
```

| `type` | HTTP | When |
|---|---|---|
| `/errors/validation` | 400 | Malformed/blank field, bad `tipo`/`papel`. |
| `/errors/invalid-credentials` | 401 | Bad login/password or inactive account/tenant (no enumeration). |
| `/errors/unauthenticated` | 401 | Missing/expired/malformed JWT on a protected endpoint. |
| `/errors/tenant-mismatch` | 403 | Body/target tenant ≠ JWT/service-credential tenant. |
| `/errors/insufficient-authority` | 403 | Missing required `acs.*` authority. |
| `/errors/tenant-not-found` | 404 | Unknown tenant id. |
| `/errors/caregiver-not-found` | 404 | Unknown caregiver id in tenant. |
| `/errors/unknown-patient` | 404/422 | Patient not resolvable in the tenant (404 on directory read, 422 on link). |
| `/errors/login-duplicate` | 409 | `login` already used in the tenant. |
| `/errors/tenant-not-active` | 409 | Caregiver creation against a non-`active` tenant. |
| `/errors/tenant-suspended` | 409 | Provisioning retry on a suspended tenant. |
| `/errors/cross-tenant-link` | 422 | Caregiver and patient in different tenants. |
| `/errors/upstream-unavailable` | 502/504 | MIG/PAC/HIST unreachable/timeout on a critical path. |

- THE SYSTEM SHALL include the `correlationId` (from `X-Correlation-Id`) in every ProblemDetail body.
- THE SYSTEM SHALL never include any credential, password, biometric, or clinical data in an error body
  or log line.

---

## 9. Endpoint summary

| Verb | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/oauth2/token` | public | Authenticate → JWT |
| GET | `/oauth2/jwks` | public | JWK set (resource-server validation) |
| POST | `/api/v1/acs/tenants` | `acs.tenant.provisionar` | Provision tenant (saga vs MIG) |
| GET | `/api/v1/acs/tenants/{id}` | `acs.tenant.provisionar` | Get tenant |
| POST | `/api/v1/acs/tenants/{id}/provisioning:retry` | `acs.tenant.provisionar` | Retry stuck provisioning |
| POST | `/api/v1/acs/caregivers` | `acs.cuidador.criar` | Create caregiver |
| GET | `/api/v1/acs/caregivers` | `acs.cuidador.read` | List caregivers (tenant-scoped) |
| GET | `/api/v1/acs/caregivers/{id}` | `acs.cuidador.read` | Get caregiver |
| POST | `/api/v1/acs/caregivers/{id}/deactivation` | `acs.cuidador.desativar` | Deactivate caregiver (soft) |
| POST | `/api/v1/acs/caregivers/{id}/links` | `acs.vinculo.criar` | Link caregiver↔patient (anchor) |
| GET | `/api/v1/acs/caregivers/{id}/links` | `acs.cuidador.read` | List a caregiver's links |
| DELETE | `/api/v1/acs/caregivers/{id}/links/{linkId}` | `acs.vinculo.revogar` | Revoke link (soft) |
| GET | `/api/v1/acs/directory/tenants/{tenantId}` | `acs.directory.read` | Tenant validity |
| GET | `/api/v1/acs/directory/patients/{pacienteId}/caregivers` | `acs.directory.read` | Routing targets (EVNT) |
| GET | `/api/v1/acs/directory/caregivers/{cuidadorId}` | `acs.directory.read` | Role/authorities |
| GET | `/api/v1/acs/directory/caregivers/{cuidadorId}/patient-scope` | `acs.directory.read` | Data-scope (entity-scope) |
| GET | `/api/v1/acs/health` | none/internal | Liveness/readiness (SPEC-07) |

## 10. References

- ADR-004 (tenant/identity provisioning authority), ADR-005 (auth + RBAC authority model),
  ADR-006 (caregiver↔patient linkage drives data-scope).
- Consumed by: EVNT api-contract §3.4/§5 (`confirmadoPor` resolve, routing target), GEST api-contract §6
  (validates ACS JWT via `jwk-set-uri`), EDGE (`EDGE-INT-002`).
- `contracts/data-model.md`, `contracts/event-contracts.md`, `contracts/hub-integration.md`,
  `contracts/integration-contracts.md`, `SPEC-01`, `SPEC-03`, `SPEC-06`.
