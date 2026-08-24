package com.neuroassist.acs.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import com.neuroassist.acs.shared.error.NoActiveSigningKeyException;
import com.neuroassist.acs.shared.error.TenantAlreadySeededException;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import com.neuroassist.acs.shared.error.TenantSuspendedException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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

    /**
     * Extrai o objeto de extensão "error" do ProblemDetail — é lá que ficam code e category, os
     * campos que consumidores usam para decidir o que fazer.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorExtension(ProblemDetail pd) {
        assertThat(pd).isNotNull();
        assertThat(pd.getProperties()).isNotNull();
        return (Map<String, Object>) pd.getProperties().get("error");
    }

    @Test
    void handleInvalidCredentials_returns401ProblemDetail() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(401);

        // O defeito original: o status já era 401, mas título e categoria vinham do fallback
        // (internal_error / "Internal Server Error"), porque o catálogo do ProblemDetailFactory
        // estava vazio. Assertar só o status deixava o bug passar — daí estas duas linhas.
        assertThat(pd.getTitle()).isEqualTo("Unauthorized");
        Map<String, Object> error = errorExtension(pd);
        assertThat(error).containsEntry("code", "acs.auth.invalid-credentials");
        assertThat(error)
                .as("credencial inválida é resposta normal a entrada inválida, não bug do servidor")
                .containsEntry("category", "auth");
    }

    @Test
    void handleInvalidCredentials_titleDoesNotDiscriminateFailureReason() {
        // SEGURANÇA: ACS responde o mesmo 401 opaco para login inexistente, senha errada e
        // cliente de serviço não autorizado (ver ServiceTokenService). Nem o título nem o
        // detail podem devolver a distinção que o código evita.
        ProblemDetail pd =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request)
                        .getBody();

        assertThat(pd).isNotNull();
        assertThat(pd.getTitle()).isEqualTo("Unauthorized");
        assertThat(pd.getDetail()).isEqualTo("Invalid credentials");
        assertThat(pd.getDetail().toLowerCase())
                .doesNotContain("login")
                .doesNotContain("senha")
                .doesNotContain("password")
                .doesNotContain("client")
                .doesNotContain("tenant")
                .doesNotContain("not found")
                .doesNotContain("disabled");
    }

    @Test
    void handleAuthenticationException_isAuthCategoryNotInternalError() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleAuthenticationException(
                        new org.springframework.security.authentication
                                .InsufficientAuthenticationException("no token"),
                        request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pd.getTitle()).isEqualTo("Unauthorized");
        assertThat(errorExtension(pd)).containsEntry("category", "auth");
    }

    @Test
    void handleAccessDenied_isForbiddenCategoryNotInternalError() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleAccessDenied(
                        new org.springframework.security.access.AccessDeniedException("nope"),
                        request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pd.getTitle()).isEqualTo("Forbidden");
        assertThat(errorExtension(pd)).containsEntry("category", "forbidden");
    }

    @Test
    void handleIllegalArgument_isValidationCategoryNotInternalError() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleIllegalArgument(new IllegalArgumentException("bad enum"), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pd.getTitle()).isEqualTo("Validation Failed");
        assertThat(errorExtension(pd)).containsEntry("category", "validation");
    }

    @Test
    void handleTenantAlreadySeeded_returns409Conflict() {
        // Antes: sem código e sem handler, virava 500 / internal_error — estado de negócio
        // esperado reportado como bug do servidor.
        ResponseEntity<ProblemDetail> entity =
                handler.handleTenantAlreadySeeded(
                        new TenantAlreadySeededException(UUID.randomUUID()), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pd.getTitle()).isEqualTo("Tenant Already Seeded");
        Map<String, Object> error = errorExtension(pd);
        assertThat(error).containsEntry("code", "acs.tenant.already-seeded");
        assertThat(error).containsEntry("category", "conflict");
    }

    @Test
    void handleCoded_derivesStatusFromCatalogWhenNoDedicatedHandler() {
        // handleCoded NÃO chama setStatus: o status vem inteiramente do catálogo. Com o
        // catálogo vazio, toda CodedException sem handler dedicado saía 500.
        ProblemDetail pd = handler.handleCoded(new NoActiveSigningKeyException(), request);

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(errorExtension(pd))
                .containsEntry("code", "acs.signing-key.no-active-key")
                .containsEntry("category", "internal_error");
    }

    @Test
    void handleTenantNotFound_returns404() {
        UUID tenantId = UUID.randomUUID();
        ResponseEntity<ProblemDetail> entity =
                handler.handleTenantNotFound(new TenantNotFoundException(tenantId), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(pd.getTitle()).isEqualTo("Tenant Not Found");
        assertThat(errorExtension(pd))
                .containsEntry("code", "acs.tenant.not-found")
                .containsEntry("category", "not_found");
    }

    @Test
    void handleTenantSuspended_returns409() {
        UUID tenantId = UUID.randomUUID();
        ResponseEntity<ProblemDetail> entity =
                handler.handleTenantSuspended(new TenantSuspendedException(tenantId), request);
        ProblemDetail pd = entity.getBody();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pd.getTitle()).isEqualTo("Tenant Suspended");
        assertThat(errorExtension(pd))
                .containsEntry("code", "acs.tenant-suspended")
                .containsEntry("category", "conflict");
    }

    @Test
    void handleUpstreamUnavailable_returns502() {
        ResponseEntity<ProblemDetail> entity =
                handler.handleUpstreamUnavailable(
                        new UpstreamUnavailableException(
                                "mig-service", new RuntimeException("down")),
                        request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ProblemDetail pd = entity.getBody();
        assertThat(pd.getTitle()).isEqualTo("Upstream Unavailable");
        assertThat(errorExtension(pd))
                .as(
                        "falha de dependência não é bug local — categoria upstream_error, nao internal_error")
                .containsEntry("category", "upstream_error");
    }

    @Test
    void handleGenericException_returns500() {
        ProblemDetail pd =
                handler.handleGenericException(new RuntimeException("unexpected"), request);

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getTitle()).isEqualTo("Internal Server Error");
        assertThat(errorExtension(pd))
                .containsEntry("code", "internal_error.unhandled")
                .containsEntry("category", "internal_error");
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
