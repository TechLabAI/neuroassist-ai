package com.neuroassist.acs.application.port.out;

import com.neuroassist.acs.domain.model.SigningKey;
import java.util.List;

/**
 * Outbound port — SigningKeyPort. Provides access to JWT RS256 signing keys stored in
 * neuroassist_global schema. Does NOT expose private key material (privateKeyEnc stays in
 * infrastructure layer). Zero Spring/JPA imports (RULE-01, RULE-02, RULE-06).
 */
public interface SigningKeyPort {

    /**
     * Returns the currently active signing key for JWT issuance. Throws if no active key exists
     * (system misconfiguration).
     *
     * @return the active SigningKey (public metadata only — no privateKeyEnc)
     */
    SigningKey getActiveSigningKey();

    /**
     * Returns all keys suitable for JWT validation (active + recently retired). Used by the JWKS
     * endpoint to enable token validation during key rotation.
     *
     * @return list of public SigningKeys (active and recently retired keys)
     */
    List<SigningKey> getPublicKeys();
}
