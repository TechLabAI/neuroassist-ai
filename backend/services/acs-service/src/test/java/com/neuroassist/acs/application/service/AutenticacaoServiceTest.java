package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.RefreshTokenRequest;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import com.neuroassist.acs.shared.error.InvalidTokenTypeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Unit tests for AutenticacaoService (ACS-006, ACS-007, ACS-026). All ports are mocked — no Spring
 * context required.
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

        Tenant activeTenant =
                new Tenant(
                        TENANT_ID,
                        "Test Tenant",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "schema_test",
                        Instant.now());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant));

        service =
                new AutenticacaoService(
                        cuidadorRepository,
                        tenantRepository,
                        histAuditPort,
                        tokenIssuer,
                        passwordEncoder,
                        jwtDecoder,
                        new SimpleMeterRegistry(),
                        ObservationRegistry.NOOP);
    }

    // -----------------------------------------------------------------------
    // authenticate() tests — ACS-006
    // -----------------------------------------------------------------------

    @Test
    void authenticate_validCredentials_returnsToken() {
        String hash = passwordEncoder.encode(SENHA);
        Cuidador cuidador = buildCuidador(hash);

        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());

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
        org.mockito.Mockito.doThrow(new RuntimeException("audit down"))
                .when(histAuditPort)
                .recordFact(any());

        // Should NOT throw — audit is fire-and-forget
        AutenticacaoResponse result =
                service.authenticate(new AutenticacaoRequest("password", LOGIN, SENHA));

        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // refresh() tests — ACS-007 / ACS-026 Item B10
    // -----------------------------------------------------------------------

    /** B10-1: Refresh válido → retorna novo access + novo refresh token. */
    @Test
    void refresh_validRefreshToken_returnsNewTokenPair() {
        Cuidador cuidador = buildCuidador("hashed");
        Jwt refreshJwt = buildRefreshJwt("refresh");

        when(jwtDecoder.decode("valid-refresh-token")).thenReturn(refreshJwt);
        when(cuidadorRepository.findById(CUIDADOR_ID)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());

        AutenticacaoResponse result =
                service.refresh(new RefreshTokenRequest("valid-refresh-token"));

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access-token-value");
        assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
        verify(tokenIssuer).issue(cuidador);
    }

    /**
     * B10-2: Refresh expirado → 401 InvalidCredentialsException. The JwtDecoder throws JwtException
     * for expired tokens.
     */
    @Test
    void refresh_expiredToken_throws401() {
        when(jwtDecoder.decode("expired-token")).thenThrow(new JwtException("Token expired"));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(cuidadorRepository, never()).findById(any());
    }

    /** B10-3: Refresh com token_type != "refresh" → 400 InvalidTokenTypeException. */
    @Test
    void refresh_wrongTokenType_throws400() {
        Jwt accessJwt = buildRefreshJwt("access"); // wrong type!
        when(jwtDecoder.decode("access-token-as-refresh")).thenReturn(accessJwt);

        assertThatThrownBy(
                        () -> service.refresh(new RefreshTokenRequest("access-token-as-refresh")))
                .isInstanceOf(InvalidTokenTypeException.class);

        verify(cuidadorRepository, never()).findById(any());
    }

    /** B10-4: Cuidador não encontrado no sub → 404 CaregiverNotFoundException. */
    @Test
    void refresh_caregiverNotFound_throws404() {
        Jwt refreshJwt = buildRefreshJwt("refresh");
        when(jwtDecoder.decode("valid-refresh-token")).thenReturn(refreshJwt);
        when(cuidadorRepository.findById(CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("valid-refresh-token")))
                .isInstanceOf(CaregiverNotFoundException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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

    /** Builds a mock Jwt with the given token_type claim and subject = CUIDADOR_ID. */
    private Jwt buildRefreshJwt(String tokenType) {
        return Jwt.withTokenValue("jwt-value")
                .header("alg", "RS256")
                .subject(CUIDADOR_ID.toString())
                .claim("token_type", tokenType)
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuedAt(Instant.now())
                .build();
    }

    // ------------------------------------------------------------------------
    // E2E-019 — fatos de autenticação (AUD-01 / AUD-02)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("login bem-sucedido grava autenticacao.realizada")
    void successfulLoginRecordsCanonicalFact() {
        Cuidador cuidador = buildCuidador(passwordEncoder.encode(SENHA));
        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());

        service.authenticate(new AutenticacaoRequest("password", LOGIN, SENHA));

        ArgumentCaptor<HistAuditPort.AuditFact> fact =
                ArgumentCaptor.forClass(HistAuditPort.AuditFact.class);
        verify(histAuditPort).recordFact(fact.capture());
        // Vocabulário controlado do hist-service. "LOGIN_SUCCESS" não pertencia a nenhum, e nunca
        // foi rejeitado porque o adapter era um no-op.
        assertThat(fact.getValue().fact()).isEqualTo("autenticacao.realizada");
        assertThat(fact.getValue().empresaId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("senha errada grava autenticacao.falhou, sem a senha no payload")
    void wrongPasswordRecordsFailureFact() {
        when(cuidadorRepository.findByLogin(LOGIN))
                .thenReturn(Optional.of(buildCuidador(passwordEncoder.encode(SENHA))));

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest("password", LOGIN, "errada")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<HistAuditPort.AuditFact> fact =
                ArgumentCaptor.forClass(HistAuditPort.AuditFact.class);
        verify(histAuditPort).recordFact(fact.capture());
        assertThat(fact.getValue().fact()).isEqualTo("autenticacao.falhou");
        assertThat(fact.getValue().payload()).containsEntry("motivo", "credencial-invalida");
        assertThat(fact.getValue().payload().keySet())
                .doesNotContain("senha", "password", "credentialHash");
    }

    @Test
    @DisplayName("login inexistente NÃO gera fato — não há empresa a que atribuí-lo")
    void unknownLoginRecordsNothing() {
        when(cuidadorRepository.findByLogin("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest("password", "fantasma", SENHA)))
                .isInstanceOf(InvalidCredentialsException.class);

        // Atribuir a um tenant arbitrário deixaria um desconhecido escrever na trilha de uma
        // empresa qualquer só chutando logins.
        verify(histAuditPort, never()).recordFact(any());
    }

    @Test
    @DisplayName("falha ao auditar não impede a autenticação")
    void auditFailureNeverBlocksLogin() {
        Cuidador cuidador = buildCuidador(passwordEncoder.encode(SENHA));
        when(cuidadorRepository.findByLogin(LOGIN)).thenReturn(Optional.of(cuidador));
        when(tokenIssuer.issue(cuidador)).thenReturn(buildResponse());
        doThrow(new RuntimeException("hist down")).when(histAuditPort).recordFact(any());

        assertThatCode(
                        () ->
                                service.authenticate(
                                        new AutenticacaoRequest("password", LOGIN, SENHA)))
                .doesNotThrowAnyException();
    }
}
