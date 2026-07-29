# Covers SPEC-03
Feature: Synchronous tenant schema provisioning

Background:
  Given the global schema neuroassist_global is at version "V0001"
  And the tenant-template migration set is at current version "V0002"

Scenario: Provisioning a new tenant creates and migrates its schema to head
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And tenant schema "t_b3f1" does not exist
  When ACS invokes provisioning for tenant "b3f1"
  Then the response indicates schema "t_b3f1" was provisioned
  And schema "t_b3f1" is at version "V0002"

Scenario: Re-provisioning a current tenant is an idempotent no-op
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And tenant schema "t_b3f1" exists at version "V0002"
  When ACS invokes provisioning for tenant "b3f1"
  Then the response indicates schema "t_b3f1" was already current
  And no DDL was executed

Scenario: Provisioning an unknown tenantId is rejected before any DDL
  Given the tenant registry has no tenant "ffff"
  When ACS invokes provisioning for tenant "ffff"
  Then the response is rejected with code "invalid-tenant-id"
  And no DDL was executed

Scenario: The tenant template is identical for familia and clinica
  Given the tenant registry contains tenant "b3f1" of tipo "familia"
  And the tenant registry contains tenant "9a2c" of tipo "clinica"
  When ACS invokes provisioning for tenant "b3f1"
  And ACS invokes provisioning for tenant "9a2c"
  Then schema "t_b3f1" and schema "t_9a2c" have the same set of tables

Scenario: Provisioning ensures the global schema is at head first
  Given the global schema neuroassist_global is behind head
  And the tenant registry contains tenant "b3f1" of tipo "familia"
  When ACS invokes provisioning for tenant "b3f1"
  Then schema "neuroassist_global" is at the latest version
  And schema "t_b3f1" is at version "V0002"
