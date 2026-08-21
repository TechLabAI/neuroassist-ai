package com.neuroassist.hist.domain.model;

/**
 * Thrown when the request payload contains a forbidden biometric key ({@code landmarks}, {@code
 * image}, {@code frame}, {@code video}, {@code embedding}). Maps to HTTP 422 / {@code type:
 * /errors/forbidden-payload}.
 */
public class ForbiddenPayloadException extends HistDomainException {

    public ForbiddenPayloadException(String forbiddenKey) {
        super(
                "forbidden-payload",
                "Payload contains forbidden key '"
                        + forbiddenKey
                        + "'. Biometric/image data must not be stored in the ledger.");
    }
}
