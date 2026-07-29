# 01 — Architectural Vision — ACS (Frontend)

## 1. Intent

At the end, a caregiver setting up NeuroAssist can, in one guided flow, **create her
unit of care** (a family or a clinic), **bring in the people who will actually respond
to Márcio** — herself and at least one backup — give each the right role, and **bind
them to the patient**, always seeing honestly whether that patient is *covered*: at
least one active person is on the hook for an emergency. ACS-FE is the **access and
coverage surface**: it turns "these are the people allowed to help, in these roles,
for this patient" into an explicit, auditable, coverage-honest map. It never detects,
never alarms, and never decides *how* an alarm is delivered — it decides *who is
allowed and expected to catch it*.

## 2. Problem statement

**Current journey (without ACS-FE):** who may see a patient's help requests, and who is
responsible when an emergency fires, lives implicitly — one caregiver, one laptop, no
roles, no backup, no record of who was ever granted or lost access. If that single
person is unavailable, an emergency reaches no one and nothing on screen warned that the
patient was uncovered.

**New journey:** the unit of care is **first-class**. A `Tenant`/empresa (family or
clinic) holds a roster of `Cuidador`es with explicit `papel`, each linked to the
patient(s) they cover. The surface stays honest about **coverage** — a patient with no
active alarm-eligible caregiver is shown as *uncovered*, and the action that would create
that state (unlinking or deactivating the last covering caregiver) is blocked with an
explicit reason (ADR-005). Access is granted and revoked deliberately, and every change
is an auditable fact (HIST), so no one silently gains or loses the ability to respond.

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "Set up my household, invite
  a backup so Márcio is never uncovered, and trust that the panel tells me the truth
  about who is responsible." Uses ACS mostly at setup and when the caregiving
  arrangement changes.
- **Coordenador de Clínica — secondary.** JTBD: "Keep an accurate roster of caregivers
  and which patient each one covers, on/off-board staff, and never leave a patient
  uncovered when a shift ends." Uses ACS as an ongoing administrative surface across
  multiple patients.
- **Danilo Amaral (technical conductor) — onboarding only.** JTBD: "During first setup,
  create the tenant, register the first caregiver, and drive the onboarding wizard from
  tenant through first command before handing over." Owns the wizard once per install.

## 4. MVP scope (in)

- **Tenant bootstrap** — create the `Tenant`/empresa (`tipo = familia | clinica`) and
  establish it as the **active tenant** for the session (`X-Empresa-Id`, schema-per-empresa).
- **Caregiver roster** — invite/register a `Cuidador`, assign `papel`
  (`cuidadora | coordenador_clinica`), list caregivers, deactivate one.
- **Patient ↔ caregiver linkage** — bind/unbind caregivers to a `Paciente` (id owned by
  PAC) as responsible, alarm-eligible responders.
- **Coverage surface** — per patient, an honest indicator of whether it has ≥ 1 active
  alarm-eligible caregiver; block the action that would remove the **last** one (ADR-005).
- **Onboarding wizard shell** — ACS owns the `scope:web` wizard page that sequences the
  multi-module steps (tenant → patient(PAC) → device(EDGE) → first command(PAC) →
  caregiver), composing each module's step feature at page level.
- **Active-tenant / current-caregiver session surface** — the entitlement context the
  shared `empresa-context` / `auth-session` libs expose to every other module.

## 5. Out of scope (MVP)

- Authentication mechanics / credential exchange (login, tokens, IdP/MSAL) — consumed
  from `@neuroassist/shared/auth-session`; ACS authors *authorization*, not the login.
- Patient record, command catalog, calibration (PAC); gesture detection (GEST); event &
  alarm delivery (EVNT — ACS says *who is eligible*, EVNT says *how it's delivered*);
  device pairing/heartbeat (EDGE); audit-ledger storage (HIST — ACS only emits facts).
- Fine-grained per-command / per-screen ACLs, delegation chains, shift scheduling.
- Any asynchronous broker (messaging disabled MVP).

## 6. Architectural principles

- **Standalone-first** — every ACS component is `standalone: true`; the single `NgModule`
  is only the `acs.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — roster, linkage and coverage state live in `signal<T>` inside ACS
  facades, exposed via `.asReadonly()`; no NgRx, no manual `BehaviorSubject`.
- **Lazy-by-default** — loaded via `@neuroassist/acs`; caregiver forms and the wizard
  steps are `loadComponent`-lazy.
- **OnPush mandatory** on every component.
- **Coverage-honest discipline** — the surface never presents a patient as safely
  covered without an active alarm-eligible caregiver, and refuses to silently create an
  uncovered patient (ADR-005 local), mirroring PAC/GEST honest-state discipline.
- **Boundary discipline** — `scope:acs` imports only `scope:acs` + `scope:shared`; the
  wizard composes PAC/EDGE step features only at `scope:web` (nx-boundaries.md, ADR-006).
- **Author access, don't authenticate** — ACS owns roles, linkage and coverage; the
  credential exchange stays entirely in the shared auth layer.

## 7. Success signals

- **Coverage truth**: 100% of patients with zero active alarm-eligible caregivers are
  visibly marked *uncovered*; no unlink/deactivate that removes the last covering
  caregiver ever succeeds silently (asserted in e2e).
- **Setup completion**: a conductor can go from no tenant to a covered patient with a
  first command in a single uninterrupted wizard pass.
- **Role correctness**: a `cuidadora` never sees clinic-roster management affordances; a
  `coordenador_clinica` can manage the roster — provable from the permission map.
- **Auditability**: every access grant/revoke and role change produces a HIST-bound fact
  (no silent entitlement drift).
- **p95 roster-load** ≤ 800 ms and **p95 grant/revoke** ≤ 1 s against the API gateway.
