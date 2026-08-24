package com.neuroassist.gest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * O que estes testes protegem: antes da E2E-033 os limiares eram um {@code Map<String,Object>} sem
 * validação nenhuma, e um perfil incoerente não falhava no publish — falhava na casa do paciente,
 * quando um alarme não saía.
 */
class DetectionThresholdTableTest {

    @Nested
    @DisplayName("faixa isolada")
    class FaixaIsolada {

        @Test
        void aceita_os_valores_historicos() {
            assertThatCode(() -> new DetectionThresholds(0.22, 0.27, 0.30, 2, 2000, 0.30, 0.22, 0.25, 5))
                    .doesNotThrowAnyException();
        }

        @Test
        void recusa_histerese_invertida_no_ear() {
            // earOpen abaixo de earClose: o olho estaria "aberto" e "fechado" ao mesmo tempo na
            // faixa entre os dois, e o detector oscilaria a cada frame.
            assertThatThrownBy(
                            () -> new DetectionThresholds(0.27, 0.22, 0.30, 2, 2000, 0.30, 0.22, 0.25, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("earClose")
                    .hasMessageContaining("0.27");
        }

        @Test
        void recusa_lado_oposto_acima_do_limiar_de_levantamento() {
            // briOtherMax >= briRaiseOn: o lado que deveria estar relaxado já contaria como
            // levantado, e o levantamento individual nunca se distinguiria do duplo.
            assertThatThrownBy(
                            () -> new DetectionThresholds(0.22, 0.27, 0.30, 2, 2000, 0.30, 0.22, 0.31, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("briOtherMax");
        }

        @Test
        void recusa_zero_frames_de_sustentacao() {
            // Sem sustentação mínima, um único frame de ruído do MediaPipe vira um pedido de ajuda.
            assertThatThrownBy(
                            () -> new DetectionThresholds(0.22, 0.27, 0.30, 2, 2000, 0.30, 0.22, 0.25, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("browMinFrames");
        }
    }

    @Nested
    @DisplayName("coerência entre faixas")
    class EntreFaixas {

        @Test
        void defaults_sao_validos() {
            assertThatCode(DetectionThresholdTable::defaults).doesNotThrowAnyException();
        }

        @Test
        void media_reproduz_exatamente_as_constantes_do_agente() {
            // A propriedade que torna a adoção do perfil segura: enquanto ninguém escolher outra
            // faixa, o comportamento é o de antes, valor por valor. Espelha edge_agent_core.py —
            // e check_edge_agent_drift.py compara os dois lados.
            DetectionThresholds media = DetectionThresholdTable.defaults().media();
            assertThat(media.earClose()).isEqualTo(0.22);
            assertThat(media.earOpen()).isEqualTo(0.27);
            assertThat(media.earOtherMax()).isEqualTo(0.30);
            assertThat(media.blinkMinFrames()).isEqualTo(2);
            assertThat(media.doubleBlinkWindowMs()).isEqualTo(2000);
            assertThat(media.briRaiseOn()).isEqualTo(0.30);
            assertThat(media.briRaiseOff()).isEqualTo(0.22);
            assertThat(media.briOtherMax()).isEqualTo(0.25);
            assertThat(media.browMinFrames()).isEqualTo(5);
        }

        @Test
        void recusa_alta_que_detecta_menos_que_baixa_no_ear() {
            // O defeito que esta validação existe para pegar: a cuidadora escolhe "alta" e o gesto
            // fica MAIS difícil de disparar. EAR cai quando o olho fecha, então mais sensível é um
            // limiar maior.
            DetectionThresholds invertida =
                    new DetectionThresholds(0.19, 0.24, 0.27, 2, 2500, 0.26, 0.18, 0.21, 3);
            DetectionThresholdTable defaults = DetectionThresholdTable.defaults();

            assertThatThrownBy(
                            () ->
                                    new DetectionThresholdTable(
                                            defaults.baixa(), defaults.media(), invertida))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("earClose")
                    .hasMessageContaining("crescer");
        }

        @Test
        void recusa_alta_que_exige_mais_sustentacao_que_baixa() {
            DetectionThresholds sustentacaoDemais =
                    new DetectionThresholds(0.25, 0.30, 0.33, 2, 2500, 0.26, 0.18, 0.21, 9);
            DetectionThresholdTable defaults = DetectionThresholdTable.defaults();

            assertThatThrownBy(
                            () ->
                                    new DetectionThresholdTable(
                                            defaults.baixa(), defaults.media(), sustentacaoDemais))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("browMinFrames")
                    .hasMessageContaining("decrescer");
        }

        @Test
        void forBand_devolve_a_faixa_pedida_e_cai_no_padrao_quando_nula() {
            DetectionThresholdTable table = DetectionThresholdTable.defaults();

            assertThat(table.forBand(Sensibilidade.alta).earClose()).isEqualTo(0.25);
            assertThat(table.forBand(Sensibilidade.baixa).earClose()).isEqualTo(0.19);
            assertThat(table.forBand(null)).isEqualTo(table.media());
        }
    }

    @Nested
    @DisplayName("primitivo")
    class Primitivo {

        @Test
        void sensibilidade_nula_vira_a_padrao() {
            // Um nulo aqui seria um NullPointerException a 30 fps na casa do paciente.
            DetectionProfilePrimitive p =
                    new DetectionProfilePrimitive(
                            java.util.UUID.randomUUID(),
                            java.util.UUID.randomUUID(),
                            java.util.UUID.randomUUID(),
                            "Piscar",
                            "ref/piscar-v1",
                            null);

            assertThat(p.sensibilidade()).isEqualTo(Sensibilidade.media);
        }
    }
}
