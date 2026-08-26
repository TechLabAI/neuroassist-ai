package com.neuroassist.mig.api.exception;

import com.neuroassist.mig.application.service.ProvisioningInProgressException;
import com.neuroassist.mig.application.service.SchemaMigrationFailedException;
import com.neuroassist.mig.application.service.UnknownTenantException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 responses with the stable {@code code} values of api-contract §1.4 (E2E-016).
 *
 * <p>The status codes carry the distinction the caller acts on: 4xx means acs-service asked for
 * something that will be refused identically next time, 5xx means try again. Collapsing the two is
 * how a permission problem ends up retried as an outage — the mistake this platform made in the
 * EDGE heartbeat.
 */
@Slf4j
@RestControllerAdvice
public class ProvisioningExceptionHandler {

    @ExceptionHandler(UnknownTenantException.class)
    public ProblemDetail unknownTenant(UnknownTenantException e) {
        log.warn("Provisioning refused — unknown tenant: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-tenant-id", e.getMessage());
    }

    /** An invalid schema name is a caller error, and never a reason to retry. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidArgument(IllegalArgumentException e) {
        log.warn("Provisioning refused — invalid argument: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "invalid-tenant-id", e.getMessage());
    }

    @ExceptionHandler(ProvisioningInProgressException.class)
    public ProblemDetail inProgress(ProvisioningInProgressException e) {
        log.warn("Provisioning refused — run in progress: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "run-in-progress", e.getMessage());
    }

    @ExceptionHandler(SchemaMigrationFailedException.class)
    public ProblemDetail migrationFailed(SchemaMigrationFailedException e) {
        // The tenant is now half-provisioned: the schema may exist with part of the history
        // applied. Loud on purpose — this is the one failure an operator must see.
        log.error("Provisioning FAILED: {}", e.getMessage(), e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "migration-failed", "schema migration failed");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return pd;
    }
}
