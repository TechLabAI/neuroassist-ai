## [FE-ACS-026] Error surface wiring — inline hints + toast + banner (SPEC-07 §1-3 full mapping)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All thirteen `ProblemDetail` error types from SPEC-07 §1 are wired to their correct FE surface (inline /
toast / banner) with pt-BR messages. Inline errors appear on form fields; toasts auto-dismiss for transient
errors; banners persist for session-level problems. No safety-critical error (last-covering, cross-tenant)
is routed here — those are blocking modals (FE-ACS-027).

**Context / Problem statement:**
- SPEC-07 §1 maps 13 BE error types to UI surfaces; §3 defines the decision rule (inline / toast / banner / modal).
- This issue implements the non-modal subset: inline field errors (400 validation, 409 login-duplicate,
  404/422 unknown-patient), toasts (403 insufficient-authority, 502/504 upstream-unavailable), and banners
  (403 tenant-mismatch, 409 tenant-not-active, 409 tenant-suspended).
- The blocking modals (last-covering, cross-tenant) are FE-ACS-027.
- SPEC-07 §4 retry strategy: 502/504 and 409 tenant-not-active are retryable via explicit user action.

**Approach (execution path):**
1. In `AcsCuidadorFacade` and `AcsTenantFacade`: map `AppError.code` to a surface type (inline/toast/banner).
2. Add `errorSurface: 'inline' | 'toast' | 'banner'` to `AppError` (or use a lookup table).
3. In `CaregiverEditorComponent`:
   - 400 validation + login field error → pass to `LoginFieldComponent` as `@Input() fieldError`.
   - Map 404 caregiver-not-found → redirect (not an inline error).
4. In `TenantSetupComponent`:
   - 409 tenant-not-active / tenant-suspended → banner via `TenantSaveStatusComponent`.
   - 403 tenant-mismatch → banner (session-level, persists).
5. `@neuroassist/shared/ui-feedback` toast service: call for 403 insufficient-authority, 502/504.
6. Unit tests: each error type routes to correct surface; toast dismissed; banner persists; redirect on 404.

**Non-goals:**
- No blocking modals (FE-ACS-027).
- No 401 unauthenticated (handled by shared `auth-session`).

**Layer(s):**
frontend / libs/acs/feature-caregiver-editor · libs/acs/feature-tenant-setup · libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 table, §3 decision rule, §4 retry)

**Architecture alignment:**
- `@neuroassist/shared/error-model` AppError; `@neuroassist/shared/ui-feedback` toast service
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Modified:**
- 🔲 `libs/acs/data-access/src/lib/cuidador/acs-cuidador.facade.ts` — map AppError → surface type
- 🔲 `libs/acs/data-access/src/lib/tenant/acs-tenant.facade.ts` — tenant error banners
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/caregiver-editor.component.ts` — inline + redirect wiring
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/components/login-field.component.ts` — @Input() fieldError
- 🔲 `libs/acs/feature-tenant-setup/src/lib/components/tenant-save-status.component.ts` — banner modes

**Tests (additions):**
- 🔲 `caregiver-editor.component.spec.ts` — 400 validation → inline hint; 404 → redirect
- 🔲 `acs-cuidador.facade.spec.ts` — login-duplicate → inline error on login field; 502 → retryable toast
- 🔲 `nx test acs-*` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P7

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-editor` PASS
- `npx nx test acs-data-access` PASS
- 400 validation → campo específico com inline hint; Salvar permanece desabilitado
- 409 login-duplicate → hint inline no campo login; cuidador não salvo
- 403 insufficient-authority → toast auto-dismiss; ação não marcada como concluída
- 403 tenant-mismatch → banner persiste (não toast)
- 409 tenant-not-active → retryable banner (retry button visível)
- 502/504 → retryable toast; ação não marcada como concluída

**Dependencies:**
- FE-ACS-007 (AcsTenantFacade — tenant errors)
- FE-ACS-010 (AcsCuidadorFacade — caregiver errors)
- FE-ACS-016 (editor smart — inline wiring)
- FE-ACS-020 (tenant setup — banner wiring)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consome `ProblemDetail` do BE via AppError mapping

**Testing / Evidence required:**
- Screenshot: campo login com erro "Este login já existe nesta empresa."
- Screenshot: toast 403 insufficient-authority
- Screenshot: banner tenant-not-active (com retry)

**Risks / Notes:**
- `@neuroassist/shared/error-model` deve já ter o mapeamento ProblemDetail → AppError;
  se não, implementar o mapper na camada data-access antes de consumir nas features.
