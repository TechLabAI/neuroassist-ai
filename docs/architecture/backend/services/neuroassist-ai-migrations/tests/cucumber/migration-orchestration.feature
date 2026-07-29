# Covers SPEC-02
Feature: Migration orchestration across global and tenant schemas

Background:
  Given the global schema neuroassist_global exists
  And the tenant-template migration set is at current version "V0002"

Scenario: migrate-all applies the global set before any tenant set
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And schema "t_b3f1" does not exist
  When MIG runs with mode "migrate-all"
  Then the run exits with code 0
  And schema "neuroassist_global" is at version "V0001"
  And schema "t_b3f1" is at version "V0002"

Scenario: migrate-all fans out over every registered tenant
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And the tenant registry contains tenant "9a2c" of tipo "clinica"
  When MIG runs with mode "migrate-all"
  Then the run exits with code 0
  And the run report marks schema "t_b3f1" as "ok"
  And the run report marks schema "t_9a2c" as "ok"

Scenario: A failure on one tenant schema does not stop the fan-out
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And the tenant registry contains tenant "9a2c" of tipo "clinica"
  And schema "t_9a2c" has a corrupted migration history
  When MIG runs with mode "migrate-all"
  Then the run exits with code 2
  And the run report marks schema "t_b3f1" as "ok"
  And the run report marks schema "t_9a2c" as "failed"

Scenario: migrate-global only targets the global schema
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And schema "t_b3f1" does not exist
  When MIG runs with mode "migrate-global"
  Then the run exits with code 0
  And schema "neuroassist_global" is at version "V0001"
  And schema "t_b3f1" does not exist

Scenario: An empty tenant registry still migrates the global schema
  Given the tenant registry has no tenants
  When MIG runs with mode "migrate-all"
  Then the run exits with code 0
  And schema "neuroassist_global" is at version "V0001"
