package com.neuroassist.evnt.domain.model;

/**
 * O grau da escada de escalonamento em que uma tentativa aconteceu (ADR-011 §1).
 *
 * <p>Zero Spring/JPA (RULE-01, RULE-02). Persistido como texto em {@code
 * notification_dispatch.escalation_grade} — o nome da constante é o valor gravado.
 *
 * <p><b>Por que não basta o índice do degrau.</b> {@code escalation_order} diz "foi a terceira
 * pessoa da fila"; não diz <i>por que</i> aquela pessoa entrou. A pergunta que se faz depois de um
 * incidente é "por que a coordenadora da clínica foi acionada?", e a resposta é o grau: porque
 * nenhuma cuidadora vinculada ao paciente recebeu.
 *
 * <p>A ordem das constantes é a ordem normativa da escada, e é ela que ordena a escada em {@code
 * CaregiverLadder} — cada grau só é acionado depois de o anterior não receber.
 */
public enum EscalationGrade {

    /** Grau 1 — a destinatária original: a primeira cuidadora vinculada ao paciente. */
    CUIDADORA_ORIGINAL,

    /** Grau 2 — as demais cuidadoras vinculadas àquele paciente. */
    CUIDADORA_ADICIONAL,

    /** Grau 3 — a coordenadora da clínica. É do tenant, não de um paciente específico. */
    COORDENADOR_CLINICA,

    /**
     * Grau 4, o último — o apoio do paciente. Acionado só quando nenhuma cuidadora e nenhuma
     * coordenadora atendeu.
     */
    PACIENTE_APOIO,

    /**
     * Papel que o ACS passou a devolver e que esta versão do EVNT não conhece.
     *
     * <p>Vai para o fim da escada de propósito: um papel novo não pode preceder os graus que
     * produto declarou, e também não pode ser descartado — perder um destinatário possível é pior
     * do que acioná-lo tarde.
     */
    DESCONHECIDO
}
