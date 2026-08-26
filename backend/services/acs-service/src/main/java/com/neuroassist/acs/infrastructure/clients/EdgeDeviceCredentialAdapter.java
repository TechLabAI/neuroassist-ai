package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.port.out.DeviceCredentialVerificationPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link DeviceCredentialVerificationPort} over {@link
 * EdgeDeviceCredentialClient} (E2E-005).
 *
 * <p><b>Fail-closed and uniform.</b> Every outcome that is not an explicit "valid" collapses to an
 * empty {@link Optional}: 401 (unknown client, wrong secret, retired device, inactive tenant),
 * edge-service unreachable, circuit open, malformed body. The caller turns all of them into the
 * same 401, so a device cannot tell "wrong secret" from "EDGE is down" — §7.5 rule 3.
 *
 * <p><b>Logging.</b> The {@code clientSecret} is never logged, at any level; only {@code clientId}
 * and the failure class appear, and only at {@code debug}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeDeviceCredentialAdapter implements DeviceCredentialVerificationPort {

    private final EdgeDeviceCredentialClient edgeClient;

    @Override
    public Optional<VerifiedDevice> verify(String clientId, String clientSecret) {
        DeviceCredentialVerifyResponse response;
        try {
            response =
                    edgeClient.verifyCredential(
                            new DeviceCredentialVerifyRequest(clientId, clientSecret));
        } catch (feign.FeignException.Unauthorized | feign.FeignException.Forbidden e) {
            log.debug("Device credential rejected by edge-service: clientId={}", clientId);
            return Optional.empty();
        } catch (Exception e) {
            // Includes edge-service down / circuit open: a device token cannot be issued without a
            // verdict, and guessing would hand out edge.device on an unverified secret.
            log.debug(
                    "Device credential verification failed: clientId={} error={}",
                    clientId,
                    e.toString());
            return Optional.empty();
        }

        if (response == null || !response.valid() || response.tenantId() == null) {
            log.debug(
                    "Device credential not valid per edge-service: clientId={} body={}",
                    clientId,
                    response);
            return Optional.empty();
        }
        return Optional.of(new VerifiedDevice(response.deviceId(), response.tenantId()));
    }
}
