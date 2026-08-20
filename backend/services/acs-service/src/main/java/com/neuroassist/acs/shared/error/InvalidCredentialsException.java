package com.neuroassist.acs.shared.error;

/**
 * Thrown when caregiver credentials are invalid (wrong login or senha). Maps to HTTP 401. Message
 * is intentionally generic (do not discriminate between wrong login vs. wrong password — security
 * best practice).
 */
public class InvalidCredentialsException extends CodedException {

    public InvalidCredentialsException() {
        super("acs.auth.invalid-credentials", "Invalid credentials");
    }
}
