# SPEC-02 / SPEC-06 — Write-through caregiver actions and scope enforcement

Feature: Caregiver actions are proxied to the owner service; DASH stores no domain truth

Background:
  Given a caregiver "Jamilly" for tenant "familia-amaral"
  And patient "Marcio" is in Jamilly's scope
  And command "cmd-water" belongs to "Marcio"

Scenario: Calibration edit is forwarded to PAC and PAC result is returned
  When Jamilly updates calibration of "cmd-water" with sensibilidade "media"
  Then DASH forwards the calibration edit to PAC
  And the response body is PAC's authoritative result
  And DASH persists no calibration value locally

Scenario: PAC rejection is relayed unchanged
  Given PAC rejects the calibration edit with status 409
  When Jamilly updates calibration of "cmd-water"
  Then the response status is 409
  And the ProblemDetail is relayed from PAC

Scenario: Event confirmation is written through to EVNT
  Given event "ev-9" belongs to "Marcio"
  When Jamilly confirms event "ev-9" with feedback "real"
  Then DASH forwards feedback "real" to EVNT
  And the response body is EVNT's updated event state

Scenario: Invalid feedback is rejected before calling EVNT
  When Jamilly confirms event "ev-9" with feedback "maybe"
  Then the response status is 422
  And EVNT is not called

Scenario: A patient outside scope is forbidden without leaking existence
  Given patient "Outro" is NOT in Jamilly's scope
  When Jamilly requests GET /panel/patients/Outro/overview
  Then the response status is 403
  And the response does not reveal whether "Outro" exists

Scenario: A command not belonging to the patient yields 404
  Given command "cmd-other" does not belong to "Marcio"
  When Jamilly updates calibration of "cmd-other" for "Marcio"
  Then the response status is 404
