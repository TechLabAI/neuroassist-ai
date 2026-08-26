package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /internal/devices/verify-credential} (service-identity.md §7.3).
 *
 * <p>O {@code clientSecret} nunca é logado nem ecoado na resposta.
 */
public record VerifyCredentialRequest(@NotBlank String clientId, @NotBlank String clientSecret) {

    /** Não expõe o segredo em stack traces, logs de debug de binding ou dumps de ProblemDetail. */
    @Override
    public String toString() {
        return "VerifyCredentialRequest[clientId=" + clientId + ", clientSecret=***]";
    }
}
