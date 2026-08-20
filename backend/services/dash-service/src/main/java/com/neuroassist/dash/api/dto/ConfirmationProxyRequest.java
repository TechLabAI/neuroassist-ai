package com.neuroassist.dash.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO para confirmacao de evento via EVNT. */
public record ConfirmationProxyRequest(@NotBlank String eventId) {}
