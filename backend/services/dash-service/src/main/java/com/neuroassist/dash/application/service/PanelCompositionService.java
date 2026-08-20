package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.PanelCompositionUseCase;
import com.neuroassist.dash.application.port.out.CommandCatalogPort;
import com.neuroassist.dash.application.port.out.DeviceLivenessPort;
import com.neuroassist.dash.application.port.out.HistoryQueryPort;
import com.neuroassist.dash.application.port.out.IdentityDirectoryPort;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;
import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.domain.model.PanelSummary;
import com.neuroassist.dash.domain.model.PatientOverview;
import com.neuroassist.dash.domain.model.PatientSummaryEntry;
import com.neuroassist.dash.shared.error.PatientNotInScopeException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servico de composicao de leitura do painel do cuidador.
 *
 * <p>Politica de degradacao (ADR-004, matriz P3):
 *
 * <ul>
 *   <li>ACS (IdentityDirectoryPort) — hard: falha propaga como 503
 *   <li>PAC (CommandCatalogPort) — hard: falha propaga como 503
 *   <li>HIST (HistoryQueryPort) — hard: falha propaga como 503
 *   <li>EDGE (DeviceLivenessPort) — soft: falha retorna DeviceStatus degraded=true
 * </ul>
 *
 * <p>DASH e BFF stateless — tenant vem do JWT, sem TenantContextHolder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanelCompositionService implements PanelCompositionUseCase {

    private static final int MAX_HISTORY_SIZE = 100;
    private static final DeviceStatus DEGRADED_DEVICE =
            new DeviceStatus("unknown", "unknown", true);

    private final IdentityDirectoryPort identityDirectoryPort;
    private final CommandCatalogPort commandCatalogPort;
    private final DeviceLivenessPort deviceLivenessPort;
    private final HistoryQueryPort historyQueryPort;

    /**
     * Compoe o resumo do painel para o cuidador.
     *
     * <p>ACS e hard: se falhar, propaga excecao (503). EDGE e soft: falha por paciente resulta em
     * entrada com deviceStatus="unknown" e degraded=true.
     */
    @Override
    public PanelSummary composeSummary(String caregiverId, String tenantId) {
        log.debug("composeSummary caregiverId={} tenantId={}", caregiverId, tenantId);

        // ACS — hard dependency: se falhar propaga 503
        List<String> patientIds = identityDirectoryPort.getPatientIdsForCaregiver(caregiverId);
        log.debug(
                "composeSummary found {} patients for caregiverId={}",
                patientIds.size(),
                caregiverId);

        List<PatientSummaryEntry> entries =
                patientIds.stream().map(patientId -> buildSummaryEntry(patientId)).toList();

        return new PanelSummary(entries);
    }

    /**
     * Compoe o overview detalhado de um paciente.
     *
     * <p>Valida escopo via ACS (hard). PAC e hard. EDGE e soft.
     */
    @Override
    public PatientOverview composePatientOverview(
            String caregiverId, String patientId, String tenantId) {
        log.debug(
                "composePatientOverview caregiverId={} patientId={} tenantId={}",
                caregiverId,
                patientId,
                tenantId);

        // ACS — hard: valida escopo
        validateScope(caregiverId, patientId);

        // PAC — hard: catalogo de comandos e calibracao
        CommandCatalogSnapshot catalog = commandCatalogPort.getCommandCatalog(patientId);
        String calibrationState = commandCatalogPort.getCalibrationState(patientId);
        CommandCatalogSnapshot enriched =
                new CommandCatalogSnapshot(catalog.commands(), calibrationState);

        // EDGE — soft: liveness do dispositivo
        DeviceStatus device = safeGetDeviceStatus(patientId);

        return new PatientOverview(patientId, enriched, device);
    }

    /**
     * Consulta o historico paginado de eventos de um paciente.
     *
     * <p>Valida escopo via ACS (hard). HIST e hard. Size capado a 100.
     */
    @Override
    public HistoryPage queryHistory(
            String caregiverId, String patientId, int page, int size, String tenantId) {
        log.debug(
                "queryHistory caregiverId={} patientId={} page={} size={} tenantId={}",
                caregiverId,
                patientId,
                page,
                size,
                tenantId);

        // ACS — hard: valida escopo
        validateScope(caregiverId, patientId);

        // cap size
        int cappedSize = Math.min(size, MAX_HISTORY_SIZE);
        if (size > MAX_HISTORY_SIZE) {
            log.debug("queryHistory size capped from {} to {}", size, MAX_HISTORY_SIZE);
        }

        // HIST — hard: historico de eventos
        return historyQueryPort.getHistory(patientId, page, cappedSize);
    }

    /**
     * Consulta o status de liveness do dispositivo edge de um paciente.
     *
     * <p>Valida escopo via ACS (hard). EDGE e soft.
     */
    @Override
    public DeviceStatus queryDeviceStatus(String caregiverId, String patientId, String tenantId) {
        log.debug(
                "queryDeviceStatus caregiverId={} patientId={} tenantId={}",
                caregiverId,
                patientId,
                tenantId);

        // ACS — hard: valida escopo
        validateScope(caregiverId, patientId);

        // EDGE — soft
        return safeGetDeviceStatus(patientId);
    }

    // ---- helpers ----

    private PatientSummaryEntry buildSummaryEntry(String patientId) {
        // EDGE — soft degrade por paciente
        DeviceStatus device = safeGetDeviceStatus(patientId);
        // name e hasActiveEvent serao resolvidos por adapters reais em P5;
        // por ora usamos patientId como placeholder e hasActiveEvent=false
        return new PatientSummaryEntry(patientId, patientId, false, device.status());
    }

    private void validateScope(String caregiverId, String patientId) {
        List<String> patientIds = identityDirectoryPort.getPatientIdsForCaregiver(caregiverId);
        if (!patientIds.contains(patientId)) {
            log.warn(
                    "Scope violation: caregiverId={} attempted access to patientId={}",
                    caregiverId,
                    patientId);
            throw new PatientNotInScopeException(patientId);
        }
    }

    private DeviceStatus safeGetDeviceStatus(String patientId) {
        try {
            return deviceLivenessPort.getDeviceStatus(patientId);
        } catch (Exception ex) {
            log.warn("EDGE soft-degrade for patientId={}: {}", patientId, ex.getMessage());
            return DEGRADED_DEVICE;
        }
    }
}
