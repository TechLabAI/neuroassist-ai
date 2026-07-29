# Unresolved questions — discovery ingestion (D-073)

> Source: `D:\01_Trabalho\Cybersoft\TechLab IA\techlab-ba\projects\neuro-assist-ai`
> Generated at: 2026-07-26T23:49:32Z

The discovery ingestion populated `project-context.json` with the fields it could extract confidently. The items below need human input before running `/new-project P1`. Edit `project-context.json` directly, then proceed.

## Open items

_(no unresolved items — every required field was extracted)_
## Field provenance

Which input file each extracted field came from:

- `_extensions.compliance` ← `business/constraints.md`
- `_extensions.data_model` ← `design/data-model.json`
- `_extensions.frontend_reference` ← `design/ (angular-prototype + design-system)`
- `_extensions.lbc_personas` ← `business/lean-business-case.md`
- `_extensions.module_candidates` ← `business/technical-framing.md`
- `_extensions.stakeholder_roles` ← `business/stakeholders.md`
- `_extensions.wireframe` ← `design/wireframe.md`
- `growth` ← `handoff-package.md (§7.1 growth:)`
- `modules.registry` ← `handoff-package.md`
- `multiTenancy.globalSchema` ← `handoff-package.md`
- `multiTenancy.isolationStrategy` ← `architecture/adr/ADR-003-multi-tenancy-schema-per-empresa.md`
- `multiTenancy.mode` ← `handoff-package.md (D-293 normalized: isolation/term decoupled, mode from LBC classification)`
- `multiTenancy.term` ← `architecture/adr/ADR-003-multi-tenancy-schema-per-empresa.md`
- `project.codeName` ← `handoff-package.md`
- `project.description` ← `business/lean-business-case.md`
- `project.displayName` ← `business/lean-business-case.md`
- `project.issuePrefix` ← `handoff-package.md`
- `project.name` ← `business/lean-business-case.md`
- `project.rigorLevel` ← `handoff-package.md`
- `project.segment` ← `handoff-package.md`
- `stack.backend.framework` ← `architecture/adr/ADR-001-backend-jvm-java.md`
- `stack.backend.language` ← `architecture/adr/ADR-001-backend-jvm-java.md`
- `stack.backend.packageBase` ← `handoff-package.md`
- `stack.backend.version` ← `architecture/adr/ADR-001-backend-jvm-java.md`
- `stack.database.engine` ← `handoff-package.md`
- `stack.frontend.framework` ← `architecture/adr/ADR-005-frontend-angular-nx.md`
- `stack.frontend.monorepoTool` ← `architecture/adr/ADR-005-frontend-angular-nx.md`
- `stack.frontend.npmScope` ← `handoff-package.md`
- `stack.messaging.enabled` ← `architecture/adr/ADR-006-arquitetura-hibrida-edge-cloud.md`

## ADRs preserved

10 ADR file(s) copied verbatim to `_inbox/adr/` as discovery history. The framework's own ADR generation produces a parallel set under `docs/architecture/backend/adr/` in P2.

- `_inbox/adr/ADR-001-backend-jvm-java.md`
- `_inbox/adr/ADR-002-ia-embarcada-edge.md`
- `_inbox/adr/ADR-003-multi-tenancy-schema-per-empresa.md`
- `_inbox/adr/ADR-004-arquitetura-hexagonal.md`
- `_inbox/adr/ADR-005-frontend-angular-nx.md`
- `_inbox/adr/ADR-006-arquitetura-hibrida-edge-cloud.md`
- `_inbox/adr/ADR-007-tema-claro-padrao-excecao-ds.md`
- `_inbox/adr/ADR-008-evento-tipado-via-comando.md`
- `_inbox/adr/ADR-009-catalogo-tipo-gesto-extensivel.md`
- `_inbox/adr/ADR-010-evento-unico-sem-tabela-ativo-separada.md`
