package com.neuroassist.edge.application.port.in;

import java.util.UUID;

/**
 * Inbound port — verificação de credencial de dispositivo (C-18, service-identity.md §7).
 *
 * <p>O EDGE é dono da credencial (emitiu, guarda o hash, sabe se o dispositivo foi aposentado); o
 * ACS é dono da emissão de token. Este port é a única travessia dessa fronteira.
 *
 * <p>O tenant é resolvido internamente pelo índice global — nunca vem do chamador.
 */
public interface DeviceCredentialVerificationUseCase {

    /**
     * Verifica o par {@code clientId}/{@code clientSecret}.
     *
     * @throws com.neuroassist.edge.shared.error.CodedException {@code edge.credential-invalid}
     *     (401) para credencial desconhecida, segredo errado, credencial revogada ou dispositivo
     *     aposentado — indistinguíveis para o chamador
     */
    VerificationResult verify(String clientId, String clientSecret);

    /** Dispositivo identificado por uma credencial válida. */
    record VerificationResult(UUID deviceId, String tenantId) {}
}
