# ADR Index — dash (DASH)

## Shared ADRs (project-level)

Cross-module FE decisions live in `docs/architecture/frontend/adr/`. This project
materialized **ADR-006** first; ADR-001–005 are **reserved/pending** per that
document's own note (the project did not adopt the generic tax-map numbering).

| ADR | Title | Status |
|---|---|---|
| [ADR-006](../../../../adr/ADR-006-module-entry-as-library.md) | Module entry as library (`@neuroassist/<module>`) | accepted |
| ADR-001..005 (shared) | angular-standalone-signals · nx-boundaries · access-layer · lazy-loading · design-tokens | reserved/pending |

> DASH follows the shared standards in `docs/architecture/frontend/standards/` —
> `module-pattern.md`, `nx-boundaries.md`, `access-layer-contracts.md`,
> `design-system-tokens.md`, `bootstrap-flow.md`.

## Local ADRs (module-specific)

| ADR | Title | Status |
|---|---|---|
| [ADR-005](ADR-005-server-driven-home-summary.md) | Server-driven calm home summary + shell-level cross-module composition | proposed |

> Note: the local ADR is numbered ADR-005 following the generator convention for the
> module-critical decision. It is a **module-local** file and does not collide with the
> reserved shared ADR-005 slot; the two live in different directories.
