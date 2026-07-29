Feature: Service registration and lease lifecycle

# Refs: SPEC-02 (registry core), SPEC-05 (idempotency), contracts/api-contract.md §3-4

Background:
  Given the neuroassist-ai-service-registry (REG) is running as a single Eureka node
  And no instance of "PAC-SERVICE" is currently registered

Scenario: A starting instance registers and becomes discoverable
  When an instance of "PAC-SERVICE" registers under its logical id
  Then the registry advertises that instance as UP
  And a discovery query for "PAC-SERVICE" returns that instance

Scenario: A healthy instance keeps its lease by renewing
  Given an instance of "PAC-SERVICE" is registered and UP
  When the instance renews its lease within the lease expiration window
  Then the registry keeps advertising the instance as UP

Scenario: A repeated registration does not create a duplicate lease
  Given an instance of "PAC-SERVICE" is registered and UP
  When the same instance registers again with the same instanceId
  Then the registry holds exactly one lease for that instanceId
  And the instance remains advertised as UP

Scenario: A graceful shutdown de-registers immediately
  Given an instance of "PAC-SERVICE" is registered and UP
  When the instance de-registers on graceful shutdown
  Then a discovery query for "PAC-SERVICE" no longer returns that instance

Scenario: A non-UP instance is not advertised for discovery
  Given an instance of "PAC-SERVICE" is registered
  When the instance reports status OUT_OF_SERVICE
  Then the registry does not return that instance for discovery
