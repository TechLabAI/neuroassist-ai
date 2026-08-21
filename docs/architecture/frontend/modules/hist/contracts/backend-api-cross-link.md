# Backend API Cross-Link — HIST (`@neuroassist/hist` ↔ `hist-service` + `evnt-service`)

> This file **references** backend contracts; it does not redefine them. Source of truth:
> `docs/architecture/backend/services/hist-service/contracts/api-contract.md` (History surface).
> The FE consumes **only** the read surface; the ingestion surface (`POST /audit-facts`) is service-to-service.

## 1. Consumed endpoints

| FE operation | Backend endpoint | Contract §Ref | Scope | Notes |
|---|---|---|---|---|
| `HistoryFacade.load` / `loadMore` | `GET /api/v1/hist/history` | §3.1 | `hist:read` / `hist:read:all` | period→`from`/`to`; paged `{items,page,size,total}`; `order=desc` |
| `HistoryFacade.get` (detail, post-MVP) | `GET /api/v1/hist/audit-facts/{id}` | §2.2 | `hist:read` | 404 if absent in tenant |
| `HistoryFacade.verify` (coordinator badge) | `GET /api/v1/hist/history/verify` | §3.2 | `hist:read:all` | `intact` / `broken@N` / `empty` |
| Fact-type facet (optional) | `GET /api/v1/hist/fact-types` | §5 | `hist:read` | active controlled vocabulary v1 |
| `HistoryFacade.markFeedback` | `POST /api/v1/evnt/events/{eventId}/feedback` | evnt-service (see §3 below) | `evnt` write scope | verdict `real`/`falso_positivo`; **owner = EVNT** (ADR / gap G1) |

## 2. NOT consumed by the FE (by design)

| Backend endpoint | Why FE never calls it |
|---|---|
| `POST /api/v1/hist/audit-facts` | Ingestion surface — producer services (EVNT/PAC/ACS/EDGE) only; requires `hist:write` (api-contract §2, §8). |
| any UPDATE / DELETE | None exists — immutable append-only ledger (ADR-004). |

## 3. Feedback ownership (gap G1)

- **Decision reflected here:** the feedback verdict is owned by **`evnt-service`** (EVNT is the system of record; it emits `FalsoPositivoMarcado`). HIST **mirrors** the resulting audit fact into the ledger via its own ingestion path — the FE does **not** write to `hist-service` for feedback.
- The FE guards this with the `feedbackTarget` feature flag (`'evnt' | 'hist'`, default `'evnt'`). If the execution phase resolves G1 toward a `hist-service` proxy, only the flag and `base_path` change; the DTO (`{ verdict }`) is identical.
- **Verify in execution:** confirm the exact `evnt-service` feedback endpoint path and scope against EVNT's api-contract before Backlog wiring.

## 4. Field-level cross-reference (DTO → VM)

`AuditRecordResponse` (api-contract §2.1) → `HistoryItemVm` (SPEC-03 §2). The facade DTO→VM mapping is the single adaptation point (vision R4). Key resolved fields (`label`/`urgency`/`confirmationLine`) are expected server-side (A5); client-side PAC/ACS joins are a documented fallback (gap G2).

## 5. Headers & tenancy

Every call goes through `@neuroassist/shared/access-http-base` → GWY, carrying `Authorization: Bearer`, `X-Empresa-Id`, `X-Correlation-Id`. The tenant is **never** set from client input; the JWT `tenant` claim is authoritative server-side (api-contract §1.1). Cross-tenant reads are impossible by construction.

## 6. Error contract

RFC 7807 `application/problem+json` (api-contract §4) → `AppError` (`@neuroassist/shared/error-model`) → user copy (SPEC-06). Every FE error surface echoes the `correlationId`.

## 7. Open items carried to execution

- **G1** feedback endpoint/owner — confirm against evnt-service api-contract.
- **G2** read-model query contract — confirm period-boundary semantics, pagination shape, and whether `rotulo`/`urgencia`/`confirmadoPor` are pre-resolved.
- **LGPD erasure vs immutability** — still open (M2 warning); does not block FE read specs but affects any future "redacted record" rendering.
