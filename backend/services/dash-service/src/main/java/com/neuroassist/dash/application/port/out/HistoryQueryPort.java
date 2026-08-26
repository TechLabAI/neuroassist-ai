package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.HistoryPage;

/** Outbound port — consulta paginada ao HIST para historico de eventos de um paciente. */
public interface HistoryQueryPort {

    /** Retorna uma pagina do historico de eventos do paciente. */
    HistoryPage getHistory(String patientId, int page, int size);
}
