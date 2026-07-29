# SPEC ref: SPEC-03-emergency-eligibility-gate.md, SPEC-10-integrations.md §2.1
# EARS invariants: api-contract.md §4.6, §4.7; event-contracts.md §2.1
# ADR refs: ADR-005 (emergency-eligibility gate — fail-closed / advisory asymmetry)
Feature: Emergency Eligibility Gate (ADR-005)

  Background:
    Given the PAC service is running
    And a valid JWT bearer token is present with tenant claim "empresa-abc"
    And the request header "X-Empresa-Id" is "empresa-abc"
    And patient "pac-001" exists in "empresa-abc"

  # -----------------------------------------------------------------------
  # PUBLISH PATH — fail-closed for emergencies
  # -----------------------------------------------------------------------

  # EARS: WHEN publishing with urgencia=emergencia, THE SYSTEM SHALL call GEST isSupported for every primitive.
  Scenario: Publish emergency command when all primitives are supported — gate passes
    Given command "cmd-emg" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And primitive "gst-001" is referenced as principal
    And GEST reports primitive "gst-001" as "supported"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "published"

  # EARS: IF any referenced primitive is not supported, THEN THE SYSTEM SHALL reject with 422 and leave estado=validated.
  Scenario: Reject emergency publish when principal primitive is not supported by GEST
    Given command "cmd-emg" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And primitive "gst-pending" is referenced as principal
    And GEST reports primitive "gst-pending" as "pending-training"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish" with matching ETag
    Then the response status is 422
    And the response body field "type" is "/errors/emergency-not-detectable"
    And a GET request to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg" returns estado "validated"

  # Gate applies to ALL primitives (both principal and secondary for composite commands)
  Scenario: Reject emergency composite publish when secondary primitive is not supported
    Given command "cmd-composite" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And primitive "gst-001" is referenced as principal (supported)
    And primitive "gst-pending" is referenced as secondary
    And GEST reports primitive "gst-001" as "supported"
    And GEST reports primitive "gst-pending" as "pending-training"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-composite:publish" with matching ETag
    Then the response status is 422
    And the response body field "type" is "/errors/emergency-not-detectable"

  # EARS: IF GEST eligibility call times out or errors WHILE urgencia=emergencia, THE SYSTEM SHALL fail closed with 503.
  Scenario: Fail closed with 503 when GEST is unreachable during emergency publish
    Given command "cmd-emg" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And GEST is unreachable (connection timeout)
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish" with matching ETag
    Then the response status is 503
    And the response body field "type" is "/errors/eligibility-gate-unavailable"
    And the response header "Retry-After" is present
    And a GET request to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg" returns estado "validated"

  # EARS: IF GEST circuit breaker is OPEN WHILE urgencia=emergencia, THE SYSTEM SHALL fail closed.
  Scenario: Fail closed when GEST circuit breaker is open for an emergency command
    Given command "cmd-emg" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And GEST circuit breaker is open
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish" with matching ETag
    Then the response status is 503
    And the response body field "type" is "/errors/eligibility-gate-unavailable"

  # -----------------------------------------------------------------------
  # PUBLISH PATH — advisory-only for routine commands
  # -----------------------------------------------------------------------

  # EARS: WHERE urgencia=rotina, THE SYSTEM SHALL treat the gate as advisory (non-supported -> warning, no block).
  Scenario: Publish routine command with non-supported primitive — warning returned, not blocked
    Given command "cmd-rot" for patient "pac-001" exists with estado "validated", urgencia "rotina"
    And primitive "gst-pending" is referenced as principal
    And GEST reports primitive "gst-pending" as "pending-training"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-rot:publish" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "published"
    And the response body contains an array field "warnings" with at least 1 item

  # EARS: WHERE urgencia=rotina, GEST unavailability SHALL NOT block publish.
  Scenario: Publish routine command when GEST is unreachable — advisory pass
    Given command "cmd-rot" for patient "pac-001" exists with estado "validated", urgencia "rotina"
    And GEST is unreachable (connection timeout)
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-rot:publish" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "published"
    And the response body contains an array field "warnings" with at least 1 item

  # -----------------------------------------------------------------------
  # RECALIBRATE PATH — re-run gate when urgency raised to emergencia
  # -----------------------------------------------------------------------

  # EARS: IF a recalibration raises urgencia to emergencia, THE SYSTEM SHALL re-run the emergency gate.
  Scenario: Recalibrate raising urgency to emergencia re-runs the emergency gate (gate passes)
    Given command "cmd-pub" for patient "pac-001" exists with estado "published", urgencia "rotina"
    And primitive "gst-001" is referenced as principal
    And GEST reports primitive "gst-001" as "supported"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate" with matching ETag and body:
      """
      { "sensibilidade": "alta", "tempoMinGestoMs": 250, "tempoMaxGestoMs": 1400, "urgencia": "emergencia" }
      """
    Then the response status is 200
    And the response body field "urgencia" is "emergencia"

  Scenario: Recalibrate raising urgency to emergencia — gate fails, recalibration rejected
    Given command "cmd-pub" for patient "pac-001" exists with estado "published", urgencia "rotina"
    And primitive "gst-pending" is referenced as principal
    And GEST reports primitive "gst-pending" as "pending-training"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate" with matching ETag and body:
      """
      { "sensibilidade": "alta", "tempoMinGestoMs": 250, "tempoMaxGestoMs": 1400, "urgencia": "emergencia" }
      """
    Then the response status is 422
    And the response body field "type" is "/errors/emergency-not-detectable"

  # -----------------------------------------------------------------------
  # GATE SLA: connect/read timeout <= 2s
  # -----------------------------------------------------------------------

  Scenario: GEST eligibility call respects 2-second timeout for emergency command
    Given command "cmd-emg" for patient "pac-001" exists with estado "validated", urgencia "emergencia"
    And GEST responds after 2500ms
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish" with matching ETag
    Then the response status is 503
    And the response body field "type" is "/errors/eligibility-gate-unavailable"
