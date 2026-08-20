package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * Thrown when a login already exists in the tenant (unique constraint violation). Maps to HTTP 409.
 * SECURITY: never discriminate between existing login vs. other conflict causes.
 */
public class LoginDuplicateException extends CodedException {

    public LoginDuplicateException(String login, UUID tenantId) {
        super("acs.cuidador.login-duplicate", "Login already exists in this tenant");
        withContext(Map.of("login", login, "tenantId", tenantId.toString()));
    }
}
