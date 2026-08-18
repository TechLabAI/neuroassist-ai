# Covers SPEC-02 (audit ingestion & chaining), ADR-004, ADR-005
Feature: Audit fact ingestion is write-once and validated

Background:
  Given a provisioned tenant "familia-silva"
  And a producer token with scope "hist:write"

Scenario: A valid new audit fact is appended as the first record of its stream
  Given an audit fact "018f-0001" of type "evento.confirmado" for subject paciente "pac-1" by caregiver "cg-1"
  When it is submitted to POST /audit-facts
  Then the response status is 201
  And the record has sequenceNo 0
  And the record prevHash is the genesis constant
  And the response has a Location header

Scenario: A second fact on the same subject continues the chain
  Given the stream "paciente:pac-1" already has 1 record
  And an audit fact "018f-0002" of type "evento.descartado" for subject paciente "pac-1" by caregiver "cg-1"
  When it is submitted to POST /audit-facts
  Then the response status is 201
  And the record has sequenceNo 1
  And the record prevHash equals the previous record recordHash

Scenario: An unknown factType is rejected
  Given an audit fact "018f-0003" of type "evento.xpto" for subject paciente "pac-1" by caregiver "cg-1"
  When it is submitted to POST /audit-facts
  Then the response status is 422
  And the problem type is "/errors/unknown-fact-type"

Scenario: A payload carrying a biometric key is rejected
  Given an audit fact "018f-0004" of type "evento.confirmado" for subject paciente "pac-1" by caregiver "cg-1"
  And its payload contains the key "landmarks"
  When it is submitted to POST /audit-facts
  Then the response status is 422
  And the problem type is "/errors/forbidden-payload"

Scenario: A correction references a prior record and leaves it unchanged
  Given a recorded fact "018f-0002" exists for subject paciente "pac-1"
  And an audit fact "018f-0005" of type "registro.corrigido" for subject paciente "pac-1" by caregiver "cg-1"
  And it corrects fact "018f-0002"
  When it is submitted to POST /audit-facts
  Then the response status is 201
  And the record correctsRecordId points to the record of fact "018f-0002"
  And the original record of fact "018f-0002" is unchanged

Scenario: No endpoint updates or deletes a record
  Given a recorded fact "018f-0002" exists for subject paciente "pac-1"
  When a DELETE is attempted on /audit-facts of that record
  Then the response status is 405
