package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;

/**
 * Outbound port — catálogo ativo de comandos do paciente e write-through de recalibração no PAC.
 *
 * <p><b>{@code getCalibrationState} foi removido daqui.</b> Ele existia para consumir {@code GET
 * /api/v1/patients/&#123;id&#125;/calibration}, um endpoint que o PAC nunca serviu (404 verificado
 * contra o serviço rodando). Não há no PAC equivalente a um "estado de calibração" do paciente: o
 * que existe é {@code calibracaoVersao} por comando, já presente em {@link CommandCatalogSnapshot}.
 * A porta prefere não ter o método a ter um método que só sabe mentir.
 */
public interface CommandCatalogPort {

    /** Snapshot do catálogo ativo do paciente. */
    CommandCatalogSnapshot getCommandCatalog(String patientId);

    /** Recalibra um comando publicado no PAC e devolve o resultado autoritativo (write-through). */
    CalibrationResult recalibrate(String patientId, String commandId, CalibrationParams params);
}
