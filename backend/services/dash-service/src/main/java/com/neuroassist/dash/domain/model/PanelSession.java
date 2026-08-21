package com.neuroassist.dash.domain.model;

import java.time.Instant;

/** Efemero: registro em memoria de uma sessao SSE aberta pelo cuidador. Zero JPA/Spring. */
public record PanelSession(
        String caregiverId, String tenantId, String sessionId, Instant openedAt) {}
