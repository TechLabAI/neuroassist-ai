# ADR-005 (TREI-local) — LLM authoring conversation via `trei-service` proxy (no browser-side model)

- **Status:** proposed (2026-08-31)
- **Scope:** module-local (TREI frontend)
- **Decisor:** FE Architect (vision phase); ratifies ADR-016 (dual-authorization LLM
  authoring) and the product invariant TECH-007 ("the runtime never generates text").

> Numbered ADR-005 per the generator convention for the module-critical decision. It is a
> **module-local** file and does not collide with the reserved shared ADR-005 slot
> (different directory) — same convention CONV/GEST use.

## Context

TREI is the **only** surface in the product where an LLM produces new content (wireframe
§5.5: `ChatConversationPanel` is "a única tela do produto em que uma LLM gera conteúdo").
The product's core safety property — established for CONV/Tela 06 — is that the **patient
runtime never generates text**; it only navigates and plays curated, dual-approved
content (TECH-007, ADR-015/016). If the *authoring* surface were to call the model from
the browser, the Anthropic API key and the generation capability would live in client
code, and the "no generation at runtime" boundary would be one bundle away from breaking.
The FE must render a live conversation with the assistant without ever holding the model
credential or invoking the model itself.

## Decision

The **LLM conversation is entirely server-mediated by `trei-service`**. TREI-FE:

1. sends the caregiver's turns to `trei-service` and renders the assistant turns
   `trei-service` returns (request/response, or SSE/streaming if `trei-service` exposes
   it — behind the `streamingConversation` feature flag);
2. holds **no** Anthropic SDK and **no** model credential in the browser bundle — the
   `libs/trei/*` and `apps/trei/*` dependency graph must contain no `@anthropic-ai/*`
   package and issue no request to `api.anthropic.com`;
3. **never fabricates generated content** — a proposed `frase`/`categoria` is rendered
   only when it actually arrives from `trei-service` (INV-2). Prompt assembly, moderation,
   and the model call are `trei-service` internals the FE never sees.

The boundary is enforced, not just documented: a dependency/lint check fails the build if
an Anthropic package enters the TREI graph, and an e2e network assertion fails if any
request to `api.anthropic.com` leaves the browser.

## Alternatives considered

1. **Browser calls the Anthropic API directly (with a key or a short-lived token).**
   Rejected: puts generation capability and a credential in client code, directly
   contradicting TECH-007 and making the "runtime never generates" invariant enforceable
   only by trust, not by architecture. Also leaks prompt/moderation logic to the client.
2. **A generic browser-side "AI SDK" abstraction shared with a future runtime feature.**
   Rejected: there is no legitimate browser-side generation anywhere in the product;
   sharing such an abstraction would normalize exactly the thing CONV forbids.
3. **Streaming mandated now (SSE) regardless of the BE.** Rejected as premature: the
   `trei-service` contract is not yet specified (doc 06 G-1/G-2); the decision fixes the
   *boundary* and leaves transport (request/response vs SSE) behind a flag until the BE
   capability is known.

## Consequences

- **Positive:** the product-wide "no client-side generation" invariant is enforced by
  architecture and CI, not by convention; the browser never holds a model credential; all
  prompt/moderation policy stays server-side; the FE stays a thin presentational client of
  one service.
- **Negative:** TREI-FE cannot show assistant output until `trei-service` returns it (no
  purely-local echo of generated content); the conversation UX is bounded by `trei-service`
  latency, requiring first-class `assistant-thinking`/error states (doc 05 §5); a streaming
  UX depends on a BE SSE capability that is not yet contracted (G-2).

## References

- ADR-016 (LLM-assisted authoring, dual authorization) — `project-context.json` modules[TREI]
- TECH-007 / TECH-008 (runtime never generates; no browser-side ElevenLabs/LLM)
- BA wireframe Tela 08 §5.3/§5.5 (`ChatConversationPanel`, AI badge) —
  `techlab-ba/projects/neuro-assist-ai/design/wireframe.md`
- TREI docs 02 §1–2 (system boundary), 04 §6 (streaming vs request/response), 05 §2
- `docs/architecture/frontend/standards/nx-boundaries.md`
