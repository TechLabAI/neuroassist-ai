Feature: Eviction and self-preservation

# Refs: SPEC-02 (registry core), SPEC-03 (standalone posture), contracts/api-contract.md §4-5

Background:
  Given the neuroassist-ai-service-registry (REG) is running as a single Eureka node

Scenario: A stale instance is evicted after the lease window
  Given an instance of "EVNT-SERVICE" is registered and UP
  When the instance stops renewing its lease beyond the expiration window
  And the eviction loop runs while self-preservation is not active
  Then the registry removes the instance from discovery

Scenario: Self-preservation holds membership during a renewal-rate dip
  Given several instances are registered and UP
  When the fleet-wide renewal rate drops below the self-preservation threshold
  Then the registry stops evicting instances
  And the last known membership is preserved

Scenario: Eviction resumes when the renewal rate recovers
  Given self-preservation is active after a renewal-rate dip
  When the renewal rate recovers above the threshold
  Then the registry resumes normal eviction of expired instances

Scenario: A transient blip never flushes the registry to empty
  Given several instances are registered and UP
  When a transient network blip causes many heartbeats to be missed at once
  Then the registry continues to advertise the last known instances
  And no discovery query returns an empty result for a still-live service
