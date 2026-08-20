package com.neuroassist.pac.domain.service;

/**
 * Domain service for Paciente business rules.
 *
 * <p>Pure POJO — zero Spring/JPA imports (RULE-01/RULE-02). Registered as {@code @Bean} in
 * DomainServiceConfig. Currently only holds invariant validation; deeper rules (emergency gate
 * eligibility, lifecycle checks) live in dedicated domain services.
 */
public class PacienteDomainService {

    /**
     * Validates that the nome is not null and not blank.
     *
     * <p>Bean Validation (@NotBlank) already guards the API surface; this provides a domain-level
     * safety net for programmatic callers that bypass the controller.
     */
    public void validateNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Paciente nome must not be blank");
        }
    }
}
