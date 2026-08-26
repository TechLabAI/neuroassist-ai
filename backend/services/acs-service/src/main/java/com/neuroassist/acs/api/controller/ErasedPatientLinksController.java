package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.CaregiverPatientLinkUseCase;
import com.neuroassist.acs.shared.audit.AuditAction;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vínculos de um paciente cujos dados pessoais foram apagados (LGPD).
 *
 * <p><b>Controller próprio, e não mais um método em {@code CaregiverPatientLinkController}.</b>
 * Aquele está mapeado em {@code /api/v1/acs/caregivers}, e esta operação é sobre um
 * <i>paciente</i>: pendurá-la lá produziria {@code /acs/caregivers/patients/&#123;id&#125;/links},
 * uma URL que mente sobre o recurso. Escrevi o cliente do PAC pedindo {@code /acs/patients/…} — a
 * URL que a operação merece — e só descobri a divergência quando a chamada real devolveu 404. É a
 * 21ª ocorrência do padrão de plan-e2e-integration.md §11, desta vez cometida por mim: o caminho
 * foi escrito a partir da intenção, não do mapeamento.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acs/patients")
@RequiredArgsConstructor
public class ErasedPatientLinksController {

    private final CaregiverPatientLinkUseCase caregiverPatientLinkUseCase;

    /**
     * DELETE /api/v1/acs/patients/{pacienteId}/links — revoga TODOS os vínculos do paciente.
     *
     * <p><b>É a única porta do sistema que atravessa a invariante D-204</b>, e por isso tem
     * authority própria: {@code acs.vinculo.revogar-apagamento}, concedida apenas ao cliente de
     * serviço do pac-service. Nenhum papel humano a possui, e há teste percorrendo todos eles.
     *
     * <p>A invariante protege o caminho do alarme de um paciente vivo; um paciente apagado não
     * produz alarme, e manter o vínculo apenas preservaria a associação "esta cuidadora cuida desta
     * pessoa" — que é exatamente o que o apagamento se propôs a remover.
     *
     * <p>Idempotente: paciente sem vínculos ativos responde 200 com {@code revogados: 0}.
     */
    @DeleteMapping("/{pacienteId}/links")
    @PreAuthorize("hasAuthority('acs.vinculo.revogar-apagamento')")
    @AuditAction("REVOKE_ALL_LINKS_FOR_ERASED_PATIENT")
    public ResponseEntity<ErasedPatientLinksRevoked> revokeAllForErasedPatient(
            @PathVariable UUID pacienteId) {
        int revogados = caregiverPatientLinkUseCase.revokeAllForErasedPatient(pacienteId);
        return ResponseEntity.ok(new ErasedPatientLinksRevoked(pacienteId, revogados));
    }

    /** Quantos vínculos foram revogados pelo apagamento. */
    public record ErasedPatientLinksRevoked(UUID pacienteId, int revogados) {}
}
