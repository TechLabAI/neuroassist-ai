# SPEC-03 / SPEC-05 — Real-time notification delivery over SSE

Feature: Deliver EVNT notifications to the caregiver panel and report delivery honestly

Background:
  Given a caregiver "Jamilly" for tenant "familia-amaral"
  And patient "Marcio" is in Jamilly's scope

Scenario: Emergency dispatch reaches an open panel and is reported delivered
  Given Jamilly has an open SSE session on /panel/stream
  When EVNT dispatches notification "d-1" with tier "full_screen_alarm" for caregiver "Jamilly"
  Then DASH pushes an SSE "notification" event carrying dispatchId "d-1"
  And DASH reports delivered("d-1") to EVNT

Scenario: Dispatch to a caregiver with no open session is surfaced, not dropped
  Given Jamilly has no open SSE session
  When EVNT dispatches notification "d-2" for caregiver "Jamilly"
  Then the dispatch response delivery is "no_open_session"
  And DASH reports no delivered signal for "d-2"

Scenario: Redelivered dispatch is idempotent
  Given Jamilly has an open SSE session
  And notification "d-3" was already delivered
  When EVNT dispatches notification "d-3" again
  Then DASH does not send a second delivered("d-3") to EVNT

Scenario: On reconnect the caregiver receives unacknowledged notifications
  Given EVNT holds unacknowledged notifications "d-4" and "d-5" for Jamilly
  When Jamilly connects to /panel/stream
  Then DASH replays "d-4" and "d-5" over SSE
  And DASH emits an SSE "catchup-complete" event

Scenario: Acknowledge is written through to EVNT and is idempotent
  Given notification "d-6" was delivered to Jamilly
  When Jamilly acknowledges "d-6"
  Then the response status is 204
  And DASH reports acknowledged("d-6") to EVNT
  When Jamilly acknowledges "d-6" again
  Then the response status is 204
  And DASH does not send a second acknowledged("d-6")

Scenario Outline: Tier is taken from EVNT, never decided by DASH
  Given Jamilly has an open SSE session
  When EVNT dispatches notification with urgencia "<urgencia>" and tier "<tier>"
  Then the SSE notification carries tier "<tier>"

  Examples:
    | urgencia    | tier              |
    | emergencia  | full_screen_alarm |
    | rotina      | discreet          |
