package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.DeviceTokenRequest;
import com.neuroassist.acs.application.port.out.DeviceCredentialVerificationPort;
import com.neuroassist.acs.application.port.out.DeviceCredentialVerificationPort.VerifiedDevice;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeviceTokenService} (E2E-005, {@code standards/service-identity.md} §7).
 *
 * <p>The contract under test is mostly about what acs-service refuses to do: it never inspects the
 * secret, never invents a tenant, and never lets the caller distinguish one failure from another.
 */
class DeviceTokenServiceTest {

    private static final String CLIENT_ID = "edge-3fd6f0a2-0000-4000-8000-000000000001";
    private static final String CLIENT_SECRET = "device-secret";
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private DeviceCredentialVerificationPort verificationPort;
    private TokenIssuer tokenIssuer;
    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        verificationPort = mock(DeviceCredentialVerificationPort.class);
        tokenIssuer = mock(TokenIssuer.class);
        service = new DeviceTokenService(verificationPort, tokenIssuer, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("issues a device token with edge.device and the tenant reported by edge-service")
    void issuesDeviceToken() {
        when(verificationPort.verify(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(Optional.of(new VerifiedDevice(DEVICE_ID, TENANT_ID)));
        AutenticacaoResponse expected =
                new AutenticacaoResponse(
                        "jwt", null, "Bearer", 900, TENANT_ID, null, null, null, List.of("edge.device"));
        when(tokenIssuer.issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device")))
                .thenReturn(expected);

        AutenticacaoResponse response =
                service.issueToken(new DeviceTokenRequest(CLIENT_ID, CLIENT_SECRET));

        assertThat(response).isSameAs(expected);
        assertThat(response.refreshToken()).isNull();
        assertThat(response.authorities()).containsExactly("edge.device");
        verify(tokenIssuer).issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device"));
    }

    @Test
    @DisplayName("the tenant always comes from edge-service, never from the request")
    void tenantComesFromEdge() {
        when(verificationPort.verify(anyString(), anyString()))
                .thenReturn(Optional.of(new VerifiedDevice(DEVICE_ID, TENANT_ID)));
        when(tokenIssuer.issueDeviceToken(anyString(), any(), anyList()))
                .thenReturn(
                        new AutenticacaoResponse(
                                "jwt",
                                null,
                                "Bearer",
                                900,
                                TENANT_ID,
                                null,
                                null, null,
                                List.of("edge.device")));

        service.issueToken(new DeviceTokenRequest(CLIENT_ID, CLIENT_SECRET));

        verify(tokenIssuer).issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device"));
    }

    @Test
    @DisplayName("401 when edge-service rejects the credential")
    void rejectsInvalidCredential() {
        when(verificationPort.verify(CLIENT_ID, CLIENT_SECRET)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.issueToken(new DeviceTokenRequest(CLIENT_ID, CLIENT_SECRET)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).issueDeviceToken(anyString(), any(), anyList());
    }

    @Test
    @DisplayName("401 — indistinguishable — when edge-service could not answer at all")
    void rejectsWhenEdgeUnavailable() {
        // The adapter collapses "edge down" into an empty Optional precisely so this path is the
        // same one as a wrong secret (§7.5 rule 3).
        when(verificationPort.verify(CLIENT_ID, "another-secret")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.issueToken(
                                        new DeviceTokenRequest(CLIENT_ID, "another-secret")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("401 when edge-service answers valid but without a tenant — never defaulted")
    void rejectsVerificationWithoutTenant() {
        when(verificationPort.verify(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(Optional.of(new VerifiedDevice(DEVICE_ID, null)));

        assertThatThrownBy(
                        () -> service.issueToken(new DeviceTokenRequest(CLIENT_ID, CLIENT_SECRET)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).issueDeviceToken(anyString(), any(), anyList());
    }

    @Test
    @DisplayName("the secret is forwarded to edge-service and nowhere else")
    void forwardsSecretOnlyToEdge() {
        when(verificationPort.verify(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(Optional.of(new VerifiedDevice(DEVICE_ID, TENANT_ID)));
        when(tokenIssuer.issueDeviceToken(anyString(), any(), anyList()))
                .thenReturn(
                        new AutenticacaoResponse(
                                "jwt",
                                null,
                                "Bearer",
                                900,
                                TENANT_ID,
                                null,
                                null, null,
                                List.of("edge.device")));

        AutenticacaoResponse response =
                service.issueToken(new DeviceTokenRequest(CLIENT_ID, CLIENT_SECRET));

        verify(verificationPort).verify(CLIENT_ID, CLIENT_SECRET);
        // issueDeviceToken receives the clientId as subject — the secret never reaches the issuer.
        verify(tokenIssuer).issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device"));
        assertThat(response.accessToken()).doesNotContain(CLIENT_SECRET);
    }
}
