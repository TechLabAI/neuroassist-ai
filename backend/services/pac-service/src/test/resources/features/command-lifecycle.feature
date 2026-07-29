# SPEC ref: SPEC-02-command-lifecycle-orchestration.md, SPEC-01-api-surface.md §4
# EARS invariants: api-contract.md §4.1..§4.8, event-contracts.md §1
Feature: Command Lifecycle (Draft → Validate → Publish → Recalibrate → Retire)

  Background:
    Given the PAC service is running
    And a valid JWT bearer token is present with tenant claim "empresa-abc"
    And the request header "X-Empresa-Id" is "empresa-abc"
    And patient "pac-001" exists in "empresa-abc"

  # -----------------------------------------------------------------------
  # AUTHORING (DRAFT)
  # -----------------------------------------------------------------------

  # EARS: WHEN a command draft is created, THE SYSTEM SHALL set estado=drafted and calibracaoVersao=0.
  Scenario: Author a new command draft with minimal valid fields
    Given GEST knows primitive "gst-001"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos" with body:
      """
      {
        "rotulo": "quero água",
        "necessidade": "hidratacao",
        "primitivoPrincipalId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": 300,
        "tempoMaxGestoMs": 1500,
        "urgencia": "rotina"
      }
      """
    Then the response status is 201
    And the response header "Location" matches "/api/pac/v1/pacientes/pac-001/comandos/[0-9a-f-]+"
    And the response body field "estado" is "drafted"
    And the response body field "calibracaoVersao" is 0

  # EARS: IF rotulo or necessidade is blank, THEN THE SYSTEM SHALL reject with 400.
  Scenario Outline: Reject command draft with blank required text field
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos" with body:
      """
      {
        "rotulo": "<rotulo>",
        "necessidade": "<necessidade>",
        "primitivoPrincipalId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": 300,
        "tempoMaxGestoMs": 1500,
        "urgencia": "rotina"
      }
      """
    Then the response status is 400
    And the response body field "type" is "/errors/validation"

    Examples:
      | rotulo    | necessidade |
      |           | hidratacao  |
      | quero água |            |

  # EARS: IF tempoMinGestoMs <= 0 or tempoMinGestoMs > tempoMaxGestoMs, THEN THE SYSTEM SHALL reject with 400.
  Scenario Outline: Reject command draft with invalid timing
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos" with body:
      """
      {
        "rotulo": "quero água",
        "necessidade": "hidratacao",
        "primitivoPrincipalId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": <min>,
        "tempoMaxGestoMs": <max>,
        "urgencia": "rotina"
      }
      """
    Then the response status is 400
    And the response body field "type" is "/errors/validation"

    Examples:
      | min | max  |
      | 0   | 1500 |
      | -1  | 1500 |
      | 800 | 500  |

  # EARS: IF primitivoSecundarioId == primitivoPrincipalId, THEN THE SYSTEM SHALL reject with 400.
  Scenario: Reject composite command where both primitives are the same
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos" with body:
      """
      {
        "rotulo": "quero água",
        "necessidade": "hidratacao",
        "primitivoPrincipalId": "gst-001",
        "primitivoSecundarioId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": 300,
        "tempoMaxGestoMs": 1500,
        "urgencia": "rotina"
      }
      """
    Then the response status is 400
    And the response body field "type" is "/errors/validation"

  # EARS: IF an active command for same gesture+necessidade already exists, THEN THE SYSTEM SHALL reject with 422.
  Scenario: Reject duplicate active command mapping (same gesture+need per patient)
    Given command "cmd-001" for patient "pac-001" maps "gst-001" to need "hidratacao" with estado "published"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos" with body:
      """
      {
        "rotulo": "quero um copo d'água",
        "necessidade": "hidratacao",
        "primitivoPrincipalId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": 300,
        "tempoMaxGestoMs": 1500,
        "urgencia": "rotina"
      }
      """
    Then the response status is 422
    And the response body field "type" is "/errors/duplicate-active-mapping"

  # -----------------------------------------------------------------------
  # VALIDATE TRANSITION (drafted → validated)
  # -----------------------------------------------------------------------

  # EARS: WHEN validation is requested WHILE estado=drafted, THE SYSTEM SHALL resolve primitives against GEST and set estado=validated.
  Scenario: Validate a drafted command (primitive confirmed by GEST)
    Given command "cmd-draft" for patient "pac-001" exists with estado "drafted"
    And GEST knows primitive "gst-001"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-draft:validate" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "validated"

  # EARS: IF any referenced primitive id is unknown to GEST, THE SYSTEM SHALL reject with 422.
  Scenario: Reject validate when primitive is unknown to GEST
    Given command "cmd-draft" for patient "pac-001" references primitive "gst-unknown" with estado "drafted"
    And GEST returns 404 for primitive "gst-unknown"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-draft:validate" with matching ETag
    Then the response status is 422
    And the response body field "type" is "/errors/validation"

  # -----------------------------------------------------------------------
  # PUBLISH TRANSITION (validated → published)
  # -----------------------------------------------------------------------

  # Routine command: no emergency gate required.
  Scenario: Publish a validated routine command (no emergency gate)
    Given command "cmd-val" for patient "pac-001" exists with estado "validated" and urgencia "rotina"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-val:publish" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "published"

  # EARS: WHEN publish succeeds, THE SYSTEM SHALL record ComandoPublicado synchronously to HIST.
  Scenario: Publish records ComandoPublicado fact to HIST
    Given command "cmd-val" for patient "pac-001" exists with estado "validated" and urgencia "rotina"
    And HIST is available
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-val:publish" with matching ETag
    Then the response status is 200
    And HIST received a "ComandoPublicado" fact for "cmd-val"

  # -----------------------------------------------------------------------
  # EDIT DRAFT (identity fields)
  # -----------------------------------------------------------------------

  # EARS: WHILE estado != drafted, THE SYSTEM SHALL reject identity edits with 409.
  Scenario: Reject identity edit on a published command
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    When a PUT request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub" with matching ETag and body:
      """
      {
        "rotulo": "quero água mesmo",
        "necessidade": "hidratacao",
        "primitivoPrincipalId": "gst-001",
        "sensibilidade": "media",
        "tempoMinGestoMs": 300,
        "tempoMaxGestoMs": 1500,
        "urgencia": "rotina"
      }
      """
    Then the response status is 409
    And the response body field "type" is "/errors/invalid-lifecycle-transition"

  # -----------------------------------------------------------------------
  # RECALIBRATE (in-place tuning of published command)
  # -----------------------------------------------------------------------

  # EARS: WHEN recalibration is applied WHILE estado=published, THE SYSTEM SHALL update tuning and increment calibracaoVersao by exactly 1.
  Scenario: Recalibrate a published command — tuning fields updated, version incremented
    Given command "cmd-pub" for patient "pac-001" exists with estado "published" and calibracaoVersao 2
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate" with matching ETag and body:
      """
      {
        "sensibilidade": "alta",
        "tempoMinGestoMs": 250,
        "tempoMaxGestoMs": 1400,
        "urgencia": "rotina"
      }
      """
    Then the response status is 200
    And the response body field "calibracaoVersao" is 3
    And the response body field "sensibilidade" is "alta"

  # EARS: WHEN recalibration succeeds, THE SYSTEM SHALL record ComandoRecalibrado to HIST.
  Scenario: Recalibrate records ComandoRecalibrado fact to HIST
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    And HIST is available
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate" with matching ETag and body:
      """
      { "sensibilidade": "alta", "tempoMinGestoMs": 250, "tempoMaxGestoMs": 1400, "urgencia": "rotina" }
      """
    Then HIST received a "ComandoRecalibrado" fact for "cmd-pub"

  # -----------------------------------------------------------------------
  # RETIRE TRANSITION (→ retired, terminal, history-preserving)
  # -----------------------------------------------------------------------

  # EARS: WHEN retire is requested from any non-retired state, THE SYSTEM SHALL set estado=retired and SHALL NOT hard-delete.
  Scenario: Retire a published command — estado becomes retired, row persists
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:retire" with matching ETag
    Then the response status is 200
    And the response body field "estado" is "retired"
    And a GET request to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub" returns 200 with estado "retired"

  # EARS: WHILE estado=retired, THE SYSTEM SHALL reject every further transition with 409.
  Scenario: Reject any transition from a retired command
    Given command "cmd-ret" for patient "pac-001" exists with estado "retired"
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-ret:retire" with matching ETag
    Then the response status is 409
    And the response body field "type" is "/errors/invalid-lifecycle-transition"

  # EARS: WHEN retire succeeds, THE SYSTEM SHALL record ComandoAposentado to HIST.
  Scenario: Retire records ComandoAposentado fact to HIST
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    And HIST is available
    When a POST request is made to "/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:retire" with matching ETag
    Then HIST received a "ComandoAposentado" fact for "cmd-pub"

  # EARS: WHERE estado is omitted, THE SYSTEM SHALL return only active commands (drafted|validated|published), never retired.
  Scenario: List commands without filter excludes retired commands
    Given command "cmd-pub" for patient "pac-001" exists with estado "published"
    And command "cmd-ret" for patient "pac-001" exists with estado "retired"
    When a GET request is made to "/api/pac/v1/pacientes/pac-001/comandos"
    Then the response list contains "cmd-pub"
    And the response list does not contain "cmd-ret"
