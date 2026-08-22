package com.neuroassist.hist.application.port.out;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port — configurable fact-type vocabulary (P9, HIST-023). In P1/P2, the vocabulary is
 * hard-coded in the {@link com.neuroassist.hist.domain.model.FactType} enum. This port becomes
 * relevant in P9 when the catalog is loaded from DB / application.yml.
 */
public interface FactTypeRepositoryPort {

    /**
     * Returns all active fact-type codes for the given vocabulary version. Returns an empty list if
     * the vocabulary is managed by the enum (P1..P8).
     */
    List<String> findActiveByVersion(String version);

    /** Checks whether a given code is active in any version. */
    Optional<String> findByCode(String code);
}
