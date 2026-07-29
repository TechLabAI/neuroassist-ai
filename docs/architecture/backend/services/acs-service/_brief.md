# ACS — Structured Brief

> Source of truth for the following architect phases (specs, backlog, scaffold). Captures the eight
> brief items for **ACS — Cuidadores e Acesso** (`acs-service`).

## 1. Canonical name and prefix
- **Prefix:** `ACS`
- **Canonical name:** `acs` (used in paths / package `com.neuroassist.acs`)
- **Backend service:** `acs-service`

## 2. Problem the tool solves (1–2 sentences)
A NeuroAssist deployment must know *who* is allowed to see a patient's alarms and history, *in what
role*, and *within which tenant* (a family or a clinic). ACS is the module that provisions the tenant
(empresa), manages caregiver identity and roles, links caregivers to the patient(s) they are
responsible for, and is the authority every other service trusts for authentication and access scope.

## 3. Primary and secondary personas
- **Primary — Jamilly Amaral (family caregiver / `CUIDADORA_FAMILIAR`):** authenticates into the panel,
  is the responsible caregiver for a patient, and receives that patient's alarms.
- **Secondary — Clinic coordinator (`coordenador_clinica`):** in a clinic tenant, manages caregiver
  accounts, assigns caregivers to patients, and has tenant-wide visibility.
- **Indirect — Danilo Amaral (technical conductor):** bootstraps the first tenant and the first
  caregiver account during onboarding; not a day-to-day user.

## 4. MVP cut (minimum viable scope)
1. Tenant (empresa) provisioning — create the `Tenant` (`familia | clinica`) and request its
   per-tenant schema from MIG.
2. Caregiver identity — create/maintain `Cuidador` accounts with credentials and a `papel`
   (`cuidadora | coordenador_clinica`).
3. Authentication — issue a verifiable access token (JWT) carrying tenant id + authorities.
4. Caregiver↔patient linkage — bind a caregiver to the patient(s) they are responsible for; this
   linkage is the routing/scoping source EVNT/EDGE/DASH read.
5. Directory lookups — resolve, by reference, "is this tenant valid?", "which caregivers are
   responsible for patient X?", "what is caregiver Y's role?" for the rest of the system.

## 5. Out of MVP scope
- Self-service sign-up, org billing, SSO/social login, MFA.
- Patient record and command catalog (PAC); device pairing (EDGE); events/notifications (EVNT/DASH);
  audit ledger (HIST).
- Fine-grained per-field permissions beyond the role→authority mapping; delegation/temporary access.
- Any external IdP integration (deferred; see ADR-005).

## 6. Mandatory external integrations
- **MIG (`neuroassist-ai-migrations`)** — centralized Flyway; ACS requests provisioning of a new
  tenant schema (it does not run DDL itself).
- **REG (Eureka) + GWY (Spring Cloud Gateway)** — discovery/routing; ACS publishes the JWK set used by
  every service (as OAuth2 resource server) to validate tokens (`jwk-set-uri` reachable via REG/GWY).
- No third-party APIs in the MVP.

## 7. TechLab AI hub dependencies / inter-module events
- **TechLab AI:** none (`ai.hasAi = false`) — identity and access are explicit administrative
  decisions, not inference.
- **Messaging:** disabled (`stack.messaging.enabled = false`) — no event bus. ACS is consumed
  **synchronously** by PAC (`AcsDirectoryPort`), EVNT (`CaregiverRoutingClient`), EDGE (ACS
  coordination), DASH and HIST via REST. ACS is a provider, not a consumer, of other modules.

## 8. Domain anchor event
- **"Cuidador vinculado ao paciente"** (caregiver linked to patient) — the act that makes a person a
  responsible recipient of a patient's alarms; the linkage every downstream routing/scoping decision
  depends on. Secondary anchor: **"Tenant provisionado"** (tenant provisioned).
