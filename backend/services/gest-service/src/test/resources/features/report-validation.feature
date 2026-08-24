# Feature: RecognizedCommandReport — stateless validation (LGPD privacy invariant)
# Covers: SPEC-05, api-contract §5.2, event-contracts §3.1/§4, doc 02
# Key invariants:
#   - Validate-only: GEST never persists the report (video/image/landmark/confidence never stored)
#   - LGPD surface: error bodies must never echo biometric-adjacent data
#
# CORREÇÃO DE CONTRATO — a feature descrevia um contrato de relatório que nunca foi construído.
# O que existe é POST /gest-service/detection-contract com corpo {"report": {...}} validado contra
# o descritor congelado v1.0.0: campos obrigatórios {gestureId, confidence, windowMs} e campos
# PROIBIDOS {video, image, landmark, base64, pixels, frame}. O serviço responde sempre 200 com
# {"valid": bool, "errors": [...]} — não há 422, não há profileVersion, não há primitives, não há
# janela de idempotência por reportId. Os cenários que dependiam disso estão marcados @wip no fim,
# com a justificativa. Os que restam cobrem o que é o coração desta feature e ESTÁ implementado:
# a fronteira LGPD e o fato de nada ser persistido.

Feature: RecognizedCommandReport — stateless validate-only ingress

  Background:
    Given the tenant "empresa-test" is active
    And the caller is authenticated with role "gest.read"

  # --- Happy path ---

  Scenario: Valid report is accepted against the frozen contract v1.0.0
    When the edge posts a report:
      """
      { "gestureId": "3f1c-aaa", "confidence": 0.91, "windowMs": 700 }
      """
    Then the response status is 200
    And the response body has valid true
    And the response body reports no errors

  # --- Required fields ---

  Scenario: Report missing a required field is rejected by the contract
    When the edge posts a report:
      """
      { "gestureId": "3f1c-aaa", "confidence": 0.91 }
      """
    Then the response status is 200
    And the response body has valid false
    And the errors list names the field "windowMs"

  Scenario: Report with a required field explicitly null is rejected
    When the edge posts a report:
      """
      { "gestureId": "3f1c-aaa", "confidence": 0.91, "windowMs": null }
      """
    Then the response status is 200
    And the response body has valid false
    And the errors list names the field "windowMs"

  # --- Privacy invariant (LGPD) ---

  Scenario Outline: Raw biometric fields are refused at the boundary
    When the edge posts a report carrying the forbidden field "<field>"
    Then the response status is 200
    And the response body has valid false
    And the errors list names the field "<field>"

    Examples:
      | field    |
      | video    |
      | image    |
      | landmark |
      | base64   |
      | pixels   |
      | frame    |

  Scenario: Error body on refusal echoes the field name but never its content
    When the edge posts a report carrying the forbidden field "video" with content "SENTINELA-BIOMETRICA-XYZ"
    Then the response status is 200
    And the response body has valid false
    And the response body does not contain "SENTINELA-BIOMETRICA-XYZ"

  Scenario: Report is validated but never persisted — nothing is written to any GEST table
    Given the GEST tables are empty
    When the edge posts a report:
      """
      { "gestureId": "privacy-test-001", "confidence": 0.97, "windowMs": 640 }
      """
    Then the response status is 200
    And the response body has valid true
    And no row was written to any GEST table

  Scenario: Repeated validation of the same report yields the same verdict and no side effects
    Given the GEST tables are empty
    When the edge posts a report:
      """
      { "gestureId": "6b0e-report-1", "confidence": 0.91, "windowMs": 700 }
      """
    And the edge posts the same report again
    Then the response status is 200
    And both responses carry the same verdict
    And no row was written to any GEST table

  # --- Detection contract descriptor ---

  Scenario: GET detection-contract returns the frozen schema descriptor
    When the caller gets the detection contract descriptor
    Then the response status is 200
    And the response body has version "v1.0.0"
    And the descriptor requires the fields "gestureId, confidence, windowMs"
    And the descriptor forbids the fields "video, image, landmark, base64, pixels, frame"

  # --- Authorization (E2E-026) ---

  Scenario: Reading the descriptor does not grant the right to post a report
    Given the caller holds only "gest.detection-contract.read"
    When the edge posts a report:
      """
      { "gestureId": "3f1c-aaa", "confidence": 0.91, "windowMs": 700 }
      """
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"

  # ---------------------------------------------------------------------------
  # @wip — capacidade AUSENTE, não cenário difícil.
  #
  # O DetectionContractService valida três campos e a lista de campos proibidos, e é tudo.
  # Não conhece profileVersion, nem a lista de primitivos de um perfil, nem composite, nem faixa
  # de confidence, nem janela de deduplicação por reportId — e não existe header
  # X-Detection-Contract-Version em resposta nenhuma. Os cenários abaixo descrevem o api-contract
  # §5.2 que nunca foi construído; ficam registrados e fora da suíte enquanto não existir.
  # ---------------------------------------------------------------------------

  @wip
  Scenario: Report referencing unpublished profileVersion is rejected — unknown-profile-version
    When the edge posts a report with profileVersion 999
    Then the response status is 422
    And the error type is "/errors/unknown-profile-version"

  @wip
  Scenario: Report with primitive not in the referenced profile is rejected
    When the edge posts a report referencing profileVersion 7 with primitive "ghost-primitive-id"
    Then the response status is 422
    And the error type is "/errors/primitive-not-in-profile"

  @wip
  Scenario: Composite report with only one primitive entry is invalid
    When the edge posts a composite report with only one primitive
    Then the response status is 422
    And the error type is "/errors/validation"

  @wip
  Scenario Outline: Confidence value must be in range [0.0, 1.0]
    When the edge posts a report with primitive confidence <confidence>
    Then the response status is <expected_status>

    Examples:
      | confidence | expected_status |
      | -0.01      | 422             |
      | 1.01       | 422             |

  @wip
  Scenario: X-Detection-Contract-Version header is present on all GEST responses
    When the caller lists the tipos-gesto catalog
    Then the response header "X-Detection-Contract-Version" equals "v1.0.0"
