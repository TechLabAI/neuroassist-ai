package com.neuroassist.dash.domain.model;

import java.time.Instant;

/** Payload de notificacao entregue via SSE ao painel do cuidador. Zero JPA/Spring. */
public record NotificationPayload(
        String dispatchId, String tier, String message, Instant issuedAt) {}
