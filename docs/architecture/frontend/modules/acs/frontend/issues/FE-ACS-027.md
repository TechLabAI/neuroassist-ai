## [FE-ACS-027] Coverage invariant blocking modal + cross-tenant link blocking modal (ADR-005, SPEC-07 §2)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Two safety-critical error cases raise a **blocking modal** (not a toast, not a banner): (1) last-covering
caregiver deactivation/unlink guard is bypassed and BE returns 409/422; (2) cross-tenant link rejection
(422 cross-tenant-link). The modal cannot be dismissed without the coordinator taking explicit action. Coverage
is re-derived from a fresh fetch after either modal; nothing is marked as saved/linked.

**Context / Problem statement:**
- SPEC-07 §2 defines both scenarios as fail-closed and blocking — safety-critical.
- "Last-covering block": triggered both by the client-side guard (FE-ACS-010/011) and as a BE fallback (409/422).
  In both paths, the UI must show the blocking modal and leave the cuidador/vinculo unchanged.
- "Cross-tenant link": 422 from BE; the coordinator must fix the issue before proceeding.
- The message for last-covering: "Este paciente ficaria sem cuidador responsável — vincule ou ative outro antes."
- The message for cross-tenant: "Cuidador e paciente pertencem a empresas diferentes — o vínculo não é permitido."
- Both modals force a conscious resolution; they must NOT be dismissible as a simple toast.

**Approach (execution path):**
1. Create a `CoverageBlockModalComponent` (standalone, OnPush) — displays message + single "Entendido" CTA that
   triggers a fresh re-fetch of coverage. No "Cancel" without understanding the issue.
2. Create a `CrossTenantBlockModalComponent` (standalone, OnPush) — similar modal for cross-tenant-link.
3. In `AcsCuidadorFacade.deactivate`: when guard blocks OR BE returns 409 → raise modal via modal service +
   re-derive coverage from fresh fetch.
4. In `AcsLinkFacade.unlink`: same pattern for 422/409 last-covering block.
5. In `AcsLinkFacade.link`: when BE returns 422 cross-tenant-link → raise CrossTenantBlockModal + coverage
   re-fetched and re-derived.
6. Use `@neuroassist/shared/ui-feedback` modal service (or `DialogService` — check the shared API).
7. Unit tests: facade raises modal on guard bypass + BE block; coverage re-derived; nothing marked saved.

**Non-goals:**
- No dismissible toast for these two cases.
- No auto-retry (user must consciously resolve).

**Layer(s):**
frontend / libs/acs/data-access · libs/acs/feature-caregiver-roster · libs/acs/feature-patient-linkage

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-07-error-handling.md` (§2 coverage invariant, §3 modal rule)

**Architecture alignment:**
- Blocking modal (not toast); fail-closed; coverage re-derived after modal; ADR-005 enforced in both paths
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**New/modified:**
- 🔲 `libs/acs/feature-caregiver-roster/src/lib/components/coverage-block-modal.component.ts` + `.html`
- 🔲 `libs/acs/feature-patient-linkage/src/lib/components/cross-tenant-block-modal.component.ts` + `.html`
- 🔲 `libs/acs/data-access/src/lib/cuidador/acs-cuidador.facade.ts` — raise modal on 409 deactivate block
- 🔲 `libs/acs/data-access/src/lib/link/acs-link.facade.ts` — raise modal on 422/409 unlink block + 422 cross-tenant

**Tests (additions):**
- 🔲 `acs-cuidador.facade.spec.ts` — guard bypass + BE 409 → modal raised; coverage re-derived; cuidador stays active
- 🔲 `acs-link.facade.spec.ts` — unlink block → modal; cross-tenant 422 → modal; coverage re-fetched
- 🔲 `nx test acs-data-access` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P7

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- Guard bypass + BE 409 deactivate → modal visível; cuidador permanece ativo; coverage re-fetched
- Guard bypass + BE 422/409 unlink → modal visível; vinculo inalterado; coverage re-fetched
- 422 cross-tenant-link → modal visível; link não criado; coverage inalterada
- Modal NÃO dismissível por toast-click acidental (requer botão explícito)
- E2E: bypassed last-covering → blocking modal (caregiver-roster.feature 12; patient-linkage.feature 6)

**Dependencies:**
- FE-ACS-010 (AcsCuidadorFacade — deactivate guard)
- FE-ACS-011 (AcsLinkFacade — unlink guard + cross-tenant)
- FE-ACS-012 (CoverageFacade — re-derive after modal)

**Data impact:**
- Nenhum persistido

**API / Contract impact:**
- Consome respostas 409/422 do BE (api-contract §8)

**Testing / Evidence required:**
- Screenshot: modal bloqueante de last-covering (deactivate)
- Screenshot: modal bloqueante de cross-tenant-link

**Risks / Notes:**
- **Prioridade de segurança:** esta issue é safety-critical. Se o modal service compartilhado (`ui-feedback`)
  não suportar modais não-dismissíveis sem CTA explícita, implementar um modal inline no componente.
- O modal deve deixar claro QUE paciente ficaria sem cobertura — include patient name in the message.
