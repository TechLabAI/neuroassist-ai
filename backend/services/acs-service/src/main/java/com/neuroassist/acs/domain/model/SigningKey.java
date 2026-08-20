package com.neuroassist.acs.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — SigningKey (RS256 JWT signing key). Stored in neuroassist_global schema. Does NOT
 * expose privateKeyEnc — raw private key material stays in infrastructure layer only. RULE-01: zero
 * org.springframework imports. RULE-02: zero jakarta.persistence imports.
 */
public record SigningKey(
        UUID id,
        String kid,
        String algorithm,
        String publicJwk,
        SigningKeyStatus status,
        Instant notBefore,
        Instant retiredAt) {}
