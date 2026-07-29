# SPEC-04 — Authentication and Token Management

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Client credentials grant

- THE SYSTEM SHALL obtain access tokens from ACS using the OAuth2
  `client_credentials` grant at `{backend.url}/acs/oauth/token`.
- THE SYSTEM SHALL load `device.client_id` and `device.client_secret` from
  `config.toml`; the secret MUST NOT appear in any log at any level.
- THE SYSTEM SHALL request scope `edge_agent`.
- WHEN ACS returns `200 OK` with a valid JWT, THE SYSTEM SHALL cache the token
  in memory with `expires_at = now + expires_in - 30s` (30-second safety margin).

## 2. Token refresh

- WHEN `AuthClient.get_token()` is called and the cached token is not yet expired,
  THE SYSTEM SHALL return the cached token without making a network call.
- WHEN the cached token is expired or absent, THE SYSTEM SHALL call ACS to obtain
  a new token before returning.
- THE SYSTEM SHALL NOT implement the OAuth2 refresh_token flow
  (`client_credentials` grants do not issue refresh tokens).

## 3. Startup

- WHEN the PEA starts, THE SYSTEM SHALL obtain an initial token from ACS as part
  of the startup health-check sequence (step 4 of the startup sequence, after
  BackendHealthCheck confirms ACS is reachable).
- IF ACS returns `401` or `403` at startup, THE SYSTEM SHALL log CRITICAL with
  message `device credentials rejected` and exit with code 1.
- IF ACS is unreachable (connection error / timeout) at startup, THE SYSTEM SHALL
  retry up to 10 times with 5-second intervals before exiting with code 1.

## 4. Runtime token errors

- WHEN ACS returns `401` during normal operation (token rejected mid-session),
  THE SYSTEM SHALL clear the token cache and retry token acquisition once.
- WHEN the retry also returns `401`, THE SYSTEM SHALL log CRITICAL and enter
  `DEGRADED_AUTH` mode: stop publishing events (retain in outbox) but continue
  gesture classification.
- WHEN in `DEGRADED_AUTH` mode, THE SYSTEM SHALL attempt token re-acquisition
  every 60 seconds until successful.
- WHEN a new token is obtained after `DEGRADED_AUTH`, THE SYSTEM SHALL resume
  normal outbox draining.

## 5. Security constraints

- THE SYSTEM SHALL store `device.client_secret` only in `config.toml`; NEVER in
  SQLite, log files, or any event payload.
- THE SYSTEM SHALL use OS keychain integration (via `keyring` library) when
  `secrets.use_keychain = true` in `config.toml`; `client_secret` from
  `config.toml` is then treated as a fallback only.
- THE SYSTEM SHALL NOT log JWT payloads, claims, or the `sub` field at any level.
  Log only `token_obtained: true/false` and `expires_in` (numeric).
- THE SYSTEM SHALL include the Bearer token in `Authorization: Bearer {token}`
  header on every outbound HTTP request (to ACS, GEST, PAC, EVNT, EDGE services).
