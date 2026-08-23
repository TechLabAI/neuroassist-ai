package com.neuroassist.mig.application.service;

/** The tenant is not in the global registry (api-contract: {@code invalid-tenant-id}). */
public class UnknownTenantException extends RuntimeException {

    public UnknownTenantException(String message) {
        super(message);
    }
}
