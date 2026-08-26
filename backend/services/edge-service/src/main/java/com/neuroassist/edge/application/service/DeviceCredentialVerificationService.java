package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.DeviceCredentialVerificationUseCase;
import com.neuroassist.edge.application.port.out.DeviceDirectoryPort;
import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Verificação de credencial de dispositivo — C-18 / service-identity.md §7.
 *
 * <p>Chamado pelo ACS em {@code POST /internal/devices/verify-credential} antes de emitir um token
 * com a authority {@code edge.device}. O EDGE responde apenas "vale / não vale" mais a identidade
 * do dispositivo; o segredo e seu hash nunca saem daqui.
 *
 * <p><b>Por que não é {@code @Transactional}:</b> exatamente o motivo de C-21 — o Hibernate adquire
 * a conexão, e portanto fixa o {@code search_path}, quando a transação abre. O tenant precisa estar
 * vinculado **antes** disso, e a resolução vem do índice global (que é lido por JDBC com schema
 * qualificado, funcionando sem tenant nenhum). Ver {@link DeviceEnrollmentService}.
 *
 * <p><b>401 genérico:</b> {@code clientId} desconhecido, segredo errado, credencial revogada e
 * dispositivo aposentado devolvem todos {@code edge.credential-invalid}. O motivo real vai só para
 * o log em nível {@code debug}, para o endpoint não servir de enumeração.
 *
 * <p>RULE-03: sem {@code @Transactional} — é leitura pura.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCredentialVerificationService implements DeviceCredentialVerificationUseCase {

    private static final String INVALID_CODE = "edge.credential-invalid";
    private static final String INVALID_MESSAGE = "Device credential is invalid.";

    private final DeviceDirectoryPort deviceDirectory;
    private final DispositivoCredentialRepositoryPort credentialRepo;
    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DispositivoEdgeDomainService domainService;

    @Override
    public VerificationResult verify(String clientId, String clientSecret) {
        if (clientId == null
                || clientId.isBlank()
                || clientSecret == null
                || clientSecret.isBlank()) {
            log.debug("Verificação rejeitada: clientId ou clientSecret ausente");
            throw invalid();
        }

        // Fora de transação, schema qualificado: funciona sem tenant vinculado (C-21/C-18).
        String tenantId =
                deviceDirectory
                        .resolveTenant(clientId)
                        .orElseThrow(
                                () -> {
                                    log.debug(
                                            "Verificação rejeitada: clientId='{}' não está no"
                                                    + " índice global",
                                            clientId);
                                    return invalid();
                                });

        String previous = TenantContextHolder.getTenantIdOrNull();
        TenantContextHolder.set(tenantId);
        try {
            return verifyWithinTenant(clientId, clientSecret, tenantId);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private VerificationResult verifyWithinTenant(
            String clientId, String clientSecret, String tenantId) {

        Optional<DispositivoCredential> found = credentialRepo.findActiveByClientId(clientId);
        if (found.isEmpty()) {
            log.debug("Verificação rejeitada: sem credencial ativa para clientId='{}'", clientId);
            throw invalid();
        }
        DispositivoCredential credential = found.get();

        // Defesa contra ponteiro global desatualizado apontando para o tenant errado.
        if (!tenantId.equals(credential.tenantId())) {
            log.debug(
                    "Verificação rejeitada: tenant do índice global diverge da credencial"
                            + " clientId='{}'",
                    clientId);
            throw invalid();
        }

        if (!constantTimeEquals(sha256Hex(clientSecret), credential.secretHash())) {
            // NUNCA logar o segredo nem o hash.
            log.debug("Verificação rejeitada: segredo não confere clientId='{}'", clientId);
            throw invalid();
        }

        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(credential.dispositivoEdgeId(), tenantId)
                        .orElseThrow(
                                () -> {
                                    log.debug(
                                            "Verificação rejeitada: dispositivo '{}' não existe no"
                                                    + " tenant",
                                            credential.dispositivoEdgeId());
                                    return invalid();
                                });

        if (domainService.isRetired(device)) {
            log.debug("Verificação rejeitada: dispositivo '{}' aposentado", device.id());
            throw invalid();
        }

        log.info("Credencial de dispositivo verificada deviceId='{}'", device.id());
        return new VerificationResult(device.id(), tenantId);
    }

    private static CodedException invalid() {
        return new CodedException(INVALID_CODE, INVALID_MESSAGE);
    }

    /** Comparação em tempo constante — evita distinguir segredos por timing. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Mesmo algoritmo do {@link DeviceRegistrationService} — o hash gravado é SHA-256 hex. */
    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
