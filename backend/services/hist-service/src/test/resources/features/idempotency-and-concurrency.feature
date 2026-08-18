# Covers SPEC-05 (idempotency & concurrency), ADR-005
Feature: Ingestion is exactly-once per factId and gap-free per stream

Background:
  Given a provisioned tenant "familia-silva"
  And a producer token with scope "hist:write"

Scenario: A duplicate factId returns the original record without a second write
  Given an audit fact "018f-1001" of type "acesso.concedido" for subject cuidador "cg-9" by caregiver "cg-1"
  And it was already submitted successfully
  When it is submitted to POST /audit-facts again
  Then the response status is 200
  And the returned record id equals the original record id
  And the stream "cuidador:cg-9" still has exactly 1 record

Scenario Outline: Consecutive facts on one stream get consecutive sequence numbers
  Given the stream "paciente:pac-2" already has <existing> records
  And an audit fact "<factId>" of type "evento.confirmado" for subject paciente "pac-2" by caregiver "cg-1"
  When it is submitted to POST /audit-facts
  Then the record has sequenceNo <expected>

  Examples:
    | existing | factId    | expected |
    | 0        | 018f-2001 | 0        |
    | 1        | 018f-2002 | 1        |
    | 5        | 018f-2003 | 5        |

Scenario: Concurrent appends on the same stream never duplicate a sequenceNo
  Given two audit facts "018f-3001" and "018f-3002" for subject paciente "pac-3"
  When they are submitted concurrently to POST /audit-facts
  Then both are recorded
  And their sequenceNo values are 0 and 1 with no gap and no duplicate

Scenario: Concurrent submission of the same factId records only once
  Given an audit fact "018f-4001" of type "dispositivo.pareado" for subject dispositivo "dev-1" by caregiver "cg-1"
  When it is submitted concurrently by two nodes
  Then exactly one record is persisted
  And one response is 201 and the other is 200
