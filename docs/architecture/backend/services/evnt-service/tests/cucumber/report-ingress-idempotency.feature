# Refs: SPEC-01, SPEC-04, SPEC-05, ADR-004 · api-contract §3.1
Feature: Idempotent recognized-command ingress

Background:
  Given a tenant "empresa_demo" with patient "pac-1" and command "cmd-emergency" of urgency "emergencia"
  And a valid device token scoped to tenant "empresa_demo" and patient "pac-1"
  And GEST will validate any well-formed report for a known profile

Scenario: A new report creates exactly one Evento
  Given a RecognizedCommandReport with reportId "r-100" for command "cmd-emergency"
  When the edge posts the report to "/api/v1/reports"
  Then the response status is 201
  And the response body field "status" is "created"
  And the response body field "feedback" is "nao_avaliado"
  And exactly one Evento exists with reportId "r-100" in tenant "empresa_demo"

Scenario: A duplicate reportId returns the original verdict and creates no new Evento
  Given a RecognizedCommandReport with reportId "r-100" for command "cmd-emergency" was already processed
  When the edge posts a report with the same reportId "r-100"
  Then the response status is 200
  And the response body field "status" is "duplicate"
  And exactly one Evento exists with reportId "r-100" in tenant "empresa_demo"
  And no additional notification dispatch was created

Scenario: GEST rejection yields no Evento
  Given GEST will reject the report with reason "primitive-not-in-profile"
  And a RecognizedCommandReport with reportId "r-200" for command "cmd-emergency"
  When the edge posts the report to "/api/v1/reports"
  Then the response status is 422
  And the ProblemDetail field "type" is "/errors/gest-rejected"
  And no Evento exists with reportId "r-200" in tenant "empresa_demo"

Scenario: A report carrying biometric data is rejected
  Given a RecognizedCommandReport with reportId "r-300" that includes a raw frame payload
  When the edge posts the report to "/api/v1/reports"
  Then the response status is 400
  And the ProblemDetail field "type" is "/errors/forbidden-biometric-payload"

Scenario Outline: The device token tenant must match the patient tenant
  Given a valid device token scoped to tenant "<tokenTenant>" and patient "pac-1"
  And a RecognizedCommandReport with reportId "<reportId>" for command "cmd-emergency"
  When the edge posts the report to "/api/v1/reports"
  Then the response status is <status>

  Examples:
    | tokenTenant  | reportId | status |
    | empresa_demo | r-400    | 201    |
    | empresa_other| r-401    | 403    |
