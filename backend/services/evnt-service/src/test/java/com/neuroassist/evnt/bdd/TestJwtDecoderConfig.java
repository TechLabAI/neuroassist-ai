package com.neuroassist.evnt.bdd;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * A {@link JwtDecoder} that reads its claims straight out of the token string, so BDD scenarios can
 * present any identity without acs-service being up or a signing key being shared with the suite.
 *
 * <p><b>Why this had to be added.</b> Before evnt-service became a resource server, every endpoint
 * was {@code permitAll()} and the suite posted anonymously. That is why not one of its scenarios
 * could ever have caught a 401 or a 403 — the same hole that was found and closed in gest-service.
 * With the chain switched on, the suite needs a way to be somebody.
 *
 * <p>Token grammar (no signature, test-only): {@code test|tenant=<uuid>|sub=<id>|auth=a,b,c}. A
 * token that does not start with {@code test|} is rejected, so a scenario can still assert 401.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    static final String PREFIX = "test.";

    /** Builds a token for the given tenant and authorities. */
    public static String token(String tenant, String subject, String... authorities) {
        String payload = tenant + "~" + subject + "~" + String.join("+", authorities);
        return PREFIX
                + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return tokenValue -> {
            if (tokenValue == null || !tokenValue.startsWith(PREFIX)) {
                throw new org.springframework.security.oauth2.jwt.BadJwtException(
                        "Not a test token");
            }
            String payload =
                    new String(
                            Base64.getDecoder().decode(tokenValue.substring(PREFIX.length())),
                            StandardCharsets.UTF_8);
            String[] parts = payload.split("~", -1);
            String tenant = parts.length > 0 ? parts[0] : "";
            String subject = parts.length > 1 ? parts[1] : "test-subject";
            String authorities = parts.length > 2 ? parts[2] : "";
            List<String> authorityList =
                    authorities.isBlank() ? List.of() : Arrays.asList(authorities.split("\\+"));

            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", subject);
            // The three tenant aliases acs-service emits (C-3) — the filter reads tenant_id first.
            claims.put("tenant_id", tenant);
            claims.put("tenant", tenant);
            claims.put("empresaId", tenant);
            claims.put("authorities", authorityList);

            Instant now = Instant.now();
            return new Jwt(tokenValue, now, now.plusSeconds(300), Map.of("alg", "none"), claims);
        };
    }
}
