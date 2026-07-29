# SPEC-03 — Event Publication and Outbox

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Enqueue

- WHEN a `CommandMatch` is fired, THE SYSTEM SHALL generate a UUID v4 `eventId` and
  INSERT a row into `event_outbox` with `attempt_count=0` and `next_retry_at=now`.
- THE SYSTEM SHALL NOT generate a new `eventId` on retry; the original `eventId` is
  the idempotency key and MUST remain stable across all delivery attempts.
- WHEN `event_outbox` already has 50 rows, THE SYSTEM SHALL DELETE the row with the
  oldest `enqueued_at` before inserting the new one, and SHALL log ALERT with the
  dropped `eventId` and `comandoId`.

## 2. Drain loop

- THE SYSTEM SHALL run a continuous asyncio drain task that queries
  `event_outbox WHERE next_retry_at <= now ORDER BY enqueued_at ASC LIMIT 5`
  and attempts delivery for each row.
- THE SYSTEM SHALL call `AuthClient.get_token()` before each HTTP request.
- THE SYSTEM SHALL include `X-Idempotency-Key: {eventId}` on every delivery attempt.
- THE SYSTEM SHALL include `X-Correlation-Id: {session_correlation_id}` on every request.

## 3. Delivery outcomes

| EVNT response | Action |
|---|---|
| `202 Accepted` | DELETE row from `event_outbox` |
| `409 Conflict` | DELETE row (already delivered — idempotent) |
| `401 Unauthorized` | Call `AuthClient.refresh_token()`; retry once immediately; if still 401 → log WARN, back-off |
| `422` + `unknown-command` | DELETE row; call `CommandCache.invalidate()` |
| `4xx` (other) | Log ERROR; DELETE row (non-retryable client error) |
| `5xx` / timeout / connection error | Increment `attempt_count`; update `next_retry_at` per back-off schedule; retain row |

## 4. Back-off schedule

`next_retry_at = now + delay` where delay (in seconds) by `attempt_count`:

| attempt_count | delay |
|---|---|
| 0 | immediate (now) |
| 1 | 1s |
| 2 | 5s |
| 3 | 30s |
| 4 | 120s |
| ≥ 5 | 600s |

## 5. Startup replay

- WHEN the PEA starts and `event_outbox` contains rows from a previous session,
  THE SYSTEM SHALL reset all `next_retry_at` to `now` and begin draining immediately
  (do not wait for the next polling cycle).

## 6. Shutdown drain

- WHEN the PEA receives SIGTERM or SIGINT, THE SYSTEM SHALL attempt to drain the
  `event_outbox` for up to 5 seconds before exiting.
- WHEN the 5-second drain window elapses, THE SYSTEM SHALL exit regardless of
  remaining outbox entries (they will be replayed on next startup).
