# SPEC-04 — API Integration — HIST (`@neuroassist/hist`)

> Source of truth: `hist-service` **api-contract.md** (History surface §3, §8). The FE consumes **only** the read
> surface; it never calls the ingestion surface (`POST /audit-facts`). Feedback write targets `evnt-service`
> (per FE ADR / gap G1), guarded by the `feedbackTarget` feature flag. Traces vision doc 05 §2, doc 02 §2.

## 1. Resource master table

| Resource | Endpoint | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| `History` | `GET /api/v1/hist/history` | `HistClient` | `HistoryFacade` | `list` (paged, period→from/to) | in-session pages per period; refetch on entry |
| `HistoryRecord` | `GET /api/v1/hist/audit-facts/{id}` | `HistClient` | `HistoryFacade` | `get` (post-MVP detail) | none |
| `ChainVerification` | `GET /api/v1/hist/history/verify` | `HistClient` | `HistoryFacade` | `verify` (coordinator badge) | none |
| `FactTypes` | `GET /api/v1/hist/fact-types` | `HistClient` | `HistoryFacade` | `list` (vocabulary, optional facet) | session-static |
| `Feedback` | `POST /api/v1/evnt/events/{id}/feedback` **(evnt-service, via flag)** | `FeedbackClient` | `HistoryFacade` | `create` (non-optimistic) | none |

## 2. HTTP edge (`HistClient`, `libs/hist/data-access`)

- Built on `@neuroassist/shared/access-http-base` — attaches `Authorization: Bearer`, `X-Empresa-Id`, and `X-Correlation-Id` (generated at GWY if absent, api-contract §1.1). The FE **never** sets the tenant from user input.
- RxJS lives **only** here; the facade converts observables to signals. Components never subscribe to HTTP.
- Base path `/api/v1/hist`, routed through GWY, discovered via REG.

### 2.1 `list` — timeline query

```
GET /api/v1/hist/history?from={ISO}&to={ISO}&order=desc&page={n}&size={pageSize}
    [&subjectType=...&subjectId=...&factType=...&actorCaregiverId=...]
→ 200 { items: AuditRecordResponse[], page, size, total }
```

- Facade maps `period` → `from`/`to` (SPEC-03 §5). Never sends `subjectId` without `subjectType` (BE `400 /errors/incomplete-subject-filter`).
- Scope: caregivers hold `hist:read` → results are ACS-restricted to their patient scope; coordinators/DPO hold `hist:read:all` (api-contract §6).

### 2.2 `get` — single record (post-MVP detail)

```
GET /api/v1/hist/audit-facts/{id} → 200 AuditRecordResponse | 404
```

### 2.3 `verify` — chain verification (coordinator badge)

```
GET /api/v1/hist/history/verify?subjectType={t}&subjectId={id} → 200 ChainVerificationResult
    status ∈ { intact, broken, empty }
```

- Rendered as a tamper-evidence badge for coordinators (`hist:read:all`); hidden for plain caregivers.

### 2.4 `create` — feedback verdict (evnt-service)

```
POST /api/v1/evnt/events/{eventId}/feedback  { verdict: 'real' | 'falso_positivo' }
→ 200/201 confirmed | 409 already-terminal | 403 | 5xx
```

- **Non-optimistic** (SPEC-03 §4): item verdict is set only on success. `feedbackTarget` flag switches the target to `hist-service` proxy if the execution phase resolves G1 that way; the DTO is identical.

## 3. DTO → VM mapping (`AuditRecordResponse` → `HistoryItemVm`)

| DTO field | VM field | Transform |
|---|---|---|
| `id` | `id` | passthrough |
| `factType` | `factType` | passthrough (not shown as primary label) |
| `payload.rotulo` / resolved label | `label` | human-readable `Comando.rotulo`; fallback to a friendly derivation, never the raw code |
| `payload.urgencia` / resolved | `urgency` | map to `Urgency` enum |
| `occurredAt` | `occurredAtLabel` | localize to tenant locale (`14h32`) on render |
| `recordedAt` | `recordedAtIso` | passthrough (order key) |
| `actor.displayNameSnapshot` + `payload` | `confirmationLine` | compose "Você confirmou às HH'h'mm" |
| `payload.verdict` | `verdict` | `real`/`falso_positivo`/null |
| `correctsRecordId` | `correctsRecordId` | passthrough |

The facade DTO→VM mapping is the **single adaptation point** (vision R4); if the read model returns unresolved fields (fallback A5), client-side PAC/ACS joins happen here, nowhere else.

## 4. Error mapping

Backend `application/problem+json` (RFC 7807) → `AppError` via `@neuroassist/shared/error-model`. Full mapping in SPEC-06. All error surfaces carry the `correlationId` echoed from the response for support traceability.

## Criterios de aceite (EARS)

- WHEN the facade issues a timeline query, THE SYSTEM SHALL send `Authorization`, `X-Empresa-Id`, and `X-Correlation-Id` via the shared `access-http-base` and SHALL NOT set the tenant from client input.
- THE SYSTEM SHALL map the active `period` to `from`/`to` bounds and SHALL request pages with `order=desc` unless the caller opts into ascending order.
- WHEN a caregiver marks feedback, THE SYSTEM SHALL POST to the target resolved by `feedbackTarget` and SHALL set the item verdict only after a successful response (non-optimistic).
- IF the backend responds `409` to a feedback write, THEN THE SYSTEM SHALL reconcile the item to the backend verdict and SHALL NOT override it client-side.
- IF a timeline query would include a `subjectId` without a `subjectType`, THEN THE SYSTEM SHALL omit the subject facet rather than send a filter the backend rejects with `400`.
- WHERE the caller holds `hist:read:all`, THE SYSTEM SHALL enable the chain-verification badge; WHILE the caller holds only `hist:read`, THE SYSTEM SHALL hide it.
- THE SYSTEM SHALL surface the response `correlationId` on every error state for support traceability.
