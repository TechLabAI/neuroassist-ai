package com.neuroassist.dash.bdd;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Utilitario de teste para geracao de JWTs assinados com a chave RSA de teste.
 *
 * <p>Os tokens sao validos por 1 hora e incluem as claims necessarias para o dash-service:
 *
 * <ul>
 *   <li>{@code sub} — caregiverId ou service identity
 *   <li>{@code authorities} — lista de roles/authorities
 *   <li>{@code tenant_id} — extraido pelo TenantContextFilter
 * </ul>
 */
public final class JwtTestHelper {

    private JwtTestHelper() {}

    /**
     * Gera um JWT para um cuidador autenticado.
     *
     * <p>Inclui automaticamente {@code ROLE_CAREGIVER} e {@code dash.panel-composition.read} para
     * acesso total ao painel.
     */
    public static String caregiverToken(
            RSAPrivateKey privateKey, String caregiverId, String tenantId) {
        List<String> authorities =
                new ArrayList<>(
                        List.of("ROLE_CAREGIVER", "dash.panel-composition.read", "dash.write"));
        return buildToken(privateKey, caregiverId, tenantId, authorities);
    }

    /** Tenant usado por toda a suite; o mesmo dos cenarios de cuidador. */
    public static final String DEFAULT_TENANT = "familia-amaral";

    /**
     * Gera um JWT para um servico interno (ex.: SVC_EVNT).
     *
     * <p>O {@code sub} e "service-{authority}". <b>O token carrega tenant_id</b>: o {@code POST
     * /oauth2/token/service} do acs-service exige {@code tenantId} e escopa o token a ele — a
     * variante sem tenant ({@code issueTenantlessToken}) so existe in-process e nunca chega pela
     * rede. Emitir aqui um token sem tenant produzia uma fixture que nao existe em producao, e o
     * dash-service so descobria isso ao precisar do tenant para mintar sua propria identidade de
     * servico na chamada de volta ao evnt-service.
     */
    public static String serviceToken(RSAPrivateKey privateKey, String authority) {
        return serviceToken(privateKey, authority, DEFAULT_TENANT);
    }

    /** Variante que nomeia o tenant explicitamente. */
    public static String serviceToken(RSAPrivateKey privateKey, String authority, String tenantId) {
        return buildToken(privateKey, "service-" + authority, tenantId, List.of(authority));
    }

    // ---- private ----

    private static String buildToken(
            RSAPrivateKey privateKey, String subject, String tenantId, List<String> authorities) {
        try {
            JWSHeader header =
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-id").build();

            JWTClaimsSet.Builder claimsBuilder =
                    new JWTClaimsSet.Builder()
                            .subject(subject)
                            .issuer("http://localhost:8180/realms/neuroassist-ai")
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .claim("authorities", authorities);

            if (tenantId != null) {
                claimsBuilder.claim("tenant_id", tenantId);
            }

            SignedJWT jwt = new SignedJWT(header, claimsBuilder.build());
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate test JWT", e);
        }
    }
}
