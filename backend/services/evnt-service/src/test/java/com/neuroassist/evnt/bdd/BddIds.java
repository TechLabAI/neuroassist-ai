package com.neuroassist.evnt.bdd;

/**
 * Fixture identifiers for the BDD suite.
 *
 * <p>The scenarios used to say {@code "pac-1"} and {@code "cg-1"}. Those read well but are not
 * shapes this system produces: acs-service and pac-service both type patient and caregiver
 * identifiers as {@code UUID}, and hist-service rejects an audit fact whose subject id is not one.
 * A suite whose fixtures cannot appear in production cannot exercise the code that depends on their
 * shape — the audit-fact emission was the first thing to notice.
 */
public final class BddIds {

    /** The patient every scenario refers to as "pac-1". */
    public static final String PACIENTE = "11111111-1111-4111-8111-111111111111";

    /** The caregiver every scenario refers to as "cg-1". */
    public static final String CUIDADOR = "22222222-2222-4222-8222-222222222222";

    private BddIds() {}
}
