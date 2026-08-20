package com.neuroassist.acs.shared.error;

import java.util.Map;

/**
 * Thrown when a token is presented with an unexpected {@code token_type} claim (ACS-026, Item B7).
 *
 * <p>Example: an access token is used where a refresh token is required, or vice versa.
 *
 * <p>Maps to HTTP 400 Bad Request (client presented the wrong token type — not an authentication
 * failure).
 */
public class InvalidTokenTypeException extends CodedException {

    public InvalidTokenTypeException(String expected, String actual) {
        super(
                "acs.auth.invalid-token-type",
                "Invalid token type: expected '" + expected + "' but got '" + actual + "'");
        withContext(Map.of("expected", expected, "actual", actual != null ? actual : "null"));
    }

    public InvalidTokenTypeException() {
        super("acs.auth.invalid-token-type", "Invalid token type — expected refresh token");
    }
}
