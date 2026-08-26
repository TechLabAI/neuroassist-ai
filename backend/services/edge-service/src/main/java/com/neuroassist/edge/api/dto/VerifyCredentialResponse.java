package com.neuroassist.edge.api.dto;

import java.util.UUID;

/**
 * Resposta 200 de {@code POST /internal/devices/verify-credential} (service-identity.md §7.3).
 *
 * <p>Só existe em caso de sucesso — toda rejeição é um 401 genérico, então {@code valid} é sempre
 * {@code true} aqui. O campo é mantido porque é o contrato que o ACS consome.
 */
public record VerifyCredentialResponse(boolean valid, UUID deviceId, String tenantId) {

    public static VerifyCredentialResponse valid(UUID deviceId, String tenantId) {
        return new VerifyCredentialResponse(true, deviceId, tenantId);
    }
}
