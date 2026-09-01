# 05 — State and Data Flow — TREI (Frontend)

## 1. State topology

Two feature-scoped facades, both `scope:trei`, both Signals-first. No global TREI state
and no NgRx.

| State | Owner | Shape | Visibility |
|---|---|---|---|
| Session lifecycle + conversation turns | `TrainingSessionFacade` | `signal<TrainingSessionVM>` | `.asReadonly()` |
| Active locale (PT/EN) | `TrainingSessionFacade` | `signal<'pt' \| 'en'>` | `.asReadonly()` (single writer) |
| Draft proposals (projected for active locale) | `ProposalReviewFacade` | `signal<ProposedNodeVM[]>` | `.asReadonly()` |
| Derived approval summary | `ProposalReviewFacade` | `computed<ApprovalSummaryVM>` | read-only |
| Caregiver identity / authoring authority | `@neuroassist/shared/access` | shared session | read-only here |

The active-locale signal lives in `TrainingSessionFacade` and is **read** by
`ProposalReviewFacade` (via an injected accessor or a shared locale signal) to re-project
the proposal list — the two facades share the selector, they do not each own a copy
(INV-3).

## 2. Data access pattern

- `HttpClient` inside `trei-service` HTTP adapters in `libs/trei/data-access`; each
  adapter returns typed responses that the facade maps into VMs and pushes into its
  private signal. **No** `BehaviorSubject` plumbing in components.
- **Conversation channel** (ADR-005 local):
  - *Request/response mode* — `POST /trei/sessions/{id}/turns` returns the assistant reply
    (+ any new drafts); facade toggles `assistant-thinking` around the call.
  - *Streaming mode* (if `trei-service` exposes SSE) — the adapter opens an `EventSource`/
    SSE stream at the RxJS edge; each token/chunk is folded into the `turns` signal; the
    component still reads Signals only. The choice is a flag (`streamingConversation`).
- **Proposals** — `GET /trei/sessions/{id}/proposals?locale=<pt|en>` returns the draft
  `NoArvoreFala` projected for the requested locale; re-fetched on `AssistantTurnReceived`
  and `LocaleSwitched`.
- **Caregiver approval** — `POST /trei/nodes/{nodeId}/approval` with
  `{ locale, side: 'cuidadora' }` (the *only* approval the FE can send). The endpoint sets
  `conteudo[locale].confirmadoCuidadora = true`; the FE never sends `side: 'paciente'`
  (INV-1, ADR-007 local).
- All requests carry the caregiver JWT via `@neuroassist/shared/access`; all go through
  GWY. No request ever leaves the browser for `api.anthropic.com`, ElevenLabs, or ES.

## 3. Cache strategy

- **Default: no client cache.** Proposals and conversation are re-fetched on the events
  above; the training session is short-lived and correctness (fresh approval state)
  outweighs a cache. This avoids the classic stale-approval bug where an approved node
  still shows as pending.
- The active session's conversation `turns` are held in the session signal for the
  session's lifetime (not a cache — it *is* the working state); ending the session clears
  it.
- Any future caching (e.g. paging long proposal lists) must be justified by a SPEC and
  must invalidate on approval writes and locale switches.

## 4. Optimistic updates

- **Caregiver approval may be optimistic** *only if* rollback is defined: on "Aprovar",
  optimistically flip `caregiverApproved = true` on the card, then reconcile with the
  server response; on failure (including a stale-draft **409 conflict**), roll back the
  flag, refetch the node, and surface a retriable error asking the caregiver to
  re-confirm (edge case in doc 02 §3). If rollback cannot be cleanly expressed at specs,
  fall back to a non-optimistic write with a per-card pending spinner.
- **Conversation turns are never optimistic for the assistant side** — the assistant turn
  is only rendered when it actually arrives from `trei-service` (INV-2: the FE never
  fabricates generated content). The caregiver's own turn may render immediately.

## 5. Error handling

- `trei-service` returns RFC-7807 `ProblemDetail`; the data-access adapter maps it to
  `AppError` via `@neuroassist/shared/error-model`; the facade sets `status = 'error'`
  and the feature renders a caregiver-facing message through the shared feedback layer.
- **LLM / upstream failures** (Anthropic down, timeout, moderation refusal surfaced by
  `trei-service`) are first-class, retriable errors on the conversation — *not* blank
  states. Existing drafted proposals remain visible and approvable during a conversation
  error (the failure is scoped to the conversation, not the whole screen).
- **Approval conflict (409)** → refetch the node, re-project, ask for re-confirmation.
- **Auth expiry (401)** → shared access layer handles re-auth / redirect to caregiver
  login; TREI does not implement its own login.
- No error path ever exposes an Anthropic/ElevenLabs/ES detail to the caregiver — those
  are `trei-service` internals, mapped to a generic "assistant unavailable" message.

## 6. Breadcrumbs and navigation state

- `libs/trei/navigation` feeds the caregiver shell's breadcrumb with the "Treinamento"
  crumb (and any sub-view crumb, e.g. proposal review deep-link) via the shared
  `BreadcrumbItem[]` contract.
- TREI does **not** own the caregiver shell chrome (sidebar/topbar) — it registers into it
  (ADR-006); its navigation state is limited to the TREI route subtree.
- The active-locale selection is **screen state**, not route state, in the MVP (a
  `LocaleTabs` toggle, not a `:locale` route param); if deep-linkable per-locale views are
  required later, promoting locale to a route param is a specs decision.
