# ADR Index — trei (TREI)

## Shared ADRs (project-level)

Cross-module FE decisions live in `docs/architecture/frontend/adr/`. This project
materialized **ADR-006** first; the generic ADR-001–005 (tax-map / signals-first /
nx-boundaries / access-layer / lazy-loading / design-tokens) are **reserved/pending** per
that document's own note — this project did **not** adopt the generic tax-map numbering.
TREI follows the shared standards in `docs/architecture/frontend/standards/` rather than
re-emitting those ADRs.

| ADR | Title | Status |
|---|---|---|
| [ADR-006](../../../../adr/ADR-006-module-entry-as-library.md) | Module entry as library (`@neuroassist/<module>`) | accepted |
| ADR-001..005 (shared) | angular-standalone-signals · nx-boundaries · access-layer · lazy-loading · design-tokens | reserved/pending |

> TREI follows the shared standards in `docs/architecture/frontend/standards/` —
> `module-pattern.md`, `nx-boundaries.md`, `access-layer-contracts.md`,
> `design-system-tokens.md`.

### Upstream decisions TREI implements (backend/BA ADRs, `accepted`)

These are not FE ADRs but are load-bearing for TREI-FE and treated as fixed:

| ADR | Title | Relevance to TREI-FE |
|---|---|---|
| ADR-016 | TREI as the LLM-assisted authoring module (dual authorization) | the module's reason to exist; server-mediated LLM; two-key approval |
| ADR-015 | Elasticsearch semantic search (retrieval-only) | ES indexing of approved content is a `trei-service` side effect; FE never indexes |
| ADR-017 | TTS via ElevenLabs, cached audio | audio synthesis on dual approval is `trei-service`-side; FE never synthesizes |
| ADR-018 | i18n EN/PT, per-locale authored content | independent per-locale authoring + approval; the core of `LocaleTabs` |

## Local ADRs (module-specific)

| ADR | Title | Status |
|---|---|---|
| [ADR-005](ADR-005-llm-authoring-conversation-via-service-proxy.md) | LLM authoring conversation via `trei-service` proxy (no browser-side model) | proposed |
| [ADR-007](ADR-007-dual-authorization-caregiver-side-proposal-lifecycle.md) | Dual-authorization proposal lifecycle: TREI-FE writes only the caregiver side | proposed |

> Local numbering note: ADR-005 follows the generator convention for the module-critical
> decision (different directory from the reserved shared ADR-005, no collision). The
> second local ADR is numbered **007** (not 006) so no TREI-local ADR shares a number with
> the **materialized** shared ADR-006 — same convention CONV uses.
