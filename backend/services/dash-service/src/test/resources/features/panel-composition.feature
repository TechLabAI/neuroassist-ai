# SPEC-02 — Panel composition (BFF) with per-section degradation

Feature: Panel composition composes owner services into caregiver-scoped view models

Background:
  Given a caregiver "Jamilly" authenticated with a valid ACS JWT for tenant "familia-amaral"
  And Jamilly's ACS scope contains patient "Marcio"

Scenario: Home summary composes patients, device liveness and event state
  Given PAC, EVNT and EDGE are all available
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary contains patient "Marcio"
  And "Marcio" has a device status and a latest event
  And degradedSections is empty

Scenario: EDGE unavailable soft-degrades the device indicator
  Given EDGE is unreachable
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the device status for "Marcio" is "unknown"
  And the device indicator is marked degraded

# E2E-018 — "nao ha dispositivo" e "nao consegui perguntar" sao respostas diferentes.
# Antes as duas viravam degraded=true/unknown, e a cuidadora nao tinha como saber se estava
# vendo ausencia de equipamento ou ausencia de informacao.
Scenario: Patient without a paired device is not reported as degraded
  Given EDGE reports that the patient has no paired device
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the device status for "Marcio" is "no-device"
  And the device indicator is not marked degraded

Scenario: EVNT unavailable omits the event section but still returns 200
  Given EVNT is unreachable
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary omits the latest event section
  And degradedSections contains "evnt"

Scenario: PAC unavailable makes the patient overview return 503
  Given PAC is unreachable
  When Jamilly requests GET /panel/patients/Marcio/overview
  Then the response status is 503
  And the ProblemDetail type ends with "upstream-unavailable"

Scenario: History filters and paging are forwarded to HIST
  Given HIST is available
  When Jamilly requests GET /panel/patients/Marcio/history with from "2026-08-01" and size "200"
  Then the response status is 200
  And HIST received filter from "2026-08-01"
  And HIST received size "100"
