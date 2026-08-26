package com.neuroassist.evnt.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-011 §3 — a regra "ajuda nunca expira" isolada de qualquer infraestrutura. */
@DisplayName("Política de relevância clínica de um alarme (ADR-011 §3)")
class AlarmRelevancePolicyTest {

    private static final Set<String> TERMOS = Set.of("ajuda", "socorro", "help");
    private static final Instant NASCIMENTO = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UM_ANO_DEPOIS = Instant.parse("2027-01-01T00:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"ajuda", "cmd-ajuda", "AJUDA", "Ajúda", "cmd-socorro", "help-request"})
    @DisplayName("Um pedido de ajuda não expira nem com janela configurada para a urgência dele")
    void ajudaNuncaExpira(String comandoRef) {
        var policy = new AlarmRelevancePolicy(Map.of("rotina", Duration.ofSeconds(1)), TERMOS);

        assertThat(policy.isPedidoDeAjuda(comandoRef, null)).isTrue();
        assertThat(policy.janelaDeRelevancia("rotina", comandoRef, null)).isEmpty();
        assertThat(
                        policy.expirouPorRelevancia(
                                "rotina", comandoRef, null, NASCIMENTO, UM_ANO_DEPOIS))
                .isFalse();
    }

    @Test
    @DisplayName("O rótulo congelado também identifica ajuda — o comando pode ter ref opaca")
    void ajudaReconhecidaPeloRotulo() {
        var policy = new AlarmRelevancePolicy(Map.of("rotina", Duration.ofSeconds(1)), TERMOS);

        assertThat(policy.isPedidoDeAjuda("cmd-0042", "Preciso de ajuda")).isTrue();
        assertThat(
                        policy.expirouPorRelevancia(
                                "rotina",
                                "cmd-0042",
                                "Preciso de ajuda",
                                NASCIMENTO,
                                UM_ANO_DEPOIS))
                .isFalse();
    }

    @Test
    @DisplayName("Sem política para a urgência, nada expira — o default seguro do ADR-011")
    void semPoliticaNadaExpira() {
        var policy = new AlarmRelevancePolicy(Map.of(), TERMOS);

        assertThat(policy.janelaDeRelevancia("rotina", "cmd-agua", "Quero água")).isEmpty();
        assertThat(
                        policy.expirouPorRelevancia(
                                "rotina", "cmd-agua", "Quero água", NASCIMENTO, UM_ANO_DEPOIS))
                .isFalse();
    }

    @Test
    @DisplayName("Com política, expira por IDADE — nunca por contagem de tentativas")
    void expiraPorIdade() {
        var policy = new AlarmRelevancePolicy(Map.of("rotina", Duration.ofHours(2)), TERMOS);
        Instant criado = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(
                        policy.expirouPorRelevancia(
                                "rotina",
                                "cmd-agua",
                                "Quero água",
                                criado,
                                criado.plus(Duration.ofHours(1))))
                .as("dentro da janela")
                .isFalse();
        assertThat(
                        policy.expirouPorRelevancia(
                                "rotina",
                                "cmd-agua",
                                "Quero água",
                                criado,
                                criado.plus(Duration.ofHours(3))))
                .as("fora da janela")
                .isTrue();
    }

    @Test
    @DisplayName("Uma urgência sem janela não herda a janela de outra")
    void urgenciaSemJanelaNaoHerda() {
        var policy = new AlarmRelevancePolicy(Map.of("rotina", Duration.ofHours(2)), TERMOS);

        assertThat(
                        policy.expirouPorRelevancia(
                                "emergencia", "cmd-agua", "Quero água", NASCIMENTO, UM_ANO_DEPOIS))
                .isFalse();
    }
}
