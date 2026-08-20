package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO — Authentication (POST /oauth2/token). LGPD: field "senha" intentionally named in
 * Portuguese to avoid plain "password" literal. This DTO is request-only — never returned in
 * responses.
 */
public record AutenticacaoRequest(
        @NotBlank(message = "grantType is required") String grantType,
        @NotBlank(message = "login is required") String login,
        @NotBlank(message = "senha is required") String senha) {}
