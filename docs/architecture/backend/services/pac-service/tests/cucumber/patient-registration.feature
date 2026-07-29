# SPEC ref: SPEC-01-api-surface.md §3, SPEC-06-security.md
# EARS invariants: api-contract.md §1.1, §3
Feature: Patient Registration

  Background:
    Given the PAC service is running
    And a valid JWT bearer token is present with tenant claim "empresa-abc"
    And the request header "X-Empresa-Id" is "empresa-abc"

  # EARS: WHEN a valid registration is received, THE SYSTEM SHALL persist the Paciente scoped to X-Empresa-Id and return 201.
  Scenario: Register a patient with mandatory fields only
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral"
      }
      """
    Then the response status is 201
    And the response header "Location" matches "/api/pac/v1/pacientes/[0-9a-f-]+"
    And the response header "ETag" is "\"1\""
    And the response body contains field "id"
    And the response body field "nome" is "Márcio Amaral"

  # EARS: WHERE dispositivoEdgeId is present, THE SYSTEM SHALL resolve it against EDGE.
  Scenario: Register a patient with an edge device and caregivers
    Given EDGE resolves device "dev-001" as belonging to "empresa-abc"
    And ACS resolves caregiver "cuid-001" as belonging to "empresa-abc"
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "dispositivoEdgeId": "dev-001",
        "cuidadorResponsavelIds": ["cuid-001"]
      }
      """
    Then the response status is 201
    And the response body field "dispositivoEdgeId" is "dev-001"

  # EARS: IF nome is blank, THEN THE SYSTEM SHALL reject with 400.
  Scenario: Reject patient registration with blank name
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "   "
      }
      """
    Then the response status is 400
    And the response content type is "application/problem+json"
    And the response body field "type" is "/errors/validation"

  # EARS: WHERE dispositivoEdgeId is present, IF device is bound to another empresa, THEN THE SYSTEM SHALL reject with 422.
  Scenario: Reject patient registration when edge device belongs to another tenant
    Given EDGE resolves device "dev-foreign" as belonging to "empresa-xyz"
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "dispositivoEdgeId": "dev-foreign"
      }
      """
    Then the response status is 422
    And the response body field "type" is "/errors/validation"
    And the response body contains field "errors" with field code "not-supported" for "dispositivoEdgeId"

  # EARS: WHERE cuidadorResponsavelIds is non-empty, IF any caregiver is unknown, THEN THE SYSTEM SHALL reject with 422.
  Scenario: Reject patient registration with unknown caregiver
    Given ACS returns 404 for caregiver "cuid-unknown"
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "cuidadorResponsavelIds": ["cuid-unknown"]
      }
      """
    Then the response status is 422
    And the response body field "type" is "/errors/validation"

  # EARS: IF X-Empresa-Id is absent, THEN THE SYSTEM SHALL reject with 400 (type=/errors/missing-tenant).
  Scenario: Reject request without X-Empresa-Id header
    Given the request header "X-Empresa-Id" is absent
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      { "nome": "Márcio Amaral" }
      """
    Then the response status is 400
    And the response body field "type" is "/errors/missing-tenant"

  # EARS: IF the JWT tenant claim does not match X-Empresa-Id, THEN THE SYSTEM SHALL reject with 403.
  Scenario: Reject request when JWT tenant does not match X-Empresa-Id
    Given a valid JWT bearer token is present with tenant claim "empresa-other"
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      { "nome": "Márcio Amaral" }
      """
    Then the response status is 403
    And the response body field "type" is "/errors/tenant-mismatch"

  # EARS: THE SYSTEM SHALL never return a Paciente belonging to another empresa.
  Scenario: List patients returns only patients of the requesting tenant
    Given patient "pac-001" belongs to "empresa-abc"
    And patient "pac-002" belongs to "empresa-xyz"
    When a GET request is made to "/api/pac/v1/pacientes"
    Then the response status is 200
    And the response list contains "pac-001"
    And the response list does not contain "pac-002"

  # EARS: IF If-Match is missing or stale, THEN THE SYSTEM SHALL reject with 409 (conflict-precondition).
  Scenario: Reject patient update with stale ETag
    Given patient "pac-001" exists in "empresa-abc" with row_version 2
    When a PUT request is made to "/api/pac/v1/pacientes/pac-001" with "If-Match: \"1\"" and body:
      """
      { "nome": "Márcio Amaral Updated" }
      """
    Then the response status is 409
    And the response body field "type" is "/errors/conflict-precondition"

  Scenario Outline: Patient update validation for missing mandatory fields
    Given patient "<paciente_id>" exists in "empresa-abc"
    When a PUT request is made to "/api/pac/v1/pacientes/<paciente_id>" with matching ETag and body:
      """
      { "nome": "<nome>" }
      """
    Then the response status is <status>

    Examples:
      | paciente_id | nome   | status |
      | pac-valid   | Márcio | 200    |
      | pac-valid   |        | 400    |
