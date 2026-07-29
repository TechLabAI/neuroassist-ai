# Covers SPEC-05
Feature: Re-run idempotency, concurrency and checksum drift

Background:
  Given the tenant-template migration set is at current version "V0002"

Scenario: Re-running migrate-all when every schema is at head applies nothing
  Given schema "t_b3f1" is at version "V0002"
  When MIG runs with mode "migrate-all"
  Then the run exits with code 0
  And no new version was applied to schema "t_b3f1"

Scenario: Two concurrent runs against the same schema are serialized
  Given schema "t_b3f1" is at version "V0001"
  When two migration runs are started concurrently against schema "t_b3f1"
  Then the second run waits for the first run's advisory lock on schema "t_b3f1"
  And schema "t_b3f1" ends at version "V0002"
  And no version is applied twice

Scenario: A checksum mismatch aborts the schema without auto-repair
  Given schema "t_b3f1" has an applied migration whose file checksum was changed
  When MIG runs with mode "migrate-all"
  Then the run exits with code 2
  And the run report marks schema "t_b3f1" as "failed"
  And no automatic repair is attempted

Scenario: A partial failure resumes from the first pending version on the next run
  Given schema "t_b3f1" failed at version "V0002" leaving a success=false history row
  And the failed history row has been repaired by an operator
  When MIG runs with mode "migrate-all"
  Then the run exits with code 0
  And schema "t_b3f1" is at version "V0002"
