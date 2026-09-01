# Ref: SPEC-10 (integrations), hub-integration §3, api-contract §2.4
Feature: Gesture to node binding resolution

Background:
  Given a patient session with scope conv.arvore.navegar for patient p-1024
  And the active session locale is en

Scenario: Resolve a gesture bound to a tree node
  Given the engineFeatureRef gest:blink-double is bound to node n-42
  When the patient resolves the binding for gest:blink-double
  Then the response status is 200
  And the response field bound is true
  And the response field noId is n-42

Scenario: An unbound engineFeatureRef resolves without error
  Given the engineFeatureRef gest:unknown-wink is bound to no node
  When the patient resolves the binding for gest:unknown-wink
  Then the response status is 200
  And the response field bound is false
  And the response field noId is null

Scenario: The engineFeatureRef is treated as an opaque identifier
  When the patient resolves the binding for gest:blink-double
  Then the system does not parse the internal structure of the engineFeatureRef

Scenario: Binding resolution degrades gracefully when PAC/GEST is unavailable
  Given the PAC/GEST vocabulary service is unreachable
  When the patient resolves the binding for gest:blink-double
  Then the response status is 200
  And the response field bound is false
