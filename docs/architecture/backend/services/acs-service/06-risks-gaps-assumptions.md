<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
1. **ACS is the trust anchor / single point of failure for auth.** If ACS is down, no one can sign in
   and no JWK rotation can propagate. Mitigation: stateless verify-local (short-TTL tokens keep peers
   working through a brief ACS outage), cached JWK sets on resource servers, and HA for the sign-in
   path when the pilot grows.
2. **Token TTL vs. revocation gap.** With no server-side session store (MVP), deactivating a caregiver
   or revoking a link only takes effect on the next token issuance/expiry. A too-long TTL widens the
   window in which a revoked caregiver still holds valid access to sensitive health data.
3. **Cross-tenant leakage via a mis-scoped token or query.** ACS is the enforcement root; a bug in
   `tenantId` propagation or in a Hibernate filter could expose another family's alarms — the highest-
   severity failure mode for a healthtech product under LGPD.
4. **Provisioning saga inconsistency.** A `Tenant` marked `active` without its MIG schema actually
   created (or vice-versa) would let downstream services write into a missing schema. The fail-closed
   ordering (schema first, then `active`) must be honored strictly.
5. **Credential handling.** Weak hashing, credential logging, or leaking the raw credential across the
   auth boundary would be a direct breach. Requires hard review at scaffold/implement time.

## Gaps Conhecidos
- **No external IdP / SSO** in the MVP; if a clinic mandates its own directory (LDAP/OIDC), ACS's local
  authority model must federate (deferred — ADR-005).
- **No MFA, no self-service password reset, no account recovery** — operationally handled out of band
  in the pilot.
- **Refresh-token rotation and session revocation** are out of MVP scope; only short-TTL access tokens.
- **Permission catalogue granularity:** the `role → authorities` mapping is coarse (two roles). The
  constitution references a richer `permission-catalogue.arch.json`; the exact authority codes ACS
  emits must be reconciled with it in Specs.
- **JWK key storage / rotation mechanism** (DB vs. external secret manager) is deferred to Specs.

## Suposicoes a Validar
1. Every other service is (or will be) configured as an OAuth2 resource server with `jwk-set-uri`
   pointing at ACS via REG — **confirmed by RAG** for GEST/EDGE/EVNT; must be verified for DASH/HIST/PAC.
2. MIG exposes a runtime provisioning endpoint (not only build-time migrations) that ACS can call to
   create a `t_<tenantId>` schema on demand — **must be confirmed with the MIG module**.
3. `Paciente` ids are resolvable from ACS (via PAC) at link time to validate a linkage — assumes PAC
   exposes a resolve endpoint.
4. In a `familia` tenant there is effectively one caregiver and one patient; the model must still not
   special-case it (a clinic has many of each).
5. The audit facts ACS sends to HIST match HIST's expected contract (event names, payload).
6. `EmpresaContextHolder` / `MultiTenantConnectionProvider` from the shared platform conventions are
   available to ACS as they are to other services.

## Dependencias Criticas
- **MIG (`neuroassist-ai-migrations`)** — required for tenant schema provisioning; ACS cannot activate a
  tenant without it. **Hard, blocking dependency.**
- **REG (Eureka) + GWY** — required for JWK distribution and routing; without discovery, resource
  servers cannot fetch the JWK set.
- **HIST (`hist-service`)** — required for auditability of identity/access changes (LGPD); degraded but
  not blocking if temporarily unavailable (audit calls retryable).
- **PAC (`pac-service`)** — needed to validate `paciente_id` at link time; a link to an unknown patient
  must be rejected.
- **Platform multi-tenancy conventions** (`EmpresaContextHolder`, schema resolver, error model,
  constitution aspects) — ACS both consumes and *anchors* these; drift here breaks isolation.
