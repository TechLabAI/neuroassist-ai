# Feature: RecognizedCommandReport — stateless validation (LGPD privacy invariant)
# Covers: SPEC-05, api-contract §5.2, event-contracts §3.1/§4, doc 02
# Key invariants:
#   - Validate-only: GEST never persists the report (video/image/landmark/confidence never stored)
#   - profileVersion must be a published version for the tenant
#   - Reported primitives must be members of the referenced profile version
#   - Idempotency: duplicate reportId within 24h returns original verdict without side effects
#   - LGPD surface: error bodies and logs must never echo biometric-adjacent data

Feature: RecognizedCommandReport — stateless validate-only ingress

  Background:
    Given the tenant "empresa-test" is active
    And profile version 7 is published for tenant "empresa-test"
    And the profile version 7 contains primitive "3f1c-aaa" with engineFeatureRef "mp.blink.double.v1"
    And the device credential for "D-12" is valid for tenant "empresa-test"

  # --- Happy path ---

  Scenario: Valid single-primitive report is accepted
    When the edge posts a RecognizedCommandReport:
      """
      {
        "contractVersion": "1.0.0",
        "reportId": "6b0e-report-1",
        "deviceId": "D-12",
        "tenantRef": "empresa-test",
        "profileVersion": 7,
        "commandRef": "cmd-abc",
        "recognizedAt": "2026-07-27T13:05:22.140Z",
        "primitives": [
          { "tipoGestoId": "3f1c-aaa", "engineFeatureRef": "mp.blink.double.v1", "confidence": 0.91 }
        ],
        "timingWindow": { "startMs": 1200, "endMs": 1900, "durationMs": 700 },
        "composite": false
      }
      """
    Then the response status is 200
    And the response body has valid true
    And the response body has contractVersion "1.0.0"

  Scenario: Valid composite report with two supported primitives is accepted
    Given profile version 7 also contains primitive "9a20-bbb" with engineFeatureRef "mp.brow.raise.v1"
    When the edge posts a composite RecognizedCommandReport with primitives ["3f1c-aaa", "9a20-bbb"] and composite true
    Then the response status is 200
    And the response body has valid true

  # --- Unknown profileVersion ---

  Scenario: Report referencing unpublished profileVersion is rejected — unknown-profile-version
    When the edge posts a report with profileVersion 999
    Then the response status is 422
    And the error type is "/errors/unknown-profile-version"

  # --- Primitive not in profile ---

  Scenario: Report with primitive not in the referenced profile is rejected — primitive-not-in-profile
    When the edge posts a report referencing profileVersion 7 with primitive "ghost-primitive-id"
    Then the response status is 422
    And the error type is "/errors/primitive-not-in-profile"

  # --- Privacy invariant (LGPD) — stateless, nothing persisted ---

  Scenario: Report is validated but never persisted — no video, image, landmark or confidence stored
    When the edge posts a valid RecognizedCommandReport with reportId "privacy-test-001"
    Then the response status is 200
    And the response body has valid true
    And no record with reportId "privacy-test-001" exists in the database
    And no confidence value is stored in the database for this interaction
    And no biometric data is stored in any table

  Scenario: Error body on validation failure contains no biometric-adjacent data
    When the edge posts a report with profileVersion 999
    Then the response status is 422
    And the response body does not contain any confidence value
    And the response body does not contain any image or landmark data

  # --- Idempotency (guidance for EVNT receiver) ---

  Scenario: Duplicate reportId within 24h window returns original verdict without side effects
    Given a valid report with reportId "6b0e-report-1" was previously validated successfully
    When the edge posts the same report again with reportId "6b0e-report-1"
    Then the response status is 200
    And the response body has valid true
    And no additional side effects occur

  # --- Composite invariant ---

  Scenario: Composite report with only one primitive entry is invalid
    # WHEN composite = true THE SYSTEM SHALL require exactly two entries in primitives
    Given profile version 7 also contains primitive "9a20-bbb"
    When the edge posts a composite RecognizedCommandReport with only one primitive and composite true
    Then the response status is 422
    And the error type is "/errors/validation"

  # --- Confidence range ---

  Scenario Outline: Confidence value must be in range [0.0, 1.0]
    When the edge posts a report with primitive confidence <confidence>
    Then the response status is <expected_status>

    Examples:
      | confidence | expected_status |
      | 0.0        | 200             |
      | 1.0        | 200             |
      | 0.91       | 200             |
      | -0.01      | 422             |
      | 1.01       | 422             |

  # --- Detection contract descriptor ---

  Scenario: GET detection-contract returns frozen schema descriptor with current contractVersion
    Given the caller has role "gest.read"
    When the caller gets the detection contract descriptor
    Then the response status is 200
    And the response body has contractVersion "1.0.0"
    And the response body has recognizedCommandReportSchema reference
    And the response body has edgeRecognitionSignalSchema reference

  Scenario: X-Detection-Contract-Version header is present on all GEST responses
    Given the caller has role "gest.read"
    When the caller lists the tipos-gesto catalog
    Then the response header "X-Detection-Contract-Version" equals "1.0.0"
