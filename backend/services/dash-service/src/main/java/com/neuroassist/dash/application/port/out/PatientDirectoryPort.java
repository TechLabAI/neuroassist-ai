package com.neuroassist.dash.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Outbound port — nome do paciente, que vive no PAC ({@code PacienteResponse.nome}).
 *
 * <p>Separado de {@link CommandCatalogPort} de propósito: catálogo de comandos e identificação do
 * paciente são perguntas diferentes, atendidas por controllers diferentes do PAC, e o painel usa
 * cada uma num momento diferente.
 */
public interface PatientDirectoryPort {

    /**
     * Nomes dos pacientes pedidos, <b>em uma única ida ao PAC</b>.
     *
     * <p>O painel lista N pacientes; um {@code getPatientName(id)} por linha seria um N+1 sobre
     * HTTP no caminho mais quente do sistema. O PAC expõe a lista do tenant inteiro, e o filtro
     * pelo escopo da cuidadora acontece aqui.
     *
     * @return apenas os ids que o PAC conhece — um id ausente do mapa significa "o PAC não tem esse
     *     paciente", nunca "falhei ao perguntar": falha sobe como exceção
     */
    Map<String, String> getPatientNames(Collection<String> patientIds);

    /** Nome de um paciente só — usado pelo overview, que já é uma tela de um paciente. */
    String getPatientName(String patientId);
}
