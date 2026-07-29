## [FE-ACS-028] Telemetry event emission for ACS actions (invite, deactivate, link, unlink)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Four key coordinator actions (invite caregiver, deactivate caregiver, link caregiver, unlink caregiver) emit
structured telemetry events via `@neuroassist/shared/observability`. Events carry the action name, outcome
(success/failure/blocked), and anonymized context (no PII in payloads — LGPD). Blocked actions
(last-covering guard) also emit a `blocked` event.

**Context / Problem statement:**
- NeuroAssist AI is health-tech with LGPD compliance; telemetry must not carry PII (nome, login, etc.).
- Useful for understanding coordinator workflows and guard trigger frequency (coverage gap detection).
- Events carry: `module='acs'`, `action=<invite|deactivate|link|unlink>`, `outcome=<success|failure|blocked>`,
  `empresaId` (tenantId — allowed, not personal data), no `cuidadorId` in plain text.
- The shared `@neuroassist/shared/observability` service is used (consistent with other modules).

**Approach (execution path):**
1. In `AcsCuidadorFacade.saveCaregiver(...)`: after success → emit `{action:'invite', outcome:'success'}`;
   on error → emit `{action:'invite', outcome:'failure', errorCode}`.
2. In `AcsCuidadorFacade.deactivate(...)`: after success → emit `{action:'deactivate', outcome:'success'}`;
   on guard block → emit `{action:'deactivate', outcome:'blocked'}`.
3. In `AcsLinkFacade.link(...)`: emit `{action:'link', outcome:'success'|'failure'}`.
4. In `AcsLinkFacade.unlink(...)`: emit `{action:'unlink', outcome:'success'|'blocked'}`.
5. All events: `module:'acs'`; no PII fields (no cuidador nome/login).
6. Unit tests: action success → event emitted; guard block → blocked event; no PII in payload.

**Non-goals:**
- No server-side analytics pipeline (observability service handles routing).
- No performance telemetry (covered by shared infra).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3 feature flags — coverageGuard flag is relevant context)
- LGPD compliance requirement (project-context.json compliance.lgpd=true)

**Architecture alignment:**
- `@neuroassist/shared/observability` service; no PII; best-effort (event failures don't block UX)
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Modified:**
- 🔲 `libs/acs/data-access/src/lib/cuidador/acs-cuidador.facade.ts` — emit invite/deactivate events
- 🔲 `libs/acs/data-access/src/lib/link/acs-link.facade.ts` — emit link/unlink events

**Tests (additions):**
- 🔲 `acs-cuidador.facade.spec.ts` — spy on observabilityService.emit; invite success + failure; deactivate blocked
- 🔲 `acs-link.facade.spec.ts` — link/unlink events
- 🔲 `nx test acs-data-access` PASS

**Milestone:**
Wave 8 / P8 Observability

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P8

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- Invite success → event emitido com `{action:'invite', outcome:'success', module:'acs'}`
- Deactivate blocked → event emitido com `{action:'deactivate', outcome:'blocked'}`
- Nenhum evento contém `nome`, `login`, `senha` ou outros campos PII

**Dependencies:**
- FE-ACS-010 (AcsCuidadorFacade)
- FE-ACS-011 (AcsLinkFacade)

**Data impact:**
- Nenhum no FE; observability service propaga eventos para o backend

**API / Contract impact:**
- Nenhum direto

**Testing / Evidence required:**
- Spy tests confirmando payloads sem PII
- Revisão de LGPD: nenhum campo pessoal nos payloads

**Risks / Notes:**
- Se `@neuroassist/shared/observability` ainda não existir no MVP, criar um stub que apenas loga em
  `console.debug` e documentar como TODO para integração futura.
