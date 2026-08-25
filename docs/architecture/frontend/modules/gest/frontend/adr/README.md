# ADR Index — gest (GEST)

## Shared ADRs (project-level)

Cross-module FE decisions live in `docs/architecture/frontend/adr/`. This project
materialized **ADR-006** first; ADR-001–005 are **reserved/pending** per that
document's own note (the project did not adopt the generic tax-map numbering).

| ADR | Title | Status |
|---|---|---|
| [ADR-006](../../../../adr/ADR-006-module-entry-as-library.md) | Module entry as library (`@neuroassist/<module>`) | accepted |
| ADR-001..005 (shared) | angular-standalone-signals · nx-boundaries · access-layer · lazy-loading · design-tokens | reserved/pending |

> GEST follows the shared standards in
> `docs/architecture/frontend/standards/` — `module-pattern.md`,
> `nx-boundaries.md`, `access-layer-contracts.md`, `design-system-tokens.md`.

## Local ADRs (module-specific)

| ADR | Title | Status |
|---|---|---|
| [ADR-005](ADR-005-live-test-via-edge-agent-stream.md) | Live gesture test bridges to the local Edge Agent (no browser ML) | **superseded** por ADR-007 |
| [ADR-006](ADR-006-ear-bri-interim-detector.md) | EAR/BRI interim detector (ponte Python) | **superseded** por ADR-007 (sem objeto) |
| [ADR-007](ADR-007-browser-as-sole-detection-engine.md) | O browser é o único motor de detecção | accepted (2026-08-22) |
| [ADR-008](ADR-008-estacao-do-paciente-como-dispositivo.md) | A estação do paciente é um DISPOSITIVO, não um usuário | proposed (2026-08-25) |

> **Como ler 007 e 008 juntas:** a 007 decidiu **onde** a detecção roda (browser, MediaPipe); a 008
> decide **quem a executa e onde ela vive** quando não há cuidadora olhando. Sem a 008, o motor da
> 007 só roda dentro do teste de calibração — que é o estado atual do repositório, e a razão pela
> qual a cadeia gesto → evento → alarme não tem runtime.

> Note: the local ADR is numbered ADR-005 following the generator convention for
> the module-critical decision. It is a **module-local** file and does not collide
> with the reserved shared ADR-005 slot; the two live in different directories.
