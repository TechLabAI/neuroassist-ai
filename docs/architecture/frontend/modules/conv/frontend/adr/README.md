# ADR Index — conv (CONV)

## Shared ADRs (project-level)

Cross-module FE decisions live in `docs/architecture/frontend/adr/`. This project
materialized **ADR-006** first; the generic ADR-001–005 (tax-map / signals-first /
nx-boundaries / access-layer / lazy-loading / design-tokens) are **reserved/pending**
per that document's own note — this project did **not** adopt the generic tax-map
numbering. CONV follows the shared standards in
`docs/architecture/frontend/standards/` rather than re-emitting those ADRs.

| ADR | Title | Status |
|---|---|---|
| [ADR-006](../../../../adr/ADR-006-module-entry-as-library.md) | Module entry as library (`@neuroassist/<module>`) | accepted |
| ADR-001..005 (shared) | angular-standalone-signals · nx-boundaries · access-layer · lazy-loading · design-tokens | reserved/pending |

> CONV follows the shared standards in `docs/architecture/frontend/standards/` —
> `module-pattern.md`, `nx-boundaries.md`, `access-layer-contracts.md`,
> `design-system-tokens.md`.

### Upstream decisions CONV implements (backend/BA ADRs, `accepted`)

These are not FE ADRs but are load-bearing for CONV-FE and treated as fixed:

| ADR | Title | Relevance to CONV-FE |
|---|---|---|
| ADR-012 | Patient session identity (`SessaoPaciente`) | JWT + RBAC `conv.arvore.navegar`/`conv.no.selecionar`; consumed, not owned |
| ADR-013 | CONV as a new module (`NoArvoreFala`) | speech-tree model; node→Comando mapping still open |
| ADR-014 | GEST on the patient phone (Android/Chromium) | gesture input channel reuse; separate restricted patient shell |
| ADR-015 | Elasticsearch semantic search (retrieval-only) | FE consumes the result; never queries ES |
| ADR-017 | TTS via ElevenLabs, cached audio | FE plays cached audio; see local ADR-007 |
| ADR-018 | i18n EN/PT, per-locale authored content | content + audio by active locale; UI i18n mechanism TBD at specs |

## Local ADRs (module-specific)

| ADR | Title | Status |
|---|---|---|
| [ADR-005](ADR-005-agnostic-dual-input-channel.md) | Agnostic dual input channel: gesture ⊕ touch → one selected-node model | proposed |
| [ADR-007](ADR-007-cached-audio-voice-with-web-speech-fallback.md) | Voice output: cached audio playback with Web Speech API fallback | proposed |

> Local numbering note: ADR-005 follows the generator convention for the
> module-critical decision (different directory from the reserved shared ADR-005, no
> collision). The second local ADR is numbered **007** (not 006) so no CONV-local ADR
> shares a number with the **materialized** shared ADR-006 — reusing 006 locally, as
> GEST did, proved confusing.
