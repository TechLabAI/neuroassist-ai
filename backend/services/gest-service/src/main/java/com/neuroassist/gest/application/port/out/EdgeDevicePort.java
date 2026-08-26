package com.neuroassist.gest.application.port.out;

import java.util.UUID;

/**
 * Porta de saída — resolve a QUEM um dispositivo pertence: empresa e paciente.
 *
 * <p>Distinta de {@link EdgePairingPort}, que existe para o <b>guarda</b> de pareamento e fala com
 * {@code GET /devices/&#123;id&#125;/tenant} — um endpoint deliberadamente cego a paciente ("no
 * patient, liveness, or credential data", GEST FROZEN §4.2). Aquele contrato fica intacto.
 *
 * <p>Esta porta existe porque projetar o catálogo do paciente sobre o perfil da empresa (E2E-034)
 * exige saber de qual paciente se trata, e usa {@code GET /devices/&#123;id&#125;} — que já devolve
 * os dois. Manter as duas separadas é o que impede o guarda de pareamento de passar a carregar dado
 * de paciente sem ninguém decidir isso.
 */
public interface EdgeDevicePort {

    /**
     * @param deviceId dispositivo a resolver
     * @return o vínculo do dispositivo
     */
    DeviceBinding resolveBinding(UUID deviceId);

    /**
     * Três respostas, não duas — a mesma lição de {@code DetectionProfilePort.ProfilePointer} no
     * EDGE: "dispositivo sem paciente pareado" é um estado legítimo do sistema, e "não consegui
     * perguntar" não é. Colapsar os dois num {@code null} faz uma empresa recém-criada parecer uma
     * queda do EDGE.
     *
     * @param tenantId empresa dona do dispositivo; {@code null} quando desconhecido
     * @param pacienteId paciente pareado; {@code null} quando não há pareamento OU quando degradado
     * @param degraded {@code true} SÓ quando o EDGE não respondeu
     */
    record DeviceBinding(String tenantId, UUID pacienteId, boolean degraded) {

        public static DeviceBinding of(String tenantId, UUID pacienteId) {
            return new DeviceBinding(tenantId, pacienteId, false);
        }

        /** O EDGE respondeu: dispositivo existe, mas não há paciente pareado. Não é falha. */
        public static DeviceBinding unpaired(String tenantId) {
            return new DeviceBinding(tenantId, null, false);
        }

        /** O EDGE não respondeu, ou recusou. Não se sabe. */
        public static DeviceBinding unknown() {
            return new DeviceBinding(null, null, true);
        }

        public boolean hasPaciente() {
            return pacienteId != null;
        }
    }
}
