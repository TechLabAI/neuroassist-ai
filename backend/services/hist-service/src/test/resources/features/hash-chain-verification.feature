# Covers SPEC-03 (chain verification), ADR-006
Feature: Chain verification proves stream integrity

Background:
  Given a provisioned tenant "familia-silva"
  And a coordinator token with scope "hist:read:all"

Scenario: A well-formed stream verifies as intact
  Given the stream "paciente:pac-1" has 3 correctly chained records
  When GET /history/verify is called for subject paciente "pac-1"
  Then the response status is 200
  And the verification status is "intact"
  And recordsVerified is 3
  And brokenAtSequenceNo is null

Scenario: A tampered record makes the stream verify as broken at its position
  Given the stream "paciente:pac-1" has 3 records
  And the record at sequenceNo 1 has been altered directly in the database
  When GET /history/verify is called for subject paciente "pac-1"
  Then the response status is 200
  And the verification status is "broken"
  And brokenAtSequenceNo is 1

Scenario: An empty stream verifies as empty
  Given the stream "paciente:pac-empty" has no records
  When GET /history/verify is called for subject paciente "pac-empty"
  Then the response status is 200
  And the verification status is "empty"
  And recordsVerified is 0

Scenario: Verification does not mutate any record
  Given the stream "paciente:pac-1" has 3 correctly chained records
  When GET /history/verify is called for subject paciente "pac-1"
  Then no record recordHash has changed
