package com.neuroassist.gest.infrastructure.clients;

/**
 * Response payload from the edge-service device pairing check. Contains the tenantId associated
 * with the paired device and a boolean indicating whether the device is currently paired.
 */
public record TenantPairingResponse(String tenantId, boolean paired) {}
