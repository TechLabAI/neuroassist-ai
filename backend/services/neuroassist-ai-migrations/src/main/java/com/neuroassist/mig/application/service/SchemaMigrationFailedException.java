package com.neuroassist.mig.application.service;

/** Flyway refused or failed on this schema (api-contract: {@code migration-failed}). */
public class SchemaMigrationFailedException extends RuntimeException {

    public SchemaMigrationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
