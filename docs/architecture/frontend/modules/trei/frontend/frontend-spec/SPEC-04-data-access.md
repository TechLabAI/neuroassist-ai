# SPEC-04 — Data Access — TREI (Frontend)

> The `libs/trei/data-access` contract: one HTTP adapter surface over `trei-service`
> (behind GWY at `/api/v1/trei/**`) plus two signal facades. **Authoritative source: the
> BE `api-contract.md` (`trei-service`, 9 endpoints).** Where the FE vision sketched
> simplified paths (`/trei/sessions/{id}/turns`, `/trei/nodes/{id}/approval`), this SPEC
> **reconciles to the real contract** — see §6 "Contract reconciliation". All requests
> carry the caregiver JWT via `@neuroassist/shared/access`; none ever leaves the browser
> for `api.anthropic.com`, ElevenLabs, or ES (INV-2, ADR-005).

## 1. Master resource table (mapped to the authoritative BE endpoints)

| Resource | Endpoint (public base `/api/v1/trei`) | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| `TrainingSession` | `POST /sessoes` | `TrainingSessionHttpAdapter` | `TrainingSessionFacade` | `start(pacienteId, localeAutoria)` | no |
| `ProposalTurn` | `POST /sessoes/{sessaoId}/propostas` | `TrainingSessionHttpAdapter` | `TrainingSessionFacade` | `propose(instrucao, parentNoId, tipo)` | no |
| `DraftNode` | `POST /nos` · `GET /nos/{noId}` · `DELETE /nos/{noId}?locale=` | `ProposalHttpAdapter` | `ProposalReviewFacade` | `materialize`, `inspect`, `discard` | no |
| `CaregiverApproval` | `POST /nos/{noId}/aprovacoes/cuidadora` | `ProposalHttpAdapter` | `ProposalReviewFacade` | `approveCuidadora(locale)` | no |
| `Publication` | `GET /nos/{noId}/publicacao` | `ProposalHttpAdapter` | `ProposalReviewFacade` | `publicationStatus` (observe only) | no |
| `TreiHealth` | `GET /health` | `TreiHealthHttpAdapter` | `TrainingSessionFacade` | `readiness` (embedModelId, degraded) | no (poll on demand) |

> **INV-1 enforced structurally:** there is **no** `POST /nos/{noId}/aprovacoes/paciente`
> adapter, method, or Port anywhere in `libs/trei/*`. The patient approval endpoint exists
> in the BE contract (§2.5) but is reachable **only** from the patient shell / CONV — TREI
> has no code path to it (ADR-007, doc 03 INV-1).

## 2. Conversation channel (ADR-005 local)

The BE contract models the assisted conversation as a **proposal turn**, not a free-form
chat: each caregiver instruction is `POST /sessoes/{sessaoId}/propostas` with
`{ instrucao, parentNoId, tipo }`, returning `{ propostas[], aiDisclosed }`. The FE maps
this onto the `ChatConversationPanel`:

- caregiver turn (`ConversationTurnVM{author:'caregiver'}`) = the `instrucao` sent;
- assistant turn (`ConversationTurnVM{author:'assistant'}`) = a rendered summary of the
  returned `propostas[]`, appended **only** on the response (INV-2);
- each returned `proposta` becomes a `ProposedNodeVM` with `propostaId` set and
  `nodeId = null` (un-materialized) until the caregiver materializes it (§3).
- `aiDisclosed` from the response drives the "gerado por IA" banner state (ADR-017).

**Transport:** request/response by default; if `trei-service` later exposes SSE, the
adapter opens an `EventSource` at the RxJS edge and folds chunks into the `turns` signal —
selected by the `streamingConversation` feature flag (SPEC-05). Either way the component
reads Signals only; **no** `BehaviorSubject` in components (doc 05 §2).

## 3. Proposal materialization & approval flow

```
POST /sessoes            → sessaoId, llmModelId, aiDisclosed         (start)
POST /sessoes/{id}/propostas → propostas[] (inert, propostaId only)  (one conversation turn)
POST /nos                → noId, subEstado='rascunho', flags=false   (materialize a proposta into a draft node)
POST /nos/{noId}/aprovacoes/cuidadora  { locale }                    (caregiver side ONLY)
        → { confirmadoCuidadora:true, confirmadoPaciente:<unchanged>, subEstado, publicacaoDisparada:false }
GET  /nos/{noId}         → per-locale subEstado + both flags + embeddingModelId + audioRef   (inspect / refresh)
DELETE /nos/{noId}?locale= → subEstado='descartado'                  (discard a rascunho/aguardando draft)
GET  /nos/{noId}/publicacao → passos[embed|tts|index]                (observe post-dual-approval; FE never actuates)
```

- **List semantics (GAP, see §6):** the BE contract has **no** "list all drafts for a
  session" endpoint. The FE therefore assembles `proposals()` from (a) the `propostas[]`
  returned by each turn and (b) per-node `GET /nos/{noId}` refreshes after materialize /
  approve. The `ProposalReviewFacade` holds this projection in its private signal; it is
  **working state**, re-derived from the turn responses, not a cache (doc 05 §3).
- Tenancy (`empresa`, `pacienteId`) is **derived from the JWT** by `trei-service`, never
  sent in body/query beyond the explicit `pacienteId` on `POST /sessoes` / `POST /nos`
  (api-contract §1) — the FE never invents a tenant id.

## 4. Optimistic updates & idempotency

- **Caregiver approval is idempotent** (api-contract §2.4/§1): re-issuing the same
  `{locale}` approval for an already-approved `(noId, locale)` is a server no-op. The FE
  **may** optimistically flip `caregiverApproved = true` on the card, then reconcile with
  the returned flags. Because the BE approval is idempotent (no 409 on duplicate), rollback
  is required **only** for a genuine failure (401/403/404/5xx) — on failure, roll back the
  flag, refetch via `GET /nos/{noId}`, and surface a retriable error (SPEC-07).
- **Materialize (`POST /nos`)** is not optimistic — the card shows a per-card `pending`
  spinner until `noId` returns; only then does the proposal become a draft node.
- **Conversation turns are never optimistic for the assistant side** (INV-2); the caregiver
  turn may render immediately.

## 5. Cache strategy

- **No client cache.** Proposals, node state, and the conversation are re-fetched on turn
  responses, approvals, and locale switches; the session is short-lived and fresh approval
  state (avoiding the stale-approval bug) outweighs a cache (doc 05 §3).
- The active session's `turns` are held in the session signal for the session lifetime
  (working state, not a cache); ending the session clears them.
- `GET /health` is read on demand (e.g. to surface `degradedCapabilities` / `embedModelId`
  when authoring cannot proceed), never polled on a tight loop.

## 6. Contract reconciliation (FE vision sketch → authoritative BE contract)

| FE vision sketch (docs 02/05) | Authoritative BE endpoint | Resolution |
|---|---|---|
| `POST /trei/sessions/{id}/turns` | `POST /api/v1/trei/sessoes/{sessaoId}/propostas` | conversation turn = proposal request; assistant turn rendered from `propostas[]` |
| `GET /trei/sessions/{id}/proposals?locale=` | *(absent)* | **GAP** — no list endpoint; FE assembles the projection from turn responses + `GET /nos/{noId}` |
| `POST /trei/nodes/{id}/approval {side:'cuidadora'}` | `POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora {locale}` | caregiver-side write; `side` is implicit in the path (a distinct endpoint per actor) |
| approval "stale-draft **409**" | approval is **idempotent** (api-contract §2.4) | **no 409 on caregiver approval**; 409 (`already-published`) arises only on `DELETE` of an `ativo` locale |
| (implicit) materialize a draft | `POST /api/v1/trei/nos` | explicit two-step: propose (LLM) → materialize (human) before approve |

These reconciliations are mirrored in `contracts/backend-api-cross-link.md`.

## 7. Criterios de aceite (EARS)

- WHEN the caregiver starts a training session, THE SYSTEM SHALL call `POST /api/v1/trei/sessoes` with `pacienteId` and `localeAutoria` and SHALL store the returned `sessaoId`, `llmModelId`, and `aiDisclosed`.
- WHEN the caregiver submits a conversation turn, THE SYSTEM SHALL call `POST /api/v1/trei/sessoes/{sessaoId}/propostas` and SHALL render the returned proposals as un-materialized proposed nodes.
- WHEN the caregiver materializes a proposal, THE SYSTEM SHALL call `POST /api/v1/trei/nos` and SHALL mark the card pending until a `noId` is returned.
- WHEN the caregiver approves a node for the active locale, THE SYSTEM SHALL call `POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora` with only `{ locale }` and SHALL send no patient-side field.
- THE SYSTEM SHALL provide no data-access operation that calls the patient approval endpoint `POST /api/v1/trei/nos/{noId}/aprovacoes/paciente`.
- WHEN a caregiver approval optimistically flips the caregiver flag and the request then fails, THE SYSTEM SHALL roll back the flag, refetch the node via `GET /api/v1/trei/nos/{noId}`, and surface a retriable error.
- WHEN the same caregiver approval is re-issued for an already-approved node and locale, THE SYSTEM SHALL treat the idempotent success as final and SHALL NOT show a conflict error.
- WHERE the caregiver discards a draft, THE SYSTEM SHALL call `DELETE /api/v1/trei/nos/{noId}?locale=<locale>` and SHALL remove the draft from the active-locale projection.
- THE SYSTEM SHALL send every `trei-service` request with the caregiver JWT via the shared access layer and SHALL issue no request to `api.anthropic.com`, ElevenLabs, or Elasticsearch.
- THE SYSTEM SHALL NOT cache proposals or node approval state across turns and SHALL refetch node state after each approval and locale switch.
