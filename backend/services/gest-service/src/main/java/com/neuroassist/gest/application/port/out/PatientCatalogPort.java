package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.Sensibilidade;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de saída — a faixa de sensibilidade que a cuidadora calibrou para cada gesto DESTE
 * paciente, lida do catálogo ativo do PAC.
 *
 * <p>É leitura, não cópia. A sensibilidade é propriedade do {@code comando} do paciente, e o PAC é
 * o dono do dado; snapshotá-la dentro do perfil publicado criaria uma cópia que envelhece no
 * instante em que alguém recalibra (E2E-034).
 */
public interface PatientCatalogPort {

    /**
     * @param pacienteId paciente cujo catálogo ativo se quer
     * @return as faixas por primitivo, ou um resultado degradado quando o PAC não respondeu
     */
    PatientBands bandsFor(UUID pacienteId);

    /**
     * @param catalogoVersao versão do catálogo, semente do ETag combinado; {@code null} quando
     *     desconhecida
     * @param porPrimitivo faixa por {@code tipoGestoId} ({@code comando.primitivo_principal_id});
     *     gesto ausente do mapa não tem comando calibrado e usa a faixa padrão
     * @param degraded {@code true} SÓ quando o PAC não respondeu — nunca quando ele respondeu "este
     *     paciente não tem catálogo"
     */
    record PatientBands(
            Integer catalogoVersao, Map<UUID, Sensibilidade> porPrimitivo, boolean degraded) {

        public static PatientBands of(int catalogoVersao, Map<UUID, Sensibilidade> porPrimitivo) {
            return new PatientBands(catalogoVersao, Map.copyOf(porPrimitivo), false);
        }

        /** O PAC respondeu: paciente sem catálogo publicado. Estado legítimo, não falha. */
        public static PatientBands empty() {
            return new PatientBands(0, Map.of(), false);
        }

        /**
         * O PAC não respondeu. Serve-se a faixa padrão e diz-se isso — o dispositivo está na casa
         * do paciente, e degradar é aceitável; parar de detectar não é.
         */
        public static PatientBands unavailable() {
            return new PatientBands(null, Map.of(), true);
        }
    }
}
