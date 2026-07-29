Feature: Gesture classification and command matching
  As the PEA runtime
  I want to classify gestures and match them to patient commands
  So that confirmed commands are published as GestoDetectado events

  Background:
    Given the ONNX model is loaded for device "device-001"
    And the CommandVocabulary contains Comando "PEDIR_AGUA" with:
      | field                | value           |
      | primitivoPrincipal   | BLINK_LEFT      |
      | sensibilidade        | media           |
      | tempoMinGestoMs      | 800             |
      | tempoMaxGestoMs      | 2000            |
      | primitivoSecundario  | (none)          |

  Scenario: ONNX classifies gesture primitive with confidence
    Given a LandmarkSet representing a left-blink gesture
    When GestureClassifier runs inference
    Then a ClassificationResult is produced with primitive_code="BLINK_LEFT"
    And confidence is >= 0.75
    And inference completes in < 10ms

  Scenario: FSM transitions to TRACKING_PRIMARY on sustained detection above threshold
    Given the GestureWindow for "PEDIR_AGUA" is in IDLE state
    When BLINK_LEFT is detected with confidence=0.80 for 850ms continuously
    Then the GestureWindow state is MATCHED
    And a CommandMatch for "PEDIR_AGUA" is fired

  Scenario: FSM resets to IDLE when confidence drops below threshold
    Given the GestureWindow for "PEDIR_AGUA" is in TRACKING_PRIMARY state
    When confidence drops to 0.50 (below 0.75 threshold for "media")
    Then the GestureWindow state resets to IDLE
    And no CommandMatch is fired

  Scenario: Simple command does not match before tempoMinGestoMs
    Given the GestureWindow for "PEDIR_AGUA" is in TRACKING_PRIMARY state
    When BLINK_LEFT is detected with confidence=0.80 for only 500ms
    Then no CommandMatch is fired
    And the GestureWindow remains in TRACKING_PRIMARY state

  Scenario: Compound command fires when secondary follows within 500ms
    Given Comando "CONFIRM" has primitivoPrincipal=BLINK_LEFT and primitivoSecundario=BLINK_RIGHT
    And the GestureWindow for "CONFIRM" is in TRACKING_SECONDARY state
    When BLINK_RIGHT is detected with confidence=0.80 within 500ms
    Then a CommandMatch for "CONFIRM" is fired

  Scenario: Compound command times out if secondary not detected within 500ms
    Given the GestureWindow for "CONFIRM" is in TRACKING_SECONDARY state
    When 500ms elapses with no secondary primitive detected
    Then the GestureWindow resets to IDLE
    And no CommandMatch is fired

  Scenario: Cooldown prevents duplicate match for same command
    Given a CommandMatch for "PEDIR_AGUA" was just fired (tempoMaxGestoMs=2000)
    When BLINK_LEFT is detected again immediately after
    Then no new CommandMatch is fired for "PEDIR_AGUA" during the next 2200ms
    And after 2200ms a new CommandMatch CAN fire

  @performance
  Scenario: End-to-end latency stays under 200ms on host machine
    When a gesture starts being detected
    Then a CommandMatch is fired within tempoMinGestoMs + 50ms
    And the GestoDetectado event reaches EVNT within 100ms of CommandMatch
    And total gesture-to-EVNT latency is <= 200ms
