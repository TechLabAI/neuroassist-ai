package com.neuroassist.dash.application.port.in;

import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.domain.model.PanelSummary;
import com.neuroassist.dash.domain.model.PatientOverview;

/**
 * Inbound port — composicao de leitura do painel do cuidador.
 *
 * <p>Cada metodo compoe dados de multiplos upstreams aplicando a politica de degradacao
 * definida na matrix DASH (P3): ACS/PAC/HIST sao hard-503; EDGE e soft-degrade.
 */
public interface PanelCompositionUseCase {

    /**
     * Compoe o resumo do painel para o cuidador: lista todos os pacientes vinculados (ACS),
     * enriquece com liveness do dispositivo (EDGE soft-degrade).
     */
    PanelSummary composeSummary(String caregiverId, String tenantId);

    /**
     * Compoe o overview detalhado de um paciente: catalogo de comandos (PAC hard) +
     * liveness do dispositivo (EDGE soft-degrade). Valida escopo antes de compor.
     */
    PatientOverview composePatientOverview(String caregiverId, String patientId, String tenantId);

    /**
     * Consulta o historico paginado de eventos de um paciente (HIST hard).
     * Valida escopo antes de consultar. Size capado a 100.
     */
    HistoryPage queryHistory(String caregiverId, String patientId, int page, int size, String tenantId);

    /**
     * Consulta o status de liveness do dispositivo edge (EDGE soft-degrade).
     * Valida escopo antes de consultar.
     */
    DeviceStatus queryDeviceStatus(String caregiverId, String patientId, String tenantId);
}
