package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.out.EnrollmentDirectoryPort;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolve o tenant de um enroll anônimo e delega (C-21).
 *
 * <p>{@code POST /api/v1/edge/devices/enroll} não tem JWT — o enrollment token é a credencial
 * (ADR-005) — então o {@code TenantContextFilter} não vincula tenant nenhum e o {@code search_path}
 * fica no schema global, onde {@code dispositivo_enrollment} não existe.
 *
 * <p><b>Por que um serviço separado, e não uma resolução dentro do {@link
 * DeviceRegistrationService}:</b> aquela classe é {@code @Transactional} no nível do tipo. O
 * Hibernate adquire a conexão — e portanto fixa o {@code search_path} — quando a transação abre.
 * Vincular o tenant depois disso não re-roteia nada. A resolução precisa acontecer **fora** da
 * transação, e é isso que esta classe garante ao não ser transacional.
 *
 * <p>RULE-03: sem {@code @Transactional} aqui; a escrita continua sendo do {@link
 * DeviceRegistrationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEnrollmentService {

    private final EnrollmentDirectoryPort enrollmentDirectory;
    private final DeviceRegistrationUseCase deviceRegistrationUseCase;

    /**
     * Resolve o tenant pelo hash do token, vincula ao contexto e delega o enroll.
     *
     * <p>Um token desconhecido devolve o mesmo {@code edge.enrollment-invalid} que um token
     * expirado ou já consumido — o chamador não consegue distinguir os casos nem usar o endpoint
     * para descobrir se um token existe.
     */
    public DeviceRegistrationUseCase.EnrollResult enroll(
            String enrollmentToken, String agentVersion) {

        String tokenHash = sha256Hex(enrollmentToken);
        String tenantId =
                enrollmentDirectory
                        .resolveTenant(tokenHash)
                        .orElseThrow(
                                () -> {
                                    log.warn("Enroll rejeitado: token não está no índice global");
                                    return new CodedException(
                                            "edge.enrollment-invalid",
                                            "Enrollment token is invalid, expired, or already"
                                                    + " used.");
                                });

        String previous = TenantContextHolder.getTenantIdOrNull();
        TenantContextHolder.set(tenantId);
        try {
            log.debug("Enroll: tenant {} resolvido pelo índice global", tenantId);
            return deviceRegistrationUseCase.enroll(enrollmentToken, agentVersion);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    /** Mesmo algoritmo do {@code DeviceRegistrationService} — o índice guarda o mesmo hash. */
    private static String sha256Hex(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
