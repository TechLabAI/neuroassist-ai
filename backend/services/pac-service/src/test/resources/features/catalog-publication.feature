# SPEC ref: SPEC-01-api-surface.md §5, SPEC-09-messaging-architecture.md §3
# EARS invariants: api-contract.md §5; data-model.md §3; event-contracts.md §3
# Purpose: pull-based catalog surface for GEST to snapshot DetectionProfile (ADR-004)
Feature: Catalog Publication (Active Calibrated Catalog)

  Background:
    Given the PAC service is running
    And a valid JWT bearer token is present with scope "pac:catalog:read" and tenant claim "empresa-abc"
    And the request header "X-Empresa-Id" is "empresa-abc"
    And patient "pac-001" exists in "empresa-abc"

  # -----------------------------------------------------------------------
  # CATALOG CONTENT INVARIANTS
  # -----------------------------------------------------------------------

  # EARS: THE SYSTEM SHALL include only published commands in catalogo-ativo (never drafted|validated|retired).
  Scenario: Catalog returns only published commands — excludes other lifecycle states
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    And command "cmd-draft" for patient "pac-001" exists with estado "drafted"
    And command "cmd-val" for patient "pac-001" exists with estado "validated"
    And command "cmd-ret" for patient "pac-001" exists with estado "retired"
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/catalogo-ativo"
    Then the response status is 200
    And the response "comandos" list contains "cmd-pub"
    And the response "comandos" list does not contain "cmd-draft"
    And the response "comandos" list does not contain "cmd-val"
    And the response "comandos" list does not contain "cmd-ret"

  # EARS: WHEN any command is published/recalibrated/retired, THE SYSTEM SHALL bump the patient's catalogoVersao.
  Scenario: Catalog version bumps when a command is published
    Given patient "pac-001" has catalogoVersao 5
    And command "cmd-val" for patient "pac-001" exists with estado "validated" and urgencia "rotina"
    When a POST request is made (scope pac:command:publish) to "/api/pac/v1/pacientes/pac-001/comandos/cmd-val:publish" with matching ETag
    Then a GET to "/api/pac/v1/pacientes/pac-001/catalogo-ativo" returns catalogoVersao 6

  Scenario: Catalog version bumps when a command is recalibrated
    Given patient "pac-001" has catalogoVersao 7
    And command "cmd-pub" for patient "pac-001" exists with estado "published"
    When a POST request is made (scope pac:command:publish) to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate" with matching ETag and body:
      """
      { "sensibilidade": "alta", "tempoMinGestoMs": 250, "tempoMaxGestoMs": 1400, "urgencia": "rotina" }
      """
    Then a GET to "/api/pac/v1/pacientes/pac-001/catalogo-ativo" returns catalogoVersao 8

  Scenario: Catalog version bumps when a command is retired
    Given patient "pac-001" has catalogoVersao 9
    And command "cmd-pub" for patient "pac-001" exists with estado "published"
    When a POST request is made (scope pac:command:publish) to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:retire" with matching ETag
    Then a GET to "/api/pac/v1/pacientes/pac-001/catalogo-ativo" returns catalogoVersao 10

  # -----------------------------------------------------------------------
  # CATALOG PAYLOAD COMPLETENESS (for DetectionProfile snapshot by GEST)
  # -----------------------------------------------------------------------

  Scenario: Catalog response contains all required calibration fields per command
    Given command "cmd-pub" for patient "pac-001" exists with estado "published" and:
      | field                  | value              |
      | rotulo                 | chamar a cuidadora |
      | necessidade            | assistencia        |
      | primitivoPrincipalId   | gst-001            |
      | sensibilidade          | alta               |
      | tempoMinGestoMs        | 250                |
      | tempoMaxGestoMs        | 1400               |
      | urgencia               | emergencia         |
      | calibracaoVersao       | 3                  |
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/catalogo-ativo"
    Then the response status is 200
    And the response body field "pacienteId" is "pac-001"
    And the response body field "empresaId" is "empresa-abc"
    And the first command in "comandos" has field "calibracaoVersao" equal to 3
    And the first command in "comandos" has field "urgencia" equal to "emergencia"

  # -----------------------------------------------------------------------
  # HTTP CONDITIONAL GET (ETag / If-None-Match)
  # -----------------------------------------------------------------------

  # EARS: THE SYSTEM SHALL support conditional GET via If-None-Match on catalogoVersao, returning 304 when unchanged.
  Scenario: Conditional GET returns 304 when catalog is unchanged (ETag matches)
    Given patient "pac-001" has catalogoVersao 42
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/catalogo-ativo" with header "If-None-Match: \"42\""
    Then the response status is 304
    And the response body is empty

  Scenario: Conditional GET returns 200 with body when catalog has changed
    Given patient "pac-001" has catalogoVersao 43
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/catalogo-ativo" with header "If-None-Match: \"42\""
    Then the response status is 200
    And the response body field "catalogoVersao" is 43
    And the response header "ETag" is "\"43\""

  # -----------------------------------------------------------------------
  # TENANCY ISOLATION
  # -----------------------------------------------------------------------

  # EARS: THE SYSTEM SHALL never return a Paciente (or its catalog) belonging to another empresa.
  Scenario: Catalog access for a patient of another tenant returns 404
    Given patient "pac-other" exists in "empresa-xyz"
    When a GET request is made to "/api/pac/v1/pacientes/pac-other/catalogo-ativo"
    Then the response status is 404

  # -----------------------------------------------------------------------
  # SCOPE ENFORCEMENT
  # -----------------------------------------------------------------------

  # EARS: WHILE the caller lacks the scope required, THE SYSTEM SHALL respond 403.
  Scenario: Catalog access denied when JWT scope is pac:command:read instead of pac:catalog:read
    Given a valid JWT bearer token is present with scope "pac:command:read" and tenant claim "empresa-abc"
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/catalogo-ativo"
    Then the response status is 403
    And the response body field "type" is "/errors/insufficient-scope"
