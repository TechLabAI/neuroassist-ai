package com.neuroassist.mig.application.service;

/**
 * A provisioning run for this tenant is already in flight (api-contract: {@code run-in-progress}).
 */
public class ProvisioningInProgressException extends RuntimeException {

    public ProvisioningInProgressException(String message) {
        super(message);
    }
}
