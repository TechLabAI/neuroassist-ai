package com.neuroassist.acs.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO — Authentication token (POST /oauth2/token). LGPD: no credential fields (no senha,
 * credentialHash, privateKey). Includes access token, refresh token, and principal claims.
 */
public record AutenticacaoResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID tenant,
        UUID sub,
        String papel,
        /**
         * Nome de exibicao de quem entrou.
         *
         * <p>Ja existia como claim {@code nome} no JWT, mas nao no corpo. A consequencia era o
         * frontend ter de decodificar o token so para escrever "Ola, Fulana" na tela — e um
         * navegador que decodifica JWT e um navegador que, mais cedo ou mais tarde, alguem vai
         * achar que tambem pode VALIDAR ali. Quem valida assinatura e o resource server.
         *
         * <p>{@code null} nos tokens de servico e de dispositivo: nao ha pessoa a nomear.
         */
        String nome,
        List<String> authorities) {}
