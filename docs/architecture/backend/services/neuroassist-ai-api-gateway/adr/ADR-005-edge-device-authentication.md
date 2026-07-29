<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Edge device authentication scheme

## Status
proposta

## Contexto
The gateway authenticates two distinct principal kinds. Caregiver dashboard sessions use
a standard OIDC/JWT flow validated against ACS's JWK set — well understood. **Edge agents
are different:** they are headless, long-lived processes on patients' devices that push
structured events with a local retry outbox (ADR-006 hybrid edge/cloud). They have no
interactive login, must authenticate deterministically after restarts and network gaps,
and their credential must be bound to a specific `empresa`/patient so tenant context can
be resolved at the edge. Candidate schemes:

1. **Signed device token (JWT-like), issued at pairing** — device receives a long-lived
   signed token (or a refreshable pair) during EDGE pairing; gateway validates signature
   and reads the bound `empresa_id`.
2. **mTLS client certificate per device** — device presents a client cert provisioned at
   pairing; strongest transport-level identity but heavier PKI/rotation operations.
3. **Static API key per device** — simplest, but weak (no expiry, hard to rotate, high
   blast radius on leak).

This is the critical domain decision for the gateway because it defines the trust anchor
for all edge-originated events and couples the gateway to the EDGE pairing design.

## Decisão
We **propose** scheme **1 — a signed device token issued during EDGE pairing**, validated
by the gateway the same way (JWK/issuer) as caregiver JWTs, carrying a `device_id` and the
bound `empresa_id` claim. This unifies the gateway's validation path (one JWK-based
verifier for both principal kinds), gives expiry/rotation semantics a static key lacks,
and lets the gateway resolve `X-Empresa-Id` directly from the token without a per-request
lookup. mTLS (option 2) is kept as a **future hardening** path if transport-level device
identity becomes a requirement. Status is **proposta** because the exact token lifetime,
rotation/refresh flow and revocation mechanism depend on the EDGE pairing design (gap G-1)
and must be confirmed with EDGE/ACS before Specs finalise the auth contract.

## Consequências
**Positivas**
- Single validation mechanism (JWK-based) for both caregiver and device principals →
  simpler, uniform `JwtAuthenticationFilter` / `DeviceCredentialFilter` code path.
- Tenant context (`empresa_id`) travels in the token → no extra lookup at ingest time.
- Expiry + rotation reduce blast radius versus a static key.

**Negativas / trade-offs aceitos**
- Requires a device-token issuance + rotation/revocation flow in EDGE/ACS (added scope,
  cross-module dependency).
- Long-lived tokens on devices are a theft risk if the device is compromised — needs
  revocation and reasonable TTL; not as strong as mTLS.
- Decision is provisional (depends on G-1); Specs must not hard-freeze the edge auth
  contract until pairing design is confirmed.
