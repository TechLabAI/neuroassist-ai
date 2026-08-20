# HIST — API Contract (`hist-service`)

> Module: **HIST — Histórico e Auditoria** · Service: `hist-service` · Package: `com.neuroassist.hist`
> Project: NeuroAssist AI · Multi-tenant: schema-per-empresa (`t_<empresa>`) · Messaging: **disabled** (sync REST only)
> **Barrier artifact** — this is the source of truth for the FE (`@neuroassist/hist` library, route
> `/historico`) and for every producer service (EVNT, PAC, ACS, EDGE) that submits audit facts.
> EARS notation (D-057) governs every endpoint invariant. Guide: `docs/architecture/backend/standards/ears-notation.md`.
> Traces ADR-004 (immutable append-only ledger), ADR-005 (synchronous idempotent ingestion),
> ADR-006 (hash-chained tamper-evidence).

`hist-service` exposes **two API surfaces**:

1. **Ingestion surface** (`/api/v1/hist/audit-facts`) — producer services append audit facts. Write-once,
   idempotent on `factId`. Consumed service-to-service by EVNT / PAC / ACS / EDGE through GWY.
2. **History surface** (`/api/v1/hist/history`) — read-only timeline queries and chain verification.
   Consumed by DASH and by coordinators/DPOs (via the `@neuroassist/hist` FE library) through GWY.

There is **no** UPDATE and **no** DELETE endpoint anywhere in this contract — that absence is the design
(ADR-004).

---

## 1. Conventions

- **Base path:** `/api/v1/hist`, routed through GWY, discovered via REG.
- **Media type:** `application/json`. Errors use `application/problem+json` (RFC 7807, §7).
- **Versioning:** URI-versioned (`/api/v1`). Breaking payload changes bump to `/api/v2`.
- **Identifiers:** all HIST-owned ids are `uuid` (string form, RFC 4122, server-generated `uuid_generate_v7`
  for time-ordering). `factId`, `subjectId`, `actorCaregiverId` are producer-minted uuids.
- **Timestamps:** ISO-8601 UTC (`2026-08-16T14:00:00Z`). `occurredAt` = producer clock; `recordedAt` =
  HIST durable clock (authoritative order).
- **Pagination:** history endpoints accept `?page` (0-based) and `?size` (default 50, max 200); responses
  wrap items in `{ "items": [...], "page": n, "size": n, "total": n }`.
- **Ordering:** history results are ordered by `recordedAt DESC` (newest first) by default; `?order=asc`
  flips to chronological.

### 1.1 Mandatory headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | OAuth2 access token (ACS-issued, §6). Required on every endpoint. |
| `X-Correlation-Id` | request/response | Trace correlation; echoed back. Generated at GWY if absent. |
| `X-Empresa-Id` | internal only | Never trusted from the client. The JWT `tenant` claim is the sole authority for schema pinning. |

**EARS — headers & tenant scoping**
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it in the response.
- THE SYSTEM SHALL derive the acting `empresa` (tenant schema) exclusively from the JWT `tenant` claim and
  SHALL NOT accept a tenant id from the request body, query, or path.
- IF a request body carries a `tenantId`/`empresaId` field that differs from the JWT `tenant` claim, THEN
  THE SYSTEM SHALL reject the request with `403 Forbidden` (`type: /errors/tenant-mismatch`).
- THE SYSTEM SHALL confine every read and every append to the caller's tenant schema; no record or chain
  spans tenants (ADR-003 shared).

---

## 2. Ingestion surface — `/api/v1/hist/audit-facts`

Producers submit audit facts synchronously (ADR-005). Ingestion is **idempotent on `factId`** and
**write-once** (ADR-004). Requires the `hist:write` scope (service-to-service credential, §6).

### 2.1 `POST /api/v1/hist/audit-facts` — append an audit fact

Appends a new `AuditRecord` to the caller's tenant ledger, computes its position and hash in the
`(tenant, subject)` stream, and returns the persisted record.

**Request body** (`AuditFactRequest`):

```json
{
  "factId": "018f4e2a-0000-7000-8000-000000000001",
  "factType": "evento.confirmado",
  "subject": { "type": "paciente", "id": "018f4e2a-0000-7000-8000-0000000000aa" },
  "actor": {
    "caregiverId": "018f4e2a-0000-7000-8000-0000000000bb",
    "displayNameSnapshot": "Ana Costa"
  },
  "occurredAt": "2026-08-16T13:59:58Z",
  "payload": { "eventoId": "…", "verdict": "confirmed", "channel": "webcam" },
  "correctsFactId": null
}
```

| Field | Type | Req | Rule |
|---|---|---|---|
| `factId` | uuid | yes | Producer-minted idempotency key; unique per tenant. |
| `factType` | string | yes | Member of the controlled vocabulary (§5). Unknown ⇒ `422`. |
| `subject.type` | enum | yes | `paciente \| comando \| cuidador \| dispositivo`. |
| `subject.id` | uuid | yes | Opaque ref owned by producer; never resolved by HIST. |
| `actor.caregiverId` | uuid | yes | Ref → Cuidador (ACS). |
| `actor.displayNameSnapshot` | string | yes | Snapshot of caregiver label at fact time (≤ 200 chars). |
| `occurredAt` | timestamptz | yes | Producer clock; must not be in the future beyond 5 min skew. |
| `payload` | object (jsonb) | yes | Structured, self-contained snapshot. **No biometric/image data** (`422` if forbidden keys present). |
| `correctsFactId` | uuid \| null | no | If set, references a prior fact this record corrects. |

**Responses**

| Status | When | Body |
|---|---|---|
| `201 Created` | New record appended. `Location: /api/v1/hist/audit-facts/{id}`. | `AuditRecordResponse` |
| `200 OK` | Duplicate `factId` — original record returned unchanged (idempotent replay). | `AuditRecordResponse` |
| `400 Bad Request` | Malformed JSON / missing required field. | ProblemDetail |
| `401 Unauthorized` | Missing/invalid token. | ProblemDetail |
| `403 Forbidden` | Missing `hist:write` scope or tenant mismatch. | ProblemDetail |
| `422 Unprocessable Entity` | Unknown `factType`, forbidden payload keys, `correctsFactId` not found, or future `occurredAt`. | ProblemDetail |

**`AuditRecordResponse`**:

```json
{
  "id": "018f4e2a-0000-7000-8000-0000000000cd",
  "factId": "018f4e2a-0000-7000-8000-000000000001",
  "factType": "evento.confirmado",
  "subject": { "type": "paciente", "id": "018f4e2a-0000-7000-8000-0000000000aa" },
  "actor": { "caregiverId": "018f4e2a-0000-7000-8000-0000000000bb", "displayNameSnapshot": "Ana Costa" },
  "payload": { "eventoId": "…", "verdict": "confirmed", "channel": "webcam" },
  "occurredAt": "2026-08-16T13:59:58Z",
  "recordedAt": "2026-08-16T14:00:00.123Z",
  "streamKey": "paciente:018f4e2a-0000-7000-8000-0000000000aa",
  "sequenceNo": 42,
  "prevHash": "9f2c…64hex",
  "recordHash": "a1b3…64hex",
  "correctsRecordId": null
}
```

**EARS — ingestion**
- WHEN a valid `AuditFactRequest` arrives with a `factId` not yet seen in the tenant, THE SYSTEM SHALL
  append a new `AuditRecord`, assign the next `sequenceNo` for its `streamKey`, compute `recordHash`, and
  respond `201 Created`.
- WHEN a request arrives with a `factId` already present in the tenant, THE SYSTEM SHALL perform no second
  write and SHALL respond `200 OK` with the originally stored record (ADR-005).
- IF `factType` is not in the controlled vocabulary (§5), THEN THE SYSTEM SHALL reject with `422`
  (`type: /errors/unknown-fact-type`) and SHALL NOT append.
- IF `payload` contains any forbidden biometric key (`landmarks`, `image`, `frame`, `video`, `embedding`),
  THEN THE SYSTEM SHALL reject with `422` (`type: /errors/forbidden-payload`).
- WHERE `correctsFactId` is present, THE SYSTEM SHALL resolve it to a prior record in the same tenant and
  SHALL set `correctsRecordId` on the new record; IF it cannot be resolved, THEN THE SYSTEM SHALL reject
  with `422` (`type: /errors/corrects-target-not-found`).
- THE SYSTEM SHALL set `recordedAt` from its own durable clock and SHALL guarantee `recordedAt` is not
  earlier than the prior record's `recordedAt` in the same stream.
- THE SYSTEM SHALL expose no endpoint that updates or deletes an `AuditRecord` (ADR-004).

### 2.2 `GET /api/v1/hist/audit-facts/{id}` — fetch a single record

Returns one `AuditRecordResponse` by its HIST `id`. Requires `hist:read`.

| Status | When |
|---|---|
| `200 OK` | Record found in the caller's tenant. |
| `404 Not Found` | No record with that id in the caller's tenant. |

**EARS**
- WHEN a caller requests an `id` that exists in the caller's tenant, THE SYSTEM SHALL return the record.
- IF the `id` exists only in another tenant's schema, THEN THE SYSTEM SHALL respond `404 Not Found` (never
  leak cross-tenant existence).

---

## 3. History surface — `/api/v1/hist/history`

Read-only timeline and integrity verification. Consumed by DASH and by the `@neuroassist/hist` FE
library. Requires `hist:read`.

### 3.1 `GET /api/v1/hist/history` — timeline query

Returns a paginated, filtered slice of the tenant ledger.

**Query parameters** (all optional; combined with AND):

| Param | Type | Purpose |
|---|---|---|
| `subjectType` | enum | Filter by subject type. |
| `subjectId` | uuid | Filter by subject id (requires `subjectType`). |
| `factType` | string | Exact factType (repeatable for OR-set). |
| `actorCaregiverId` | uuid | Filter by acting caregiver. |
| `from` | timestamptz | Lower bound on `recordedAt` (inclusive). |
| `to` | timestamptz | Upper bound on `recordedAt` (exclusive). |
| `order` | enum | `desc` (default) \| `asc`, by `recordedAt`. |
| `page` / `size` | int | Pagination (§1). |

**Response `200 OK`** — page of `AuditRecordResponse`:

```json
{ "items": [ { "…AuditRecordResponse…" } ], "page": 0, "size": 50, "total": 137 }
```

**EARS — query**
- THE SYSTEM SHALL return records only from the caller's tenant schema, ordered by `recordedAt` per
  `?order` (default `desc`).
- WHEN `subjectId` is supplied without `subjectType`, THE SYSTEM SHALL reject with `400`
  (`type: /errors/incomplete-subject-filter`).
- WHERE a caregiver caller (scope `hist:read`, not `hist:read:all`) queries, THE SYSTEM SHALL restrict
  results to subjects within that caregiver's patient scope (resolved via ACS, §7 / integration-contracts).
- IF `from` is later than `to`, THEN THE SYSTEM SHALL reject with `400` (`type: /errors/invalid-time-window`).

### 3.2 `GET /api/v1/hist/history/verify` — chain verification

Recomputes the hash chain for one `(tenant, subject)` stream and reports integrity (ADR-006).

**Query parameters**

| Param | Type | Req | Purpose |
|---|---|---|---|
| `subjectType` | enum | yes | Stream subject type. |
| `subjectId` | uuid | yes | Stream subject id. |
| `from` / `to` | timestamptz | no | Optional window; verification still walks from the stream genesis to guarantee `prevHash` linkage. |

**Response `200 OK`** (`ChainVerificationResult`):

```json
{
  "streamKey": "paciente:018f4e2a-0000-7000-8000-0000000000aa",
  "status": "intact",
  "recordsVerified": 42,
  "brokenAtSequenceNo": null,
  "verifiedAt": "2026-08-16T14:05:00Z"
}
```

`status` ∈ `{ intact, broken, empty }`. When `broken`, `brokenAtSequenceNo` names the first record whose
recomputed hash or `prevHash` linkage fails.

**EARS — verification**
- WHEN a coordinator/DPO requests verification of a non-empty stream whose records all satisfy
  `record.prevHash == previous.recordHash` and `recordHash == sha256(prevHash ‖ canonical(record))`, THE
  SYSTEM SHALL respond `status: "intact"` with `recordsVerified` = stream length.
- IF any record in the stream fails the hash or linkage check, THEN THE SYSTEM SHALL respond
  `status: "broken"` with `brokenAtSequenceNo` set to the first failing `sequenceNo`.
- WHEN the stream has no records, THE SYSTEM SHALL respond `status: "empty"` with `recordsVerified: 0`.
- THE SYSTEM SHALL compute verification on demand over the requested stream and SHALL NOT mutate any
  record while verifying.

---

## 4. Errors — RFC 7807 ProblemDetail

All errors return `application/problem+json`:

```json
{
  "type": "/errors/unknown-fact-type",
  "title": "Unknown fact type",
  "status": 422,
  "detail": "factType 'evento.xpto' is not in the controlled vocabulary v1.",
  "instance": "/api/v1/hist/audit-facts",
  "correlationId": "c-8f2a…"
}
```

| `type` | HTTP | Meaning |
|---|---|---|
| `/errors/validation` | 400 | Malformed body / missing field. |
| `/errors/incomplete-subject-filter` | 400 | `subjectId` without `subjectType`. |
| `/errors/invalid-time-window` | 400 | `from` after `to`. |
| `/errors/unauthorized` | 401 | Missing/invalid token. |
| `/errors/forbidden` | 403 | Missing scope. |
| `/errors/tenant-mismatch` | 403 | Body tenant ≠ JWT `tenant`. |
| `/errors/not-found` | 404 | Record absent in caller's tenant. |
| `/errors/unknown-fact-type` | 422 | `factType` not in vocabulary. |
| `/errors/forbidden-payload` | 422 | Biometric/forbidden key in `payload`. |
| `/errors/corrects-target-not-found` | 422 | `correctsFactId` unresolved. |
| `/errors/future-occurred-at` | 422 | `occurredAt` beyond skew. |

**EARS — errors**
- THE SYSTEM SHALL return every error as `application/problem+json` with a stable `type`, `status`, and
  the request `correlationId`.
- THE SYSTEM SHALL NOT include stack traces, SQL, or internal identifiers in the `detail` field.

---

## 5. Controlled `factType` vocabulary (v1)

The vocabulary is versioned and co-owned with producers; an unknown type is rejected (§2.1). v1 seed
(extended per producer in that producer's Specs — a documented gap carried from vision doc 06):

| factType | Producer | Subject type(s) |
|---|---|---|
| `evento.confirmado` | EVNT | paciente |
| `evento.descartado` | EVNT | paciente |
| `evento.feedback.registrado` | EVNT | paciente |
| `comando.sensibilidade.editada` | PAC | comando |
| `comando.recalibrado` | PAC | comando |
| `acesso.concedido` | ACS | cuidador |
| `acesso.revogado` | ACS | cuidador |
| `dispositivo.pareado` | EDGE | dispositivo |
| `dispositivo.despareado` | EDGE | dispositivo |
| `registro.corrigido` | any | (subject of corrected record) |

**EARS**
- THE SYSTEM SHALL accept only `factType` values in the active vocabulary version and SHALL expose the
  active version list at `GET /api/v1/hist/fact-types` (read-only, `hist:read`).

---

## 6. Auth — OAuth2 resource server

`hist-service` is an **OAuth2 resource server**; it validates ACS-issued JWTs locally against the ACS JWK
set (no callback). Scopes:

| Scope | Grants |
|---|---|
| `hist:write` | Append audit facts (§2.1). Held by producer service credentials (EVNT/PAC/ACS/EDGE). |
| `hist:read` | Read own-scope history + verify (§2.2, §3). Held by caregivers and DASH. |
| `hist:read:all` | Read across all subjects in the tenant (coordinators/DPO). |

**EARS — auth**
- IF a request presents no valid bearer token, THEN THE SYSTEM SHALL respond `401 Unauthorized`.
- IF a `POST /audit-facts` caller lacks `hist:write`, THEN THE SYSTEM SHALL respond `403 Forbidden`.
- WHERE a reader holds `hist:read` but not `hist:read:all`, THE SYSTEM SHALL constrain results to that
  caregiver's ACS-resolved patient scope.
- THE SYSTEM SHALL validate JWT signature and `tenant` claim locally against the ACS JWK set and SHALL NOT
  call ACS on the request path for token validation.

---

## 7. Cross-service reads (ACS)

For caregiver-scoped reads (§3.1, §6), HIST resolves the caller's patient scope via **ACS**
(`ActorDirectoryPort`). This is the service's only outbound runtime dependency and is detailed in
`integration-contracts.md`.

**EARS**
- WHEN a caregiver-scoped read requires patient-scope resolution and ACS is reachable, THE SYSTEM SHALL
  filter results to the resolved scope.
- IF ACS is unreachable during a caregiver-scoped read, THEN THE SYSTEM SHALL fail closed for
  `hist:read` (respond `503`, `type: /errors/scope-unavailable`) — a scope it cannot verify is never
  widened. (Ingestion is unaffected: `actorRef` is a producer-carried snapshot, ADR-005.)

---

## 8. FE consumption note (`@neuroassist/hist`, route `/historico`)

The FE library consumes **only** the History surface (§3): the `/historico` timeline (period + subject +
factType filters, §3.1) and, for coordinators, the chain-verification badge (§3.2). The FE never calls
the ingestion surface. Correction records surface via `correctsRecordId` — the FE renders the corrective
record linked to the original, both visible (ADR-004). Pagination and `order` map directly to the
timeline's infinite-scroll / period controls.
