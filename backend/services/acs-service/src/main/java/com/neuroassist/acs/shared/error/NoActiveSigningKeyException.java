package com.neuroassist.acs.shared.error;

/**
 * Thrown when no active signing key is found in the signing_key table. Maps to HTTP 500 (system
 * misconfiguration — requires operator intervention).
 */
public class NoActiveSigningKeyException extends CodedException {

    public NoActiveSigningKeyException() {
        super(
                "acs.signing-key.no-active-key",
                "No active signing key found — system misconfiguration");
    }
}
