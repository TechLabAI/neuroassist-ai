## [INT-FE-ACS-003] Empresa-context writer seam — ACS as sole writer of @neuroassist/shared/empresa-context

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`AcsTenantFacade` is the ONLY writer of `@neuroassist/shared/empresa-context`. It writes the active tenant
only when provisioning completes successfully (201 active or retry-active). Peer modules (PAC, EVNT, EDGE,
DASH, HIST) read it but never write it. The `AcsShellIntegrationService` declares ACS as the writer so the
shell and peers can resolve the source of `X-Empresa-Id`.

**Context / Problem statement:**
- SPEC-06 §2 + FE 05 §1 define ACS as the sole writer of `empresa-context`.
- SPEC-04 §3 (AcsTenantFacade): writes empresa-context on 201 active; holds provisioning on 202.
- The `destroy()` lifecycle (FE-ACS-005) must NOT clear empresa-context (peers depend on it for the session).
- The empresa-context is the source of `X-Empresa-Id` for all subsequent peer module requests.
- Integration test: after tenant creation (201), empresa-context is set; peer modules receive the correct
  `X-Empresa-Id` in their subsequent requests.

**Approach (execution path):**
1. In `AcsTenantFacade.createTenant()` (FE-ACS-007): on 201 active →
   `empresaContextService.setActiveEmpresa({id, nome, tipo})`.
2. In `AcsTenantFacade.retry()`: on active → same write.
3. On 202 provisioning: no write (empresa not yet active).
4. In `AcsBootstrap.destroy()` (FE-ACS-005): do NOT call `empresaContextService.clear()`.
5. In `AcsShellIntegrationService.register()` (INT-FE-ACS-002): call
   `registry.declareEmpresaWriter('acs')`.
6. Integration test: after createTenant(201), read empresa-context → populated; after destroy() → still populated.

**Non-goals:**
- No read logic (peers read empresa-context directly from the shared service).
- No empresa-context implementation (that's `@neuroassist/shared`).

**Layer(s):**
frontend / libs/acs/data-access · libs/acs/integration

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2 tenant-writer seam)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§3 AcsTenantFacade)

**Architecture alignment:**
- Sole-writer pattern; `@neuroassist/shared/empresa-context` is the shared signal; destroy never clears
- Tags Nx: `scope:acs`, `type:data-access` + `type:domain`

**Implementation checklist (by file):**

**Modified (additions to existing files):**
- 🔲 `libs/acs/data-access/src/lib/tenant/acs-tenant.facade.ts` — write empresa-context on active
- 🔲 `libs/acs/shell-entry/src/lib/acs.bootstrap.ts` — verify destroy does NOT clear empresa-context
- 🔲 `libs/acs/integration/src/lib/acs-shell-integration.service.ts` — declareEmpresaWriter('acs')

**Tests (additions):**
- 🔲 `acs-tenant.facade.spec.ts` — 201 active → empresa-context populated; 202 → not populated
- 🔲 `acs.bootstrap.spec.ts` — destroy called → empresa-context still populated
- 🔲 `nx test acs-data-access` PASS

**Milestone:**
Wave 4 / P4 Integrations

**Labels:**
type:integration
layer:frontend
scope:acs
priority:P4

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- POST /tenants → 201 active → empresa-context escrito com `{id, nome, tipo}`
- POST /tenants → 202 provisioning → empresa-context NOT escrito
- bootstrap.destroy() → empresa-context inalterado
- ACS é declarado sole-writer no shell registry

**Dependencies:**
- FE-ACS-007 (AcsTenantFacade)
- FE-ACS-005 (AcsBootstrap)
- INT-FE-ACS-002 (AcsShellIntegrationService)

**Data impact:**
- Escreve `@neuroassist/shared/empresa-context` (session-scoped signal)

**API / Contract impact:**
- Nenhum direto; impacto indireto: X-Empresa-Id presente em todas as requests de peers após o write

**Testing / Evidence required:**
- Testes de unit: write/no-write por estado de provisioning
- Testes de destroy: empresa-context intacto após destruição do módulo

**Risks / Notes:**
- A implementação de `@neuroassist/shared/empresa-context` deve expor uma API de escrita privada
  (write gate) acessível apenas pelo ACS. Verificar se o shared lib já tem essa proteção.
