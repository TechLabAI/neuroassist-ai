Feature: Event publication and outbox
  As the PEA runtime
  I want to reliably publish GestoDetectado events to EVNT
  So that commands are delivered even when EVNT is temporarily unavailable

  Background:
    Given the EventPublisher is initialised with a local SQLite outbox
    And EVNT is running at http://localhost:8080/evnt

  Scenario: GestoDetectado event is inserted to outbox on CommandMatch
    When a CommandMatch for "PEDIR_AGUA" is fired for patient "pac-001"
    Then one row is inserted in event_outbox with attempt_count=0
    And the row contains a UUID v4 eventId
    And the row does not contain any biometric field

  Scenario: Drain delivers event to EVNT with idempotency key
    Given event_outbox has one pending row with eventId="evt-abc"
    When the drain loop runs
    Then a POST is made to /evnt/events/gesture-detected
    And the request includes header X-Idempotency-Key: evt-abc
    And EVNT responds 202 Accepted
    Then the row is deleted from event_outbox

  Scenario: 409 from EVNT deletes outbox row (idempotent duplicate)
    Given event_outbox has one pending row with eventId="evt-abc"
    And EVNT returns 409 Conflict for that eventId
    When the drain loop processes the row
    Then the row is deleted from event_outbox
    And no error log is emitted

  Scenario: Back-off schedule delays retry after EVNT 5xx
    Given event_outbox has one pending row with attempt_count=0
    And EVNT returns 503 Service Unavailable
    When the drain loop processes the row
    Then attempt_count is incremented to 1
    And next_retry_at is set to now + 1 second
    And the row is NOT deleted

  Scenario: Outbox overflow drops oldest row and logs ALERT
    Given event_outbox contains 50 rows
    When a new CommandMatch fires
    Then the row with the oldest enqueued_at is deleted
    And the new row is inserted
    And an ALERT log event is emitted with the dropped eventId

  Scenario: Outbox is replayed on PEA restart
    Given event_outbox contains 3 rows from a previous session
    When the PEA starts
    Then all 3 rows have next_retry_at reset to now
    And the drain loop begins immediately without waiting for the next schedule

  Scenario: Shutdown drains outbox for up to 5 seconds
    Given event_outbox contains 2 pending rows
    When SIGTERM is received
    Then the drain loop processes pending rows for up to 5 seconds
    And the process exits after draining (or after 5 second timeout)

  @lgpd
  Scenario: Event payload contains no biometric data
    When a GestoDetectado event is published to EVNT
    Then the payload contains only: deviceId, patientId, comandoId, detectedAt, confidence, eventId
    And the payload does not contain any landmark coordinates or facial data
