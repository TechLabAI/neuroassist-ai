package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Unit tests for AutenticacaoService (ACS-006). All ports are mocked — no Spring context required.
 */
class AutenticacaoServiceTest {

    private CuidadorRepositoryPort cuidadorRepository;
    private TenantRepositoryPort tenantRepository;
    private HistAuditPort histAuditPort;
    private TokenIssuer tokenIssuer;
    private PasswordEncoder passwordEncoder;
    private JwtDecoder jwtDecoder;
    private AutenticacaoService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CUIDADOR_ID = UUID.randomUUID();
    private static final String LOGIN = "joao.silva";
    private static final String SENHA = "mySenha#2024";

    @BeforeEach
    void setUp() {
        cuidadorRepository = mock(CuidadorRepositoryPort.class);
        tenantRepository = mock(TenantRepositoryPort.class);
        histAuditPort = mock(HistAuditPort.class);
        tokenIssuer = mock(TokenIssuer.class);
        jwtDecoder = mock(JwtDecoder.class);
        passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests

        Tenant activeTenant = new Tenant(TENANT_ID, "Test Tenant", TenantTipo.CLINICA, TenantStatus.ACTIVE, "schema_test", Instant.now());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant));

        service =
                new AutenticacaoService(
                        cuidadorRepository,
                        tenantRepository,
                        histAuditPort,
                        tokenIssuer,
                        passwordEncoder,
                        jwtDecoder,
                        new SimpleMeterRegistry());
    }

    @Test
    void authenticate_validCredentials_returnsToken() {
        String hash = passwordEncoder.encode(SENHA);
        Cuidador cuidador = buildCuidador(hash);

        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());
        // recordFact is void — default mock behavior is do-nothing (no stubbing needed)

        AutenticacaoResponse result =
                service.authenticate(new AutenticacaoRequest("password", LOGIN, SENHA));

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access-token-value");
        verify(tokenIssuer).issue(cuidador);
    }

    @Test
    void authenticate_loginNotFound_throws401() {
        when(cuidadorRepository.findByLogin(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest("password", "ghost", SENHA)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void authenticate_wrongSenha_throws401() {
        String hash = passwordEncoder.encode(SENHA);
        Cuidador cuidador = buildCuidador(hash);

        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest("password", LOGIN, "wrongSenha")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void authenticate_unsupportedGrantType_throws401() {
        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest(
                                                "client_credentials", LOGIN, SENHA)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(cuidadorRepository, never()).findByLogin(anyString());
    }

    @Test
    void authenticate_histAuditFails_doesNotPropagateError() {
        String hash = passwordEncoder.encode(SENHA);
        Cuidador cuidador = buildCuidador(hash);

        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());
        // Simulate audit failure — use doThrow for void methods
        org.mockito.Mockito.doThrow(new RuntimeException("audit down"))
                .when(histAuditPort)
                .recordFact(any());

        // Should NOT throw — audit is fire-and-forget
        AutenticacaoResponse result =
                service.authenticate(new AutenticacaoRequest("password", LOGIN, SENHA));

        assertThat(result).isNotNull();
    }

    private Cuidador buildCuidador(String credentialHash) {
        return new Cuidador(
                CUIDADOR_ID,
                TENANT_ID,
                "Joao Silva",
                LOGIN,
                credentialHash,
                CuidadorPapel.CUIDADORA,
                CuidadorStatus.ACTIVE,
                Instant.now(),
                Instant.now());
    }

    private AutenticacaoResponse buildResponse() {
        return new AutenticacaoResponse(
                "access-token-value",
                "refresh-token-value",
                "Bearer",
                900L,
                TENANT_ID,
                CUIDADOR_ID,
                CuidadorPapel.CUIDADORA.name(),
                List.of("acs.cuidador.read", "acs.directory.read"));
    }
}
