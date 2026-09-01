# Backend API Cross-Link — TREI (Frontend ⇄ `trei-service`)

> Authoritative source: `backend/services/trei-service/contracts/api-contract.md`
> (9 endpoints, RFC 7807, OAuth2 two actors, health exposing `embedModelId`). This file
> pins the FE↔BE mapping and records the reconciliations the FE specs applied. **The FE
> never redefines a BE contract — it references it** (command rule).

## 1. Endpoint mapping (FE data-access → authoritative BE endpoint)

| FE operation (SPEC-04) | BE endpoint (`/api/v1/trei`) | Actor / scope | FE facade |
|---|---|---|---|
| `TrainingSession.start` | `POST /sessoes` | caregiver · `treinamento.sessao.conduzir` | `TrainingSessionFacade` |
| `ProposalTurn.propose` | `POST /sessoes/{sessaoId}/propostas` | caregiver · `treinamento.no.propor` | `TrainingSessionFacade` |
| `DraftNode.materialize` | `POST /nos` | caregiver · `treinamento.no.propor` | `ProposalReviewFacade` |
| `DraftNode.inspect` | `GET /nos/{noId}` | caregiver · `treinamento.sessao.conduzir` | `ProposalReviewFacade` |
| `DraftNode.discard` | `DELETE /nos/{noId}?locale=` | caregiver · `treinamento.no.descartar` | `ProposalReviewFacade` |
| `CaregiverApproval.approveCuidadora` | `POST /nos/{noId}/aprovacoes/cuidadora` | caregiver · `treinamento.no.aprovar.cuidadora` | `ProposalReviewFacade` |
| `Publication.status` | `GET /nos/{noId}/publicacao` | caregiver · `treinamento.sessao.conduzir` | `ProposalReviewFacade` (observe) |
| `TreiHealth.readiness` | `GET /health` | caregiver | `TrainingSessionFacade` |
| **— (never called by FE) —** | `POST /nos/{noId}/aprovacoes/paciente` | **patient-with-session** · `treinamento.no.aprovar.paciente` | **none (INV-1, ADR-007)** |

## 2. Reconciliations (FE vision sketch → authoritative contract)

The FE vision docs (02/05) sketched simplified endpoints; the authoritative contract
governs. Applied in SPEC-04 §6:

1. **Conversation = proposal turn.** There is no free-form chat endpoint; each caregiver
   turn is `POST /sessoes/{sessaoId}/propostas` returning `{ propostas[], aiDisclosed }`.
   The assistant "turn" is a rendered summary of the returned proposals.
2. **No draft-list endpoint (GAP).** The contract has no `GET …/proposals`. The FE assembles
   its proposal projection from turn responses + per-node `GET /nos/{noId}`. If a list
   endpoint is later added, `ProposalReviewFacade` gains a `refreshAll` op — additive.
3. **Two-step materialization.** A proposal (`propostaId`) is inert until `POST /nos`
   materializes it into a `rascunho` node (`noId`); only then is it approvable.
4. **Caregiver approval is idempotent — no 409.** The FE vision's "stale-draft 409 on
   approval" does not exist: `POST …/aprovacoes/cuidadora` is an idempotent no-op on
   re-issue (api-contract §2.4). A `409` (`already-published`) arises only on `DELETE` of an
   `ativo` locale (§2.7). SPEC-04/07 model approval failure as 401/403/404/5xx only.
5. **`side` is implicit in the path.** The caregiver side is a distinct endpoint
   (`/aprovacoes/cuidadora`), not a `{side}` body field; the patient side is a different
   endpoint under a different actor identity (ADR-003/ADR-012) the FE never reaches.

## 3. Shared invariants surfaced to the FE

- **G5 (embedding model parity).** `GET /health` exposes `embedModelId`
  (`= TREI_EMBED_MODEL_ID`); `GET /nos/{noId}` records `embeddingModelId` per published
  locale (api-contract §2.6/§2.9, data-model §3). The FE surfaces these read-only (health
  readiness / node inspection); it neither computes nor validates embeddings.
- **Tenancy from JWT.** `empresa`/`pacienteId` derive from the validated token; the FE sends
  `pacienteId` only where the contract requires it (`POST /sessoes`, `POST /nos`) and never
  invents a tenant id (api-contract §1).
- **Correlation.** GWY injects/echoes `X-Correlation-Id`; the FE attaches the returned
  `correlationId` to each `AppError` for observability (SPEC-07 §4).
- **AI disclosure (ADR-017).** `aiDisclosed` from `POST /sessoes` / `POST …/propostas`
  drives the "gerado por IA" banner (SPEC-05 menu badge / SPEC-02 authoring banner).

## 4. Error contract

RFC 7807 `ProblemDetail` (`application/problem+json`) under base
`https://neuroassist.ai/problems/`; the FE-relevant `type` suffixes and their surfaces are
tabulated in SPEC-07 §2. FE-unreachable types (`gate-not-satisfied`, `tts-unavailable`,
`no-biometric-ingress` from a TREI action) are read-model/observational only.
