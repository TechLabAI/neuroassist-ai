# SPEC-06 — Security

> Service: `evnt-service` · Traces doc 06 gap #1 (device-token auth, still open), ADR-003 (tenant isolation).

## Objetivo
Specify authentication, authorization, tenant isolation, and the privacy invariant for evnt-service, and
carry the still-open device-token decision forward to Backlog with a defined interim contract.

## Escopo
- **In:** ingress device-token auth (interim), caregiver OAuth2 resource-server auth, tenant-isolation
  enforcement, scopes, the LGPD no-biometric rule, correlation propagation.
- **Out:** the final device-token shape (jointly owned with EDGE — open item, tracked in Backlog).

## Decisões técnicas
- **Caregiver endpoints:** OAuth2 resource-server; caregiver session JWT with `tenant` claim + scopes
  `evnt:read`, `evnt:confirm`. Authorization = tenant match AND caregiver is a routing target/role for the
  patient (resolved via ACS).
- **Ingress (`POST /reports`):** device-scoped token. **Interim contract (until EDGE freezes):** JWT-shaped
  device token with claims `deviceId`, `tenant`, `paciente`, `exp`; validated signature + tenant/patient
  consistency. **Open:** mTLS vs OAuth2 `client_credentials` — decided with EDGE in Backlog (doc 06 gap #1).
- **Tenant isolation (ADR-003):** the resolved `empresa` pins the JPA/JDBC schema; no cross-tenant read is
  representable; a tenant mismatch never reveals resource existence (`403`, not `404` leak).
- **Privacy invariant (LGPD hard rule):** EVNT rejects any body containing image/frame/landmark data.

## Casos de borda
- Device token tenant ≠ patient tenant → `403`, no Evento.
- Caregiver JWT tenant ≠ event tenant → `403`, no existence leak.
- Missing `evnt:confirm` scope on confirmation → `403`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL authenticate ingress with a device-scoped token and reject a missing/invalid one with `401`.
- IF a token's tenant differs from the referenced patient/event tenant, THEN THE SYSTEM SHALL return `403`
  and SHALL NOT reveal whether the resource exists.
- WHERE a caregiver session lacks scope `evnt:confirm`, THE SYSTEM SHALL reject confirmation with `403`.
- THE SYSTEM SHALL scope every persistence and lookup to the resolved `empresa`; no cross-tenant access
  SHALL be representable.
- THE SYSTEM SHALL reject any request body carrying image, frame, or facial-landmark data with `400`.
- THE SYSTEM SHALL propagate `X-Correlation-Id` to every downstream call and audit fact.

## Item aberto (para Backlog)
- **Device-token shape (mTLS vs OAuth2 client_credentials)** — jointly owned with EDGE; interim JWT-shaped
  token specified above. Must be frozen with EDGE before scaffold hardening.
