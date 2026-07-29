<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent
ACS is the **identity and access authority** of NeuroAssist AI: the module that answers *who is this
person*, *what role do they have*, *which tenant do they belong to*, and *which patient(s) are they
responsible for*. It owns the `Tenant` (empresa — família or clínica), the `Cuidador` account, and the
caregiver↔patient linkage. It is the base of the platform's multi-tenancy: no patient, command, event
or device exists outside a tenant that ACS provisioned, and no request is trusted unless it carries an
identity ACS authenticated. PAC knows what a gesture means; GEST knows what is detectable; EVNT knows
what happened; **ACS knows who is allowed to know, and on whose behalf.**

## Problem Statement
A patient's alarms are sensitive health data. Someone must decide which people may see a given
patient's events, in which role, and confine every such decision inside one tenant so a family can
never see another family's data and a clinic caregiver only sees the patients assigned to them. That
requires a single component that (a) provisions the tenant that scopes all downstream data, (b)
manages caregiver accounts and their roles, (c) authenticates a caregiver and issues a token the rest
of the system can verify without calling back, (d) records which caregivers are responsible for which
patients, and (e) answers directory questions ("is this tenant valid?", "who should this patient's
alarm be routed to?", "what authority does this caller have?") for every other service. Today no other
module owns identity; without ACS, multi-tenancy, RBAC and alarm routing have no source of truth.

## Problem Statement — non-goals
ACS does not store the patient record or command catalog (PAC), run computer vision or own the gesture
vocabulary (GEST), generate events or dispatch notifications (EVNT/DASH), pair devices or track
heartbeats (EDGE), or hold the immutable audit ledger (HIST). It authenticates people and scopes
access; it does not decide clinical meaning, detect anything, or render anything.

## Personas
- **Primary — Jamilly Amaral (family caregiver, `CUIDADORA_FAMILIAR`):** signs in to the panel, is the
  responsible caregiver for Márcio, and must receive his alarms. In a `familia` tenant she typically is
  the only caregiver and sees the whole (single-patient) tenant.
- **Secondary — Clinic coordinator (`coordenador_clinica`):** in a `clinica` tenant, creates caregiver
  accounts, assigns caregivers to patients, and has tenant-wide visibility across patients.
- **Indirect — Danilo Amaral (technical conductor):** bootstraps the first tenant and first caregiver
  during onboarding; not a day-to-day user. Never a patient-facing role.
- **Indirect — Márcio Amaral (patient):** never a user of ACS; the person whose data ACS confines to
  the right people. A wrong linkage here is a privacy/safety defect, not a UX annoyance.

## MVP Scope
1. **Tenant provisioning** — create and maintain the `Tenant` (`tipo ∈ {familia, clinica}`) and
   request its per-tenant schema from MIG; ACS is the system of record for the tenant directory in
   `neuroassist_global`.
2. **Caregiver identity** — create, edit and deactivate `Cuidador` accounts with credentials and a
   `papel` (`cuidadora | coordenador_clinica`), strictly scoped to one tenant.
3. **Authentication** — verify credentials and issue a signed access token (JWT) carrying subject,
   `tenantId` and authorities; publish the JWK set so every service validates tokens locally as an
   OAuth2 resource server (no per-request callback to ACS).
4. **Caregiver↔patient linkage** — bind a `Cuidador` to the patient(s) they are responsible for; this
   linkage is the authoritative routing/scoping source consumed by EVNT/EDGE/DASH.
5. **Directory lookups** — synchronous read endpoints resolving tenant validity, a patient's
   responsible caregiver(s), and a caregiver's role, consumed by PAC, EVNT, EDGE, DASH and HIST.

## Out of Scope
- Self-service sign-up, billing/subscription, SSO/social login, MFA, password-reset self-service flows.
- Patient record and command catalog (PAC); device pairing/credentials (EDGE); event generation and
  notification (EVNT/DASH); immutable audit storage (HIST).
- External IdP federation (deferred — ADR-005) and any authorization-server features beyond issuing the
  MVP token (token exchange, refresh-token rotation policies, consent screens).
- Fine-grained per-field/per-resource ACLs beyond the role→authority mapping; delegation/temporary
  access grants.
- Any async broker/event bus (messaging disabled).

## Architectural Principles
1. **ACS is the root of the tenant tree** — every downstream aggregate is scoped to a `Tenant` that
   ACS provisioned; there is no data without a tenant, and no cross-tenant read path in ACS.
2. **Identity is issued once, verified everywhere** — ACS authenticates and mints a JWT; other services
   verify it locally via the published JWK set and never call ACS to authorize a request (stateless,
   fail-fast auth).
3. **Role maps to authorities, not to code branches** — `papel` deterministically resolves to the
   authority set the constitution's `@PreAuthorize` checks expect (RBAC.001); roles are data, not
   `if` statements (ADR-005).
4. **Linkage is the single source of routing and scope** — "who receives this patient's alarm" and
   "which patients this caregiver may see" derive only from the ACS caregiver↔patient linkage; no
   other service reinvents that mapping (ADR-006).
5. **Provisioning delegates DDL to MIG** — ACS decides *that* a tenant schema must exist; MIG owns
   *how* it is created (ADR-003 shared; ADR-004 local). ACS never runs DDL.
6. **Least data, LGPD by default** — ACS stores identity and access metadata only; no biometric,
   image, landmark or clinical event data ever reaches it. Credentials are stored hashed.
7. **Fail closed on identity** — an unknown tenant, an unverifiable token, or an ambiguous linkage
   denies access; access is granted only on an explicit, valid record.

## Success Signals
- A coordinator can create a caregiver account and assign it to a patient, and that caregiver
  immediately (and only) sees that patient's data — with zero visibility into any other tenant.
- Every other service validates an ACS-issued token locally (via the JWK set) and enforces
  `@PreAuthorize` authorities without a runtime callback to ACS.
- EVNT resolves "who to notify" for an emergency purely from the ACS linkage, and the answer matches
  what the coordinator assigned.
- Deactivating a caregiver or unlinking them from a patient removes their access on the next token
  lifecycle, with the change auditable in HIST.
- No request without a valid tenant-scoped identity ever reaches patient data; a token from tenant A
  can never read tenant B's rows.
