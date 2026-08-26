package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.domain.model.EscalationGrade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A escada de escalonamento de um alarme, em ordem normativa e determinística (ADR-011 §1).
 *
 * <p><b>Quatro graus, e a ordem é normativa — não sugestão.</b> Cada um só é acionado depois de o
 * anterior não receber; é escada, não leque:
 *
 * <ol>
 *   <li>a cuidadora destinatária original;
 *   <li>as demais cuidadoras vinculadas àquele paciente;
 *   <li>a coordenadora da clínica;
 *   <li>o apoio do paciente — último grau.
 * </ol>
 *
 * <p><b>Por que a lista não sai toda do mesmo lugar.</b> Coordenadora e apoio não são "cuidadoras
 * do paciente". O apoio é vinculado ao paciente e vem no directory junto com as cuidadoras,
 * distinguido pelo {@code papel}; a coordenadora é da <b>clínica</b> (tenant) e é resolvida à
 * parte. Uma coordenadora que também esteja vinculada ao paciente aparece uma vez só — a
 * deduplicação é por id, e o vínculo com o paciente prevalece sobre a listagem do tenant.
 *
 * <p><b>Por que desempatar pelo id dentro de cada grau.</b> O directory não promete ordem, e uma
 * escada cuja composição muda entre duas chamadas é impossível de auditar: o degrau 1 de hoje pode
 * ser outra pessoa amanhã, e a pergunta "por que a cuidadora X não foi avisada?" deixa de ter
 * resposta. Ordenar pelo identificador é arbitrário, mas é estável — e estabilidade é o que a
 * auditoria exige.
 *
 * <p>Existe como classe própria porque <b>dois</b> pontos precisam da mesma ordem: o {@code
 * ReportIngressService}, que abre o degrau 0 quando o alarme nasce, e o {@code
 * NotificationOrchestrationService}, que sobe os degraus seguintes. Duas ordenações independentes
 * fariam o escalonamento pular ou repetir pessoas.
 */
final class CaregiverLadder {

    private CaregiverLadder() {}

    /**
     * Um degrau: quem, e por que ele entrou na escada.
     *
     * @param position índice na escada — 0 é a destinatária original
     */
    record Rung(String cuidadorId, String papel, EscalationGrade grade, int position) {}

    /**
     * Monta a escada completa.
     *
     * @param patientCaregivers vínculos ativos do paciente no directory do ACS — cuidadoras, o
     *     apoio, e eventualmente uma coordenadora também vinculada. Pode ser {@code null}
     * @param clinicCoordinators coordenadoras da clínica, resolvidas fora do vínculo por paciente.
     *     Pode ser {@code null} ou vazia — nesse caso o grau 3 simplesmente não existe nesta
     *     escada, e o alarme segue para o grau 4 em vez de parar
     */
    static List<Rung> build(
            List<CaregiverRoutingPort.ResponsibleCaregiver> patientCaregivers,
            List<CaregiverRoutingPort.ResponsibleCaregiver> clinicCoordinators) {

        // LinkedHashMap com o vínculo do paciente inserido primeiro: se a mesma pessoa aparecer
        // nas duas fontes, o papel que vale é o do vínculo com o paciente.
        Map<String, String> papelPorId = new LinkedHashMap<>();
        coletar(patientCaregivers, papelPorId);
        coletar(clinicCoordinators, papelPorId);

        List<Map.Entry<String, String>> ordenados = new ArrayList<>(papelPorId.entrySet());
        ordenados.sort(
                Comparator.<Map.Entry<String, String>, Integer>comparing(
                                e -> grauBruto(e.getValue()).ordinal())
                        .thenComparing(Map.Entry::getKey));

        List<Rung> escada = new ArrayList<>(ordenados.size());
        boolean primeiraCuidadoraJaVista = false;
        for (Map.Entry<String, String> e : ordenados) {
            EscalationGrade grade = grauBruto(e.getValue());
            if (grade == EscalationGrade.CUIDADORA_ADICIONAL && !primeiraCuidadoraJaVista) {
                // A primeira cuidadora da ordem determinística É a destinatária original.
                grade = EscalationGrade.CUIDADORA_ORIGINAL;
                primeiraCuidadoraJaVista = true;
            }
            escada.add(new Rung(e.getKey(), e.getValue(), grade, escada.size()));
        }
        return List.copyOf(escada);
    }

    /** Só os ids, na ordem da escada — o que o sweep precisa para saber quem vem depois de quem. */
    static List<String> idsOf(List<Rung> escada) {
        return escada.stream().map(Rung::cuidadorId).toList();
    }

    private static void coletar(
            List<CaregiverRoutingPort.ResponsibleCaregiver> origem, Map<String, String> destino) {
        if (origem == null) {
            return;
        }
        for (CaregiverRoutingPort.ResponsibleCaregiver c : origem) {
            if (c == null || c.cuidadorId() == null || c.cuidadorId().isBlank()) {
                continue;
            }
            destino.putIfAbsent(c.cuidadorId(), c.papel());
        }
    }

    /**
     * O grau que o papel implica, antes de distinguir a destinatária original das demais
     * cuidadoras. Um papel desconhecido vai para o fim da escada — nunca é descartado.
     */
    private static EscalationGrade grauBruto(String papel) {
        String normalizado = papel == null ? "" : papel.trim().toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "CUIDADORA" -> EscalationGrade.CUIDADORA_ADICIONAL;
            case "COORDENADOR_CLINICA" -> EscalationGrade.COORDENADOR_CLINICA;
            case "PACIENTE_APOIO" -> EscalationGrade.PACIENTE_APOIO;
            default -> EscalationGrade.DESCONHECIDO;
        };
    }
}
