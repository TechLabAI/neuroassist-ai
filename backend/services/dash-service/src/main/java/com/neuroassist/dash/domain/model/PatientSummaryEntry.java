package com.neuroassist.dash.domain.model;

/**
 * Entrada resumida de um paciente na visão home do painel da cuidadora. Zero JPA/Spring.
 *
 * <p><b>Os dois campos nulos são deliberados</b> — e a razão é a mesma que separou "não há
 * dispositivo" de "não consegui perguntar ao EDGE" em E2E-018.
 *
 * @param patientId identificador opaco; sempre presente
 * @param name nome real vindo do PAC ({@code PacienteResponse.nome}). <b>{@code null} quando o PAC
 *     não respondeu</b> — nunca o id disfarçado de nome. Até aqui este campo recebia o próprio
 *     {@code patientId}, e a cuidadora via um UUID onde deveria estar uma pessoa.
 * @param hasActiveEvent há pedido de ajuda em aberto (evento {@code NAO_AVALIADO}) para este
 *     paciente. <b>{@code null} quando o EVNT não respondeu.</b> Um {@code boolean} primitivo
 *     obrigaria "não sei" a se disfarçar de {@code false} — e {@code false} aqui significa "ninguém
 *     está pedindo ajuda", que é a afirmação mais perigosa que este painel pode fazer errado. Até
 *     aqui o campo era {@code false} fixo, e o painel <i>jamais</i> indicou um alarme aberto.
 * @param deviceStatus estado do dispositivo edge; {@code "unknown"} significa "não consegui
 *     perguntar"
 */
public record PatientSummaryEntry(
        String patientId, String name, Boolean hasActiveEvent, String deviceStatus) {}
