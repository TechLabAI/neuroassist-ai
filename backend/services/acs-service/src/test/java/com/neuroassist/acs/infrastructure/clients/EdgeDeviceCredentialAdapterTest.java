package com.neuroassist.acs.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.out.DeviceCredentialVerificationPort.VerifiedDevice;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link EdgeDeviceCredentialAdapter} — every non-success outcome must collapse into
 * an empty Optional so the caller cannot leak which one happened (§7.5 rule 3).
 */
class EdgeDeviceCredentialAdapterTest {

    private static final String CLIENT_ID = "edge-3fd6f0a2-0000-4000-8000-000000000001";
    private static final String CLIENT_SECRET = "device-secret";
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private EdgeDeviceCredentialClient client;
    private EdgeDeviceCredentialAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(EdgeDeviceCredentialClient.class);
        adapter = new EdgeDeviceCredentialAdapter(client);
    }

    @Test
    @DisplayName("maps a valid verification to the device identity")
    void mapsValidVerification() {
        when(client.verifyCredential(any()))
                .thenReturn(new DeviceCredentialVerifyResponse(true, DEVICE_ID, TENANT_ID));

        Optional<VerifiedDevice> result = adapter.verify(CLIENT_ID, CLIENT_SECRET);

        assertThat(result).contains(new VerifiedDevice(DEVICE_ID, TENANT_ID));
    }

    @Test
    @DisplayName("forwards clientId and clientSecret verbatim in the body")
    void forwardsCredential() {
        when(client.verifyCredential(any()))
                .thenReturn(new DeviceCredentialVerifyResponse(true, DEVICE_ID, TENANT_ID));

        adapter.verify(CLIENT_ID, CLIENT_SECRET);

        ArgumentCaptor<DeviceCredentialVerifyRequest> captor =
                ArgumentCaptor.forClass(DeviceCredentialVerifyRequest.class);
        verify(client).verifyCredential(captor.capture());
        assertThat(captor.getValue().clientId()).isEqualTo(CLIENT_ID);
        assertThat(captor.getValue().clientSecret()).isEqualTo(CLIENT_SECRET);
        // toString must never expose the secret — it is the value most likely to end up in a log.
        assertThat(captor.getValue().toString()).doesNotContain(CLIENT_SECRET);
    }

    @Test
    @DisplayName("empty when edge-service answers 401")
    void emptyOn401() {
        when(client.verifyCredential(any())).thenThrow(feignException(401));

        assertThat(adapter.verify(CLIENT_ID, CLIENT_SECRET)).isEmpty();
    }

    @Test
    @DisplayName("empty when edge-service is unavailable — never assume valid")
    void emptyWhenEdgeDown() {
        when(client.verifyCredential(any())).thenThrow(new RuntimeException("connection refused"));

        assertThat(adapter.verify(CLIENT_ID, CLIENT_SECRET)).isEmpty();
    }

    @Test
    @DisplayName("empty when the body says valid=false")
    void emptyOnInvalidFlag() {
        when(client.verifyCredential(any()))
                .thenReturn(new DeviceCredentialVerifyResponse(false, DEVICE_ID, TENANT_ID));

        assertThat(adapter.verify(CLIENT_ID, CLIENT_SECRET)).isEmpty();
    }

    @Test
    @DisplayName("empty when the body carries no tenant")
    void emptyWithoutTenant() {
        when(client.verifyCredential(any()))
                .thenReturn(new DeviceCredentialVerifyResponse(true, DEVICE_ID, null));

        assertThat(adapter.verify(CLIENT_ID, CLIENT_SECRET)).isEmpty();
    }

    @Test
    @DisplayName("empty on a null body")
    void emptyOnNullBody() {
        when(client.verifyCredential(any())).thenReturn(null);

        assertThat(adapter.verify(CLIENT_ID, CLIENT_SECRET)).isEmpty();
    }

    private static FeignException feignException(int status) {
        Request request =
                Request.create(
                        Request.HttpMethod.POST,
                        "/internal/devices/verify-credential",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate());
        return FeignException.errorStatus(
                "verifyCredential",
                feign.Response.builder()
                        .status(status)
                        .reason("Unauthorized")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }
}
