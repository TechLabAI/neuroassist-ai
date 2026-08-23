package com.neuroassist.mig.domain.model;

/**
 * Provisioning vocabulary, shared verbatim with acs-service's {@code MigProvisioningPort}.
 *
 * <p>The api-contract draft used {@code "provisioned"} while acs-service compares against {@code
 * "READY"}. Two vocabularies for one state is how a consumer ends up polling forever against a
 * producer that already finished, so the contract doc was aligned to this enum rather than the
 * other way round — acs-service's state machine is live and its terms are already shared with
 * {@code MigSchemaStatusResponse}.
 *
 * <p>RULE-01/RULE-02: zero Spring, zero JPA.
 */
public enum ProvisioningStatus {

    /** The schema is not known to exist yet — acs-service has not recorded one for this tenant. */
    PENDING,

    /** A provisioning run is in flight for this tenant. */
    PROVISIONING,

    /** Schema exists and is migrated to head. The only state that lets acs-service activate. */
    READY,

    /** Provisioning ran and failed. Terminal: retrying will not change the outcome by itself. */
    FAILED
}
