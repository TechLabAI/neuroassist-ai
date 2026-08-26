package com.neuroassist.gest.bdd;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

/**
 * Stateless JWT builder for BDD tests. Uses the RSA keypair from {@link CucumberSpringContext} to
 * sign tokens that the BDD JwtDecoder (backed by the same RSA public key) will accept.
 *
 * <p>Usage: {@code TestJwtFactory.buildManageToken("emp_test")} returns a Bearer token string
 * suitable for the {@code Authorization} header in MockMvc requests.
 */
public final class TestJwtFactory {

    private TestJwtFactory() {}

    /**
     * Builds a signed JWT with the given tenant and scopes.
     *
     * @param tenant tenantId claim value (e.g. "emp_test")
     * @param scopes space-separated authority strings
     * @return serialized JWT string (without "Bearer " prefix)
     */
    public static String buildToken(String tenant, String... scopes) {
        try {
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) CucumberSpringContext.RSA_KEY_PAIR.getPrivate();
            JWSSigner signer = new RSASSASigner(privateKey);

            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject("bdd-user")
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            // "tenant_id" e o claim canonico e unico desde E2E-021; o alias
                            // "tenant" que estava aqui nao era lido pelo TenantContextFilter, que
                            // caia no "sub" e devolvia 503 procurando o schema "t_bdd-user".
                            .claim("tenant_id", tenant)
                            .claim("scope", String.join(" ", scopes))
                            .build();

            SignedJWT jwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test JWT for tenant=" + tenant, e);
        }
    }

    /**
     * Token de COORDENADOR_CLINICA — o conjunto de authorities que o usuario em memoria {@code
     * gest-manager} carregava no {@code SecurityConfig} antes de o HTTP Basic ser removido, copiado
     * valor por valor.
     *
     * <p>A diferenca em relacao a {@link #buildReadToken(String)} e o que os cenarios de RBAC
     * medem: se os dois tokens ficassem iguais, os cenarios de 403 passariam a testar nada.
     */
    public static String buildManageToken(String tenant) {
        return buildToken(
                tenant,
                "gest.tipo-gesto.read",
                "gest.tipo-gesto.criar",
                "gest.tipo-gesto.editar",
                "gest.detection-profile.publicar",
                "gest.detection-profile.read",
                "gest.eligibility.read",
                "gest.detection-contract.read",
                "gest.detection-contract.validar");
    }

    /**
     * Token de CUIDADORA — o conjunto do antigo usuario em memoria {@code gest-reader}: le tudo,
     * nao cria, nao edita, nao publica. CUIDADORA mirrors: both read and validar for
     * detection-contract (POST is guarded by validar, not read — E2E-026).
     */
    public static String buildReadToken(String tenant) {
        return buildToken(
                tenant,
                "gest.tipo-gesto.read",
                "gest.eligibility.read",
                "gest.detection-profile.read",
                "gest.detection-contract.read",
                "gest.detection-contract.validar");
    }

    /**
     * Token assinado e valido, mas SEM claim de tenant — o caso que o {@code TenantContextFilter}
     * tem de recusar com 403 {@code tenant_claim_missing} em vez de deixar chegar ao JDBC.
     */
    public static String buildTokenWithoutTenant(String... scopes) {
        try {
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) CucumberSpringContext.RSA_KEY_PAIR.getPrivate();
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject("bdd-user-sem-tenant")
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .claim("scope", String.join(" ", scopes))
                            .build();
            SignedJWT jwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build tenantless test JWT", e);
        }
    }

    /**
     * Builds a token with only tipo-gesto.read scope (used for authorization failure scenarios).
     */
    public static String buildReadOnlyToken(String tenant) {
        return buildToken(tenant, "gest.tipo-gesto.read");
    }
}
