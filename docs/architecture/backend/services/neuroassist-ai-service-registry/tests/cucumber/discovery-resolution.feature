Feature: Discovery resolution for consumers

# Refs: SPEC-02 (registry core), contracts/hub-integration.md §3, SPEC-07 (health)

Background:
  Given the neuroassist-ai-service-registry (REG) is running as a single Eureka node

Scenario: The gateway resolves an lb:// target to a healthy instance
  Given an instance of "PAC-SERVICE" is registered and UP
  When the API gateway (GWY) resolves "lb://PAC-SERVICE" through discovery
  Then the registry returns the healthy instance for load-balanced routing

Scenario: Only UP instances are returned for a logical id
  Given two instances of "HIST-SERVICE" are registered
  And one instance is UP and the other reports OUT_OF_SERVICE
  When a consumer queries discovery for "HIST-SERVICE"
  Then only the UP instance is returned

Scenario: A scaled-up instance becomes discoverable without consumer reconfiguration
  Given one instance of "DASH-SERVICE" is registered and UP
  When a second instance of "DASH-SERVICE" registers
  Then both instances are returned for discovery
  And no consumer configuration change is required

Scenario Outline: Each platform service resolves by its stable logical id
  Given an instance of "<service>" is registered and UP
  When a consumer queries discovery for "<service>"
  Then the registry returns that instance

  Examples:
    | service                     |
    | GEST-SERVICE                |
    | EVNT-SERVICE                |
    | PAC-SERVICE                 |
    | ACS-SERVICE                 |
    | HIST-SERVICE                |
    | DASH-SERVICE                |
    | EDGE-SERVICE                |
    | NEUROASSIST-AI-API-GATEWAY  |
