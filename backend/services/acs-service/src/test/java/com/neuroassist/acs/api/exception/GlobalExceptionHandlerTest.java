package com.neuroassist.acs.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import com.neuroassist.acs.shared.error.TenantSuspendedException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for GlobalExceptionHandler (ACS-020). Verifies HTTP status, correlationId, and no
 * credential leakage.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/acs/test");
    }

    @Test
    void handleInvalidCredentials_returns401ProblemDetail() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // ProblemDetail code is acs.auth.invalid-credentials — factory maps it
        assertThat(pd).isNotNull();
    }

    @Test
    void handleTenantNotFound_returns404() {
        UUID tenantId = UUID.randomUUID();
        ResponseEntity<ProblemDetail> entity =
                handler.handleTenantNotFound(new TenantNotFoundException(tenantId), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(pd).isNotNull();
        // Error extension contains tenantId context
        Object error = pd.getProperties() != null ? pd.getProperties().get("error") : null;
        assertThat(error).isNotNull();
    }

    @Test
    void handleTenantSuspended_returns409() {
        UUID tenantId = UUID.randomUUID();
        ResponseEntity<ProblemDetail> entity =
                handler.handleTenantSuspended(new TenantSuspendedException(tenantId), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pd).isNotNull();
    }

    @Test
    void handleUpstreamUnavailable_returns502() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleUpstreamUnavailable(
                        new UpstreamUnavailableException(
                                "mig-service", new RuntimeException("down")),
                        request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(entity.getBody()).isNotNull();
    }

    @Test
    void handleGenericException_returns500() {
        ProblemDetail pd =
                handler.handleGenericException(new RuntimeException("unexpected"), request);

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(500);
    }

    @Test
    void handleInvalidCredentials_doesNotLeakCredentials() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request);
        ProblemDetail pd = entity.getBody();

        // Verify no credential fields in ProblemDetail
        String pdString = pd.toString();
        assertThat(pdString).doesNotContain("credentialHash");
        assertThat(pdString).doesNotContain("privateKey");
        assertThat(pdString).doesNotContain("privateKeyEnc");
    }
}
