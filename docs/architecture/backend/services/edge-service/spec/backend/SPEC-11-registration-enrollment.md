# SPEC-11 — Device Registration & Enrollment (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge (**bootstrap trust**)
> **ADR refs:** ADR-005 (registration credentials the device; operator-driven)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify how an edge agent goes from nothing to an authenticated, credentialed identity: operator-driven
registration, a one-time enrollment token (bootstrap trust — doc 06 G4), and credential issuance. This is
the cold-start path (doc 01 Success Signals: register → pair → first heartbeat → profile pointer).

## 2. Scope

**In:**
- `POST /devices` (operator registration + enrollment-token minting)
- `POST /devices/enroll` (agent exchanges token → credential)
- `POST /devices/{id}:retire` (revoke credential, terminal)
- Enrollment token lifecycle (TTL, single-use, hash-stored)

**Out:**
- Credential mechanism details/rotation (SPEC-06); pairing (SPEC-03); heartbeat (SPEC-02)

## 3. Technical decisions

| Decision | Ref |
|---|---|
| Registration is operator-driven (`edge.manage`); device is never self-registered | ADR-005 |
| Two-step trust: register (operator) then enroll (agent) — no shared secret | doc 06 G4 |
| Enrollment token: one-time, TTL 900 s default, stored hashed | api-contract §2, data-model §2.2 |
| Credential = OAuth2 `client_credentials` per device; secret returned once, stored hashed | SPEC-06 §3.2 |

## 4. Registration (EARS)

- WHERE the caller lacks `edge.manage`, THE SYSTEM SHALL respond `403 /errors/insufficient-scope`.
- WHEN an operator registers a device, THE SYSTEM SHALL create it in state `registered`, mint a one-time
  enrollment token, store only its hash, set `enrollmentExpiresAt = now + ttl`, and return the token
  exactly once.
- THE SYSTEM SHALL treat `pacienteHint` as advisory only and SHALL NOT pair on registration.
- WHEN registration commits, THE SYSTEM SHALL raise `DispositivoRegistrado` post-commit.

## 5. Enrollment (EARS)

- WHEN a valid, unexpired, unconsumed enrollment token is presented at `/devices/enroll`, THE SYSTEM
  SHALL issue the device credential (returning `clientId` + `clientSecret` once), persist only the secret
  hash, mark the token consumed, and record the initial `versao`.
- THE SYSTEM SHALL perform token-consumption and credential-issuance in one transaction (SPEC-05 §5).
- IF the token is unknown, expired, or already consumed, THEN THE SYSTEM SHALL respond
  `401 /errors/enrollment-invalid` without revealing which condition failed.
- IF the device is already enrolled (state beyond `registered`), THEN THE SYSTEM SHALL respond
  `409 /errors/already-enrolled`.
- THE SYSTEM SHALL keep the enroll endpoint outside the Bearer requirement — the single-use token is the
  proof (api-contract §6.1).

## 6. Retire (EARS)

- WHERE the caller lacks `edge.manage`, THE SYSTEM SHALL respond `403 /errors/insufficient-scope`.
- WHEN a device is retired, THE SYSTEM SHALL revoke its active credential, drop any active pairing
  (audited), and move it to the terminal `retired` state.
- THE SYSTEM SHALL never hard-delete the device row (doc 05 Data Lifecycle).

## 7. Cold-start sequence

```
1. Operator: POST /devices                → device (registered) + enrollmentToken (once)
2. Agent:    POST /devices/enroll {token}  → clientId + clientSecret (once)
3. Agent:    ACS /oauth2/token (client_credentials) → device access token (edge.device)
4. Operator: POST /devices/{id}/pairings   → device (paired to patient)   [SPEC-03]
5. Agent:    POST /heartbeat (device token)→ online + profile pointer      [SPEC-02]
```

## 8. Edge cases

| Scenario | Behavior |
|---|---|
| Enroll with expired token | `401 /errors/enrollment-invalid` |
| Enroll twice (token consumed) | `401 /errors/enrollment-invalid` |
| Enroll an already-enrolled device | `409 /errors/already-enrolled` |
| Register without `edge.manage` | `403 /errors/insufficient-scope` |
| Retire an already-retired device | idempotent `200` (already terminal) or `409 device-retired` if re-mutated |

## 9. Acceptance criteria

- A registered device can enroll exactly once and obtain a working device credential.
- The enrollment token is never accepted a second time and never stored in plaintext.
- The full cold-start sequence (§7) completes with no manual DB intervention (doc 01 Success Signals).
- A retired device's credential can no longer authenticate a heartbeat.

## 10. BDD coverage

`tests/cucumber/device-registration-enrollment.feature`.
