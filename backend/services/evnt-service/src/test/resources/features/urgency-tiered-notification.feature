# Refs: SPEC-02, ADR-005 · api-contract §3.1 · event-contracts §2
Feature: Urgency-tiered notification orchestration

Background:
  Given a tenant "empresa_demo" with patient "pac-1"
  And ACS resolves "cg-1" as the responsible caregiver for patient "pac-1"
  And GEST validates any well-formed report

Scenario Outline: Notification tier is a deterministic function of urgency
  Given a command "<command>" of urgency "<urgencia>"
  And a RecognizedCommandReport with reportId "<reportId>" for command "<command>"
  When the edge posts the report to "/api/v1/reports"
  Then the response status is 201
  And a notification dispatch is created with tier "<tier>" targeting "cg-1"

  Examples:
    | command        | urgencia    | reportId | tier              |
    | cmd-emergency  | emergencia  | u-1      | full_screen_alarm |
    | cmd-routine    | rotina      | u-2      | discreet          |

Scenario: The Evento is persisted before any notification is attempted
  Given a command "cmd-emergency" of urgency "emergencia"
  And DASH is unavailable
  And a RecognizedCommandReport with reportId "u-3" for command "cmd-emergency"
  When the edge posts the report to "/api/v1/reports"
  Then the response status is 201
  And exactly one Evento exists with reportId "u-3"
  And a notification dispatch exists with status "failed" or "queued"

Scenario: A failed emergency dispatch is retried, never dropped
  Given a command "cmd-emergency" of urgency "emergencia"
  And DASH will fail the first delivery attempt and succeed on the second
  And a RecognizedCommandReport with reportId "u-4" for command "cmd-emergency"
  When the edge posts the report to "/api/v1/reports"
  And the retry sweep runs
  Then a notification dispatch for reportId "u-4" reaches status "delivered"
  And the dispatch attempts count is at least 2

Scenario: The alarm re-shows a queued dispatch after a service restart
  Given a queued notification dispatch exists for an emergency Evento
  When evnt-service restarts
  And the startup retry sweep runs
  Then the queued dispatch is re-attempted
