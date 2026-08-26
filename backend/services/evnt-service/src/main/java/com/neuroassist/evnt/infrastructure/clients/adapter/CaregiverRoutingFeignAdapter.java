package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.infrastructure.clients.AcsCaregiverClient;
import com.neuroassist.evnt.infrastructure.clients.CaregiverRoutingClient;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves caregivers through the acs-service directory.
 *
 * <p>Replaces {@code CaregiverRoutingHardcodedAdapter}, which returned the literal string {@code
 * "cuidadora-jamilly-fixo"} from production code for any argument.
 *
 * <p><b>Fail-closed.</b> If the directory cannot be reached, an alarm has no known destination —
 * there is no safe default caregiver to invent. The exception propagates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaregiverRoutingFeignAdapter implements CaregiverRoutingPort {

    /** {@code CuidadorPapel.COORDENADOR_CLINICA} — o grau 3 da escada do ADR-011. */
    private static final String PAPEL_COORDENADOR = "COORDENADOR_CLINICA";

    private final CaregiverRoutingClient client;
    private final AcsCaregiverClient caregiverClient;

    /**
     * Coordenadoras ativas da clínica.
     *
     * <p><b>Fail-soft, ao contrário do resto deste adapter — e a diferença é deliberada.</b> {@code
     * responsibleFor} é fail-closed porque sem ele o alarme não tem destinatária alguma. Aqui não:
     * se este grau não puder ser resolvido, a escada continua para o grau 4 (o apoio do paciente).
     * Propagar a exceção encerraria a escada num ponto em que ainda há para quem ir — seria
     * desistir, que é a única resposta que o ADR-011 proíbe.
     *
     * <p>O 403 tem tratamento próprio porque hoje ele é o caso esperado: o cliente de serviço
     * {@code evnt-service} não tem {@code acs.cuidador.read}. Um {@code log.error} explícito impede
     * que a lacuna passe por "não há coordenadora nesta clínica".
     */
    @Override
    public List<ResponsibleCaregiver> clinicCoordinators(String tenantId) {
        UUID id = parseUuid(tenantId, "tenant");
        if (id == null) {
            return List.of();
        }
        try {
            var todos = caregiverClient.listByTenant(id);
            if (todos == null) {
                return List.of();
            }
            return todos.stream()
                    .filter(c -> c != null && c.id() != null)
                    .filter(c -> PAPEL_COORDENADOR.equalsIgnoreCase(c.papel()))
                    .filter(c -> AcsCaregiverClient.CaregiverResponse.ACTIVE.equals(c.status()))
                    .map(c -> new ResponsibleCaregiver(String.valueOf(c.id()), c.papel()))
                    .toList();
        } catch (FeignException.Forbidden e) {
            log.error(
                    "acs-service recusou a listagem de cuidadores do tenant {} (403) — o EVNT não"
                            + " tem a autoridade acs.cuidador.read. O grau 3 da escada (coordenadora"
                            + " da clínica) fica limitado a quem também esteja vinculado ao"
                            + " paciente. Ver E2E-020.",
                    id);
            return List.of();
        } catch (Exception e) {
            log.error(
                    "Não foi possível resolver as coordenadoras da clínica {}: {} — a escada segue"
                            + " para o grau seguinte em vez de parar",
                    id,
                    e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ResponsibleCaregiver> responsibleFor(String pacienteId) {
        UUID id = parseUuid(pacienteId, "patient");
        if (id == null) {
            return List.of();
        }
        var response = client.findCaregiversForPatient(id);
        if (response == null || response.responsibleCaregivers() == null) {
            return List.of();
        }
        return response.responsibleCaregivers().stream()
                .map(c -> new ResponsibleCaregiver(String.valueOf(c.cuidadorId()), c.papel()))
                .toList();
    }

    @Override
    public boolean isKnownCaregiver(String caregiverId) {
        UUID id = parseUuid(caregiverId, "caregiver");
        if (id == null) {
            return false;
        }
        try {
            // A caregiver acs-service does not know answers 404; the scope may legitimately be
            // empty for one it does know, so presence — not list size — is the signal.
            return client.findPatientScope(id) != null;
        } catch (FeignException.NotFound e) {
            log.warn("Caregiver {} is unknown to acs-service", id);
            return false;
        }
    }

    private UUID parseUuid(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Not a valid {} id: {}", what, value);
            return null;
        }
    }
}
