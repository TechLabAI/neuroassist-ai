package com.neuroassist.gest.infrastructure.clients.dto;

/**
 * Response DTO from edge-service pairing endpoint ({@code GET
 * /api/v1/edge/devices/{deviceId}/tenant}). Used by {@code EdgePairingClient}.
 */
public record TenantResponse(String tenantId, boolean paired) {}
