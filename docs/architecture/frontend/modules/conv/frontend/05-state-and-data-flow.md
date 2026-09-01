# 05 — State and Data Flow — CONV (Frontend)

## 1. State topology

| State | Owner | Scope | Notes |
|---|---|---|---|
| Speech-tree levels + selected node | `SpeechTreeFacade` (`libs/conv/data-access`) | feature-local signal | one selected-node slot, written by both channels |
| Playback state | `TtsPlaybackFacade` (`libs/conv/feature-tts`) | feature-local signal | `idle/loading/speaking/fallback/text-only` |
| Semantic-assist result | `SemanticAssistFacade` | feature-local signal | read-model from `conv-service` |
| Pending proposals (Tela 09) | `ProposalConfirmFacade` | feature-local signal | active-locale pending nodes |
| Active locale | `ConvLocaleFacade` (or shared i18n service) | module-scoped signal | drives content + audio selection |
| Patient session / token | `@neuroassist/shared/access` | global | consumed, never owned by CONV |
| Active input channel | `SpeechTreeFacade` (presentational) | feature-local signal | metadata only; no logic branches on it |

No global CONV store; no NgRx. Cross-feature coordination (navigation → playback)
goes through the orchestrator in `libs/conv/integration`, not a shared mutable store.

## 2. Data access pattern

- Each collection has a **Port** (abstract) with a **HttpAdapter** (production) and a
  **MockAdapter** (prototype/Estágio 0) — the exact hexagonal pattern of the
  prototype's `core/data-access.ts` (`Port ← MockAdapter | HttpAdapter`, `Facade`
  injects the Port, UI injects the Facade). `DATA_PROVIDERS` binds Port→adapter; the
  promote/scaffold phase swaps Mock→Http.
- Ports (draft): `SpeechTreePort.listLevel(parentId, locale)`,
  `SemanticSearchPort.search(query, locale)`, `AudioPort.resolve(audioRef)`,
  `ProposalPort.listPending(locale)` / `confirm(nodeId, locale)` /
  `reject(nodeId, locale)`.
- `HttpClient` lives in the adapter; the facade exposes signals — **no manual
  `BehaviorSubject`**. RxJS is confined to the adapter I/O boundary; results land in
  signals.
- All HTTP goes through GWY via the shared access base with the patient-session token.

## 3. Cache strategy

- **Default: call the API per navigation.** The speech tree is small (dozens of nodes
  for one patient), so levels are fetched on demand; a light in-memory cache of the
  already-loaded tree levels for the active locale is justified (navigation is
  back-and-forth and the data is tiny/stable within a session) — invalidated on locale
  switch and on session start.
- **Cached audio** is fetched by `audioRef` on first play and may be kept for the
  session (small MP3/Opus clips) so repeated phrases speak instantly — the *cache
  reference* is BE-owned (ADR-017); the browser only caches the resolved blob for the
  session, never persists it (LGPD/SEC-002).
- Semantic-assist results are **not** cached (query-dependent, cheap, and staleness is
  undesirable).

## 4. Optimistic updates

- **Tela 09 confirmation** may be applied optimistically (remove the node from the
  pending list immediately, reconcile on server ack) because the interaction must feel
  responsive for a motor-limited patient. **Rollback:** on error, re-insert the node
  and surface a non-blocking feedback message; the node stays pending until a
  successful write. This is the only place optimistic update is justified; navigation
  and playback are read-side and need no optimism.

## 5. Error handling

- Backend errors arrive as `ProblemDetail` and are mapped to `AppError` via
  `@neuroassist/shared/error-model`, then surfaced through
  `@neuroassist/shared/ui-feedback` — **never** raw status codes to the patient.
- **Degradation is first-class, not an error:**
  - Semantic search down → silent fallback to manual tree navigation (OPS-003); no
    error shown.
  - Cached audio missing / audio source down → Web Speech fallback (OPS-004); if that
    is also unavailable → prominent text rendering. None of these are surfaced as
    blocking errors.
  - Gesture channel unavailable → touch remains; the channel indicator reflects it.
- Patient-facing error copy is calm, short, and actionable, sized for low motor
  control and possible cognitive load; no technical jargon (mirrors the GEST-FE
  human-language principle).

## 6. Breadcrumbs and navigation state

- `libs/conv/navigation` holds the tree-level path as `BreadcrumbItem[]` (root →
  active category), fed by `SpeechTreeFacade` as the patient descends/ascends.
- The restricted patient shell renders a **minimal** breadcrumb/back affordance (Tela
  06 "‹ Voltar para categorias"), not the full admin breadcrumb bar — consistent with
  the shell having no sidebar and no caregiver routes.
- Navigation never exposes a route outside `{ arvore, confirmar }`; there is no path,
  breadcrumb, or deep link from the patient shell into any caregiver surface.
