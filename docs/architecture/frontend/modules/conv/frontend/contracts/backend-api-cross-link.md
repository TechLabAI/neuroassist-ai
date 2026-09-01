# Backend API Cross-Link — CONV (Frontend ⟷ `conv-service`)

> Single source of truth for the endpoints CONV-FE consumes. **Do not redefine** the BE
> contract here — this cross-links the FE ports (SPEC-04) to the authoritative
> `conv-service` contracts and records the gaps the FE surface exposes.

## Authoritative BE sources

- `docs/architecture/backend/services/conv-service/contracts/api-contract.md`
- `docs/architecture/backend/services/conv-service/contracts/data-model.md`
- `docs/architecture/backend/services/conv-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/conv-service/design/index-design-arvore-fala.md`

Public surface via GWY: `/api/v1/conv/**` (shared ADR-008). Media type
`application/json`. Auth: OAuth2 Resource Server, patient-with-session JWT (ADR-003 →
shared ADR-012). Mandatory headers: `Authorization: Bearer`, `X-Correlation-Id` (echoed),
`Accept-Language` (optional locale override within `localesSuportados`), `X-Degraded`
(response).

## Endpoint ⟷ FE port matrix

| BE endpoint | Scope | FE Port (SPEC-04) | FE Facade | Notes |
|---|---|---|---|---|
| `GET /api/v1/conv/tree` | `conv.arvore.navegar` | `SpeechTreePort` | `SpeechTreeFacade` | structure locale-invariant; content per active locale; `pending_locale` nodes non-selectable |
| `POST /api/v1/conv/search` | `conv.arvore.navegar` | `SemanticSearchPort` | `SemanticAssistFacade` | kNN retrieval-only; `degraded:true` is soft (200), not an error |
| `POST /api/v1/conv/nodes/{id}/select` | `conv.no.selecionar` | `NodeSelectionPort` | `SpeechTreeFacade`→`TtsPlaybackFacade` | `clientEventId` idempotency; `playbackMode` drives audio vs Web Speech |
| `POST /api/v1/conv/bindings/resolve` | `conv.arvore.navegar` | `BindingPort` | gesture bridge (`integration`) | `engineFeatureRef` opaque; `bound:false` is a no-op |
| `GET /api/v1/conv/health` | none | — (ops only) | — | consumed by GWY/ops, not by CONV-FE UI |
| audio blob at `audioRef.uri` | (patient session) | `AudioPort` | `TtsPlaybackFacade` | resolved from `SelectResponse.audioRef.uri`; exact base to confirm at scaffold |

## Error model (RFC 7807 → FE surface)

The BE `ProblemDetail` `type` suffixes and their FE mapping are specified in
**SPEC-07 §1**. Terminal-session codes (`unauthenticated`, `session-revoked`,
`forbidden-scope`) exit the patient shell (SPEC-06 destroy). `retrieval-unavailable` is a
soft `200` (degraded), never surfaced as an error.

## Locale fallback (api-contract §5) — binding on the FE

| Active-locale content state | `GET /tree` node | `POST /select` outcome | FE rendering |
|---|---|---|---|
| `ativo` | `texto` filled, `selecionavel:true` | 200 text + audioRef | selectable node, speaks in active locale |
| `pendente` / not authored | `texto:null`, `contentStatus:pending_locale`, `selecionavel:false` | 409 `content-pending-locale` | disabled/esmaecido node + inline note; **never** substitute other locale |
| locale ∉ `localesSuportados` | 400 `unsupported-locale` | 400 `unsupported-locale` | banner, revert locale |

The FE **never** substitutes the other locale's text or audio — cross-locale substitution
is a safety violation in assistive health (api-contract §5, SPEC-07 §3, doc 03 §5).

## Events (CONV → HIST) — FE-visible surface

CONV emits audit facts to HIST **synchronously** (`messaging.enabled = false`); no bus,
no FE subscription. The FE-relevant contract is only that the FE supplies inputs to these
facts: `via` (`gesto`|`toque`), `clientEventId`, `locale`, and — for `SpeechNodeSelected`
— the fallback `playbackMode` (`audio-cacheado`|`web-speech`) and `degraded`. The FE does
not consume any event (event-contracts §3).

## Gaps surfaced to specs-validate / BE backlog

### Proposals (Tela 09) — endpoint absent from api-contract v1  ⚠ HIGH
The FE requires `conv-service` endpoints to drive Tela 09:
- `GET /api/v1/conv/proposals?locale=<l>` → pending nodes (`confirmadoCuidadora=true`, `confirmadoPaciente=false`) with `variants[]`.
- `POST /api/v1/conv/proposals/{nodeId}/confirm` → set `confirmadoPaciente=true` (idempotent via `clientEventId`).
- `POST /api/v1/conv/proposals/{nodeId}/adjust` `{ variant }` → choose a variant (no free text).
- `POST /api/v1/conv/proposals/{nodeId}/reject` → drop this locale's proposal.

The data-model backs this (`conv_no_conteudo.confirmado_paciente`, api-contract §2.3
narrative "write confirmadoPaciente=true → BE triggers ES index + TTS synth"), but **no
HTTP endpoint is contracted in api-contract v1**. `ProposalPort` (SPEC-04) is the FE
requirement; scaffold must keep it on the MockAdapter until BE adds the endpoints. The
Tela 09 e2e feature is tagged `@fixme` for this reason.

### `audioRef.uri` fetch route — under-specified  ⚠ MEDIUM
`POST /select` returns `audioRef.uri` (e.g. `conv/p-1024/en/n-42.opus`) but the
audio-fetch endpoint/base is not explicit in api-contract. `AudioPort` assumes it is
served via GWY under the CONV surface; confirm the exact path at scaffold.

### Node → `Comando` → `Evento` mapping — open (ADR-013)  ⚠ LOW
Whether a `frase` selection also emits a structured `Evento` (EVNT) is undecided; the FE
keeps it optional and does not depend on it.

### GEST input-channel abstraction — cross-module contract  ⚠ MEDIUM
The `INPUT_CHANNEL_PORT` shape (SPEC-06 §2) must be agreed with the GEST module so the
composition-root adapter matches the engine's recognized-gesture signal. Avoids a
`scope:conv → scope:gest` import (ADR-005).
