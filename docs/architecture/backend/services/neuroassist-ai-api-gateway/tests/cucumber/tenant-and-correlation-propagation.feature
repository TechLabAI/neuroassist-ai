Feature: Tenant and correlation-id propagation

# Refs: SPEC-02 (pipeline), SPEC-06 (header hardening), SPEC-07 (observability)
# Invariants: INV-2 (tenant from principal, strip inbound), INV-3 (correlation always present)

Background:
  Given the Service Registry has a healthy instance of "hist-service"
  And ACS exposes a valid JWK set

Scenario: Tenant id is derived from the principal and forwarded
  Given a caregiver holds a valid JWT whose empresa is "emp_clinica_norte"
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the forwarded request carries header "X-Empresa-Id" equal to "emp_clinica_norte"

Scenario: Spoofed inbound tenant header is stripped
  Given a caregiver holds a valid JWT whose empresa is "emp_clinica_norte"
  And the caregiver adds header "X-Empresa-Id" equal to "emp_other_tenant"
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the forwarded request carries header "X-Empresa-Id" equal to "emp_clinica_norte"
  And the forwarded request does not carry the caller-supplied "emp_other_tenant"

Scenario: Correlation id is generated when absent
  Given a caregiver holds a valid JWT
  And the request carries no "X-Correlation-Id" header
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the forwarded request carries a non-empty "X-Correlation-Id"
  And the response echoes the same "X-Correlation-Id"

Scenario: Inbound correlation id is honoured end to end
  Given a caregiver holds a valid JWT
  And the request carries "X-Correlation-Id" equal to "corr-abc-123"
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the forwarded request carries "X-Correlation-Id" equal to "corr-abc-123"
  And the response echoes "X-Correlation-Id" equal to "corr-abc-123"

Scenario: Device principal forwards its bound empresa
  Given an edge agent holds a valid device token bound to empresa "emp_familia_amaral"
  When the edge agent POSTs a structured event to "/api/evnt/events"
  Then the forwarded request carries header "X-Empresa-Id" equal to "emp_familia_amaral"
  And the forwarded request carries header "X-Principal-Kind" equal to "device"
