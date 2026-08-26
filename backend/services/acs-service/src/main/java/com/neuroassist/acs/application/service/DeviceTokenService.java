package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.DeviceTokenRequest;
import com.neuroassist.acs.application.port.out.DeviceCredentialVerificationPort;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * E2E-005 — issues device-identity tokens for edge devices ({@code POST /oauth2/token/device}).
 *
 * <p>Closes C-18: the EDGE heartbeat requires the {@code edge.device} authority, enroll hands the
 * device a {@code clientId}/{@code clientSecret} pair, and until now nothing turned that pair into
 * a token. Per {@code standards/service-identity.md} §7 the responsibility is split —
 * <b>edge-service validates the credential, acs-service issues the token</b> — because only EDGE
 * holds the credential hash and only ACS holds the signing key.
 *
 * <p>acs-service therefore never inspects the secret: it forwards the pair to edge-service through
 * {@link DeviceCredentialVerificationPort} and trusts the answer for the device's tenant. The
 * tenant in the emitted token is EDGE's, never the caller's.
 *
 * <p><b>401 for everything.</b> Unknown client id, wrong secret, retired device, inactive tenant
 * and "edge-service is down" all produce the same {@link InvalidCredentialsException}, so the
 * endpoint cannot be used to enumerate devices or to probe cluster health. The real reason is
 * logged at {@code debug} by the adapter.
 *
 * <p>No {@code @Transactional}: this use case touches no database — the credential lives in EDGE
 * and the signing key is loaded through {@link TokenIssuer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private static final String METRIC_DEVICE_TOKEN = "acs_device_token_requests_total";

    /**
     * Authority carried by a device token. Matches, verbatim, what edge-service enforces on {@code
     * POST /api/v1/edge/heartbeat}.
     */
    static final List<String> DEVICE_AUTHORITIES = List.of("edge.device");

    private final DeviceCredentialVerificationPort deviceCredentialVerification;
    private final TokenIssuer tokenIssuer;
    private final MeterRegistry meterRegistry;

    /**
     * Verifies a device credential with edge-service and issues a device token on success.
     *
     * @param request clientId + clientSecret handed to the device at enroll
     * @return access token with {@code token_type=device} and no refresh token
     * @throws InvalidCredentialsException (401) for every rejection reason
     */
    public AutenticacaoResponse issueToken(DeviceTokenRequest request) {
        Optional<DeviceCredentialVerificationPort.VerifiedDevice> verified =
                deviceCredentialVerification.verify(request.clientId(), request.clientSecret());

        if (verified.isEmpty()) {
            log.debug("Device token rejected — credential not verified by edge-service");
            meterRegistry.counter(METRIC_DEVICE_TOKEN, "status", "failure").increment();
            throw new InvalidCredentialsException();
        }

        DeviceCredentialVerificationPort.VerifiedDevice device = verified.get();
        if (device.tenantId() == null) {
            log.debug("Device token rejected — edge-service returned no tenant for the device");
            meterRegistry.counter(METRIC_DEVICE_TOKEN, "status", "failure").increment();
            throw new InvalidCredentialsException();
        }

        meterRegistry.counter(METRIC_DEVICE_TOKEN, "status", "success").increment();
        log.info(
                "Device token issued clientId={} deviceId={} tenant={}",
                request.clientId(),
                device.deviceId(),
                device.tenantId());

        return tokenIssuer.issueDeviceToken(
                request.clientId(), device.tenantId(), DEVICE_AUTHORITIES);
    }
}
