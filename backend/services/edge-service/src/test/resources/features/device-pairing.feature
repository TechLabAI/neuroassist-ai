# Feature: Exclusive device pairing
# Covers: SPEC-03, api-contract §7, data-model §2.1/§2.4, ADR-005 invariants #1/#3
# Invariants:
#   #1 — one paired device per patient, one patient per paired device (ux_dispositivo_edge_active_pairing)
#   #3 — every pairing transition is audited (dispositivo_edge_transition, kind='pairing')

Feature: Exclusive device pairing

  Background:
    Given the tenant "empresa-test" is active
    And the caller has role "edge.manage"

  Scenario: Pair a registered device to a patient
    Given device "d7b1" is in state "registered"
    And patient "9c2a" has no paired device
    When the caller pairs device "d7b1" to patient "9c2a"
    Then the response status is 201
    And the response body has pairingState "paired"
    And the response body has pacienteId "9c2a"
    And a pairing transition "registered -> paired" is appended with the operator as actor

  Scenario: Cannot pair a second device to a patient who already has one - invariant #1
    Given patient "9c2a" already has device "d7b1" in state "paired"
    And device "e8c2" is in state "registered"
    When the caller pairs device "e8c2" to patient "9c2a"
    Then the response status is 409
    And the error type is "/errors/pairing-conflict"

  Scenario: Cannot pair a device that is already paired
    Given device "d7b1" is in state "paired" to patient "9c2a"
    When the caller pairs device "d7b1" to patient "1f4b"
    Then the response status is 409
    And the error type is "/errors/pairing-conflict"

  Scenario: Cannot pair a retired device
    Given device "d7b1" is in state "retired"
    When the caller pairs device "d7b1" to patient "9c2a"
    Then the response status is 409
    And the error type is "/errors/device-retired"

  Scenario: Unpair a paired device for replacement
    Given device "d7b1" is in state "paired" to patient "9c2a"
    When the caller unpairs device "d7b1" with reason "device replacement"
    Then the response status is 204
    And device "d7b1" moves to state "unpaired"
    And the pacienteId of device "d7b1" is cleared
    And a pairing transition "paired -> unpaired" is appended with reason "device replacement"

  Scenario: Unpairing a device that is not paired is illegal
    Given device "d7b1" is in state "registered"
    When the caller unpairs device "d7b1"
    Then the response status is 409
    And the error type is "/errors/illegal-pairing-transition"

  Scenario: Re-pair a previously unpaired device to a new patient
    Given device "e8c2" is in state "unpaired"
    And patient "1f4b" has no paired device
    When the caller pairs device "e8c2" to patient "1f4b"
    Then the response status is 201
    And the response body has pairingState "paired"
    And a pairing transition "unpaired -> paired" is appended

  # --- Referential honesty for PAC (invariant #5) ---

  Scenario: PAC resolves the paired device for a patient
    Given device "d7b1" is in state "paired" to patient "9c2a"
    When PAC requests the device for patient "9c2a"
    Then the response status is 200
    And the response body has deviceId "d7b1"
    And the response body has pairingState "paired"

  Scenario: PAC gets 404 when the patient has no paired device
    Given patient "9c2a" has no paired device
    When PAC requests the device for patient "9c2a"
    Then the response status is 404
    And the error type is "/errors/patient-not-paired"
