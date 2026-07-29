# 06 — Risks, Gaps, Assumptions — ACS (Frontend)

## 1. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Silent uncovered patient** — a caregiver is unlinked/deactivated (or the last one leaves a clinic shift) and the patient is left with no alarm-eligible responder without anyone noticing. | Safety-critical (an emergency reaches no one). | ADR-005 local: block the unlink/deactivate that would remove the last covering caregiver; mark uncovered patients `sem cobertura` (danger), never hide them. |
| R2 | **Role ≠ coverage confusion** — implementers conflate `papel` (admin capability) with alarm eligibility, so a role change is wrongly treated as a coverage change (or vice-versa). | Wrong entitlement or false coverage. | Doc 03 §5 invariant separates the two; coverage is derived only from active linkage, never from `papel`. |
| R3 | **Active-tenant context contract drift** — ACS is the *writer* of `shared/empresa-context`; if the write contract (when/how the active tenant is set and cleared) is loose, other modules read a stale or empty tenant and mis-scope calls. | Cross-tenant data leak or empty-scope failures. | Freeze the `empresa-context` writer contract in specs (A1); ACS sets it only on confirmed tenant create/select and clears on logout. |
| R4 | **Boundary temptation** — implementers importing `scope:pac`/`scope:edge` directly from ACS to build the onboarding wizard. | Hard nx-boundaries violation / drift. | Wizard composed at `scope:web` (doc 02 §4, doc 04 §5); ESLint enforces; ACS exposes only its own step features. |
| R5 | **Client-only guard mistaken for security** — the coverage guard and coordinator-only affordances are enforced only in the UI, and the backend does not re-check. | Bypass via direct API call; false safety. | Doc 05 §4: FE guard is a courtesy; BE must re-enforce coverage (409/422) and role entitlement (freeze in specs). |
| R6 | **Onboarding ownership erosion** — the wizard mixes ACS (tenant, caregiver), PAC (patient, first command) and EDGE (device); unclear ownership leaks other modules' logic into ACS. | Module boundary erosion. | ACS owns only sequencing + its own steps; the `scope:web` shell orchestrates; each step feature stays in its module. |
| R7 | **LGPD / over-collection on caregivers** — a caregiver form could invite personal data beyond what access needs. | Privacy / compliance. | Collect the minimum (`nome`, `papel`, linkage); no health or sensitive personal fields; access/role changes audited in HIST. |

## 2. Known gaps

- The **active-tenant writer contract** on `@neuroassist/shared/empresa-context` (exact
  API ACS calls to set/clear the active tenant, and what happens on multi-tenant clinic
  users switching tenants) is not yet frozen — settle in specs (Assumption A1). Coverage
  and every other module's scoping depend on it.
- Whether caregiver onboarding is **invite-by-email** (a pending caregiver who later
  accepts) or **direct-register** (created active immediately) is not fixed; MVP assumes
  direct-register (`inviteByEmail` flag off). Affects the roster's `pending` state model.
- The **alarm-eligibility contract consumed by EVNT** (does EVNT pull "eligible caregivers
  for patient X" from ACS, or does ACS push a snapshot?) is undefined on the ACS side —
  depends on EVNT's routing design (messaging disabled → assume synchronous read).
- **Tenant switching** for a caregiver who belongs to more than one tenant (a clinic
  coordinator who is also a family caregiver) is not modeled in MVP; single active tenant
  per session assumed.
- Whether `deactivate` is reversible (re-activate a caregiver) and how that interacts with
  prior linkage is not specified.

## 3. Assumptions to validate

- **A1** — `@neuroassist/shared/empresa-context` accepts ACS as its **single writer**,
  with a defined set/clear API and a guarantee that reads observe the last confirmed write.
  (Validate with the shared-libs / platform specs — cross-cutting, highest priority.)
- **A2** — The caregiver roster and per-patient linkage stay **small** (family: 1–3
  caregivers; clinic: tens), so fetch-on-navigation without paging is adequate.
- **A3** — A `Paciente` may exist (created in PAC onboarding step 2) **before** any
  caregiver is linked; ACS therefore must tolerate and honestly surface an initially
  `sem-cobertura` patient rather than assuming coverage is always present.
- **A4** — The backend re-enforces the coverage guard and role entitlement; the FE block is
  a UX courtesy, not the authorization boundary.
- **A5** — Onboarding is conducted once by the technical conductor, who has the highest
  entitlement; the daily `cuidadora` does not need tenant-creation affordances.

## 4. Critical dependencies

- **acs-service (BE)** — tenant, caregiver and linkage CRUD, tenant-scoped; system of
  record for access. (No BE `_brief.md` existed at FE generation — BE vision should
  synchronise from this module's `_brief.md`.)
- **`@neuroassist/shared/empresa-context`** — the active-tenant context ACS **writes** and
  every module reads (A1). The single most load-bearing dependency.
- **`@neuroassist/shared/auth-session`** — the authenticated principal ACS assigns a role
  to (ACS does not authenticate).
- **PAC** — the `Paciente` read model caregivers are linked to.
- **EVNT** — downstream consumer of alarm-eligibility; the eligibility semantics authored
  here must match EVNT's routing policy.
- **HIST** — records access/role/linkage changes as immutable audit facts.
- **Shared libs** — `access-http-base`, `empresa-context`, `auth-session`,
  `access-decision`, `contracts`, `error-model`, `ui-*`.
- **Design tokens** — `@neuroassist/shared/ui-tokens` (`--color-danger` for *sem
  cobertura*, `success`, `brand`).
