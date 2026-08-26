package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.PanelCompositionUseCase;
import com.neuroassist.dash.application.port.out.CommandCatalogPort;
import com.neuroassist.dash.application.port.out.DeviceLivenessPort;
import com.neuroassist.dash.application.port.out.EventStatePort;
import com.neuroassist.dash.application.port.out.HistoryQueryPort;
import com.neuroassist.dash.application.port.out.IdentityDirectoryPort;
import com.neuroassist.dash.application.port.out.PatientDirectoryPort;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;
import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.domain.model.PanelSummary;
import com.neuroassist.dash.domain.model.PatientOverview;
import com.neuroassist.dash.domain.model.PatientSummaryEntry;
import com.neuroassist.dash.shared.error.PatientNotInScopeException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serviço de composição de leitura do painel da cuidadora.
 *
 * <p><b>Política de degradação</b> (ADR-004, matriz P3) — <i>hard</i> propaga 503, <i>soft</i>
 * degrada por seção:
 *
 * <ul>
 *   <li>ACS ({@link IdentityDirectoryPort}) — <b>hard</b> em toda a superfície. Sem ele não há nem
 *       lista de pacientes nem validação de escopo: responder qualquer coisa seria responder sobre
 *       pacientes que talvez não sejam desta cuidadora.
 *   <li>PAC ({@link CommandCatalogPort}) — <b>hard</b> no overview: o catálogo <i>é</i> o conteúdo
 *       daquela tela.
 *   <li>PAC ({@link PatientDirectoryPort}) — <b>soft no resumo</b>. Esta é a única exceção à linha
 *       "PAC é hard", e ela é deliberada: o resumo existe para mostrar quem está pedindo ajuda, e
 *       apagar o painel inteiro porque o serviço de <i>nomes</i> caiu esconderia o alarme por um
 *       dado cosmético. Degrada para {@code name = null} — nunca para o id disfarçado de nome.
 *   <li>EVNT ({@link EventStatePort}) — <b>soft</b>: degrada para {@code hasActiveEvent = null}
 *       ("não sei"), que é diferente de {@code false} ("não há").
 *   <li>HIST ({@link HistoryQueryPort}) — <b>hard</b>.
 *   <li>EDGE ({@link DeviceLivenessPort}) — <b>soft</b>: {@code deviceStatus = "unknown"}.
 * </ul>
 *
 * <p>Toda degradação soft é feita <b>aqui</b>, capturando a exceção que o adapter deixou subir. Os
 * adapters não capturam nada de propósito: um {@code @CircuitBreaker} cuja exceção é engolida
 * dentro do próprio método nunca abre, porque o resilience4j registra a chamada como sucesso.
 *
 * <p>DASH é BFF stateless — tenant vem do JWT, sem TenantContextHolder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanelCompositionService implements PanelCompositionUseCase {

    private static final int MAX_HISTORY_SIZE = 100;
    private static final DeviceStatus DEGRADED_DEVICE =
            new DeviceStatus("unknown", "unknown", true);

    private final IdentityDirectoryPort identityDirectoryPort;
    private final PatientDirectoryPort patientDirectoryPort;
    private final CommandCatalogPort commandCatalogPort;
    private final EventStatePort eventStatePort;
    private final DeviceLivenessPort deviceLivenessPort;
    private final HistoryQueryPort historyQueryPort;

    /**
     * Compõe o resumo do painel.
     *
     * <p><b>Custo em chamadas: 2 + N</b> — uma ao ACS (escopo), uma ao PAC (nomes de todos), uma ao
     * EVNT (alarme de todos), e uma ao EDGE por paciente. As duas primeiras eram tentadoras de
     * escrever por paciente; seriam um N+1 sobre HTTP no caminho mais quente do sistema, que é
     * exatamente o que a cuidadora abre quando o alarme chega. O EDGE segue por paciente porque a
     * API do edge-service ainda é assim — registrado em vez de escondido.
     */
    @Override
    public PanelSummary composeSummary(String caregiverId, String tenantId) {
        log.debug("composeSummary caregiverId={} tenantId={}", caregiverId, tenantId);

        // ACS — hard: se falhar propaga 503
        List<String> patientIds = identityDirectoryPort.getPatientIdsForCaregiver(caregiverId);
        log.debug(
                "composeSummary found {} patients for caregiverId={}",
                patientIds.size(),
                caregiverId);

        Map<String, String> nomes = safeGetPatientNames(patientIds);
        Set<String> comAlarme = safeFindPatientsWithActiveEvent(patientIds);

        List<PatientSummaryEntry> entries =
                patientIds.stream()
                        .map(patientId -> buildSummaryEntry(patientId, nomes, comAlarme))
                        .toList();

        return new PanelSummary(entries);
    }

    /**
     * Compõe o overview detalhado de um paciente.
     *
     * <p>Valida escopo via ACS (hard). PAC é hard. EDGE é soft.
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

        // PAC — hard: nome e catalogo ativo
        String nome = patientDirectoryPort.getPatientName(patientId);
        CommandCatalogSnapshot catalog = commandCatalogPort.getCommandCatalog(patientId);

        // EDGE — soft: liveness do dispositivo
        DeviceStatus device = safeGetDeviceStatus(patientId);

        return new PatientOverview(patientId, nome, catalog, device);
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

    private PatientSummaryEntry buildSummaryEntry(
            String patientId, Map<String, String> nomes, Set<String> comAlarme) {
        // EDGE — soft degrade por paciente
        DeviceStatus device = safeGetDeviceStatus(patientId);
        // `nomes` nulo == PAC não respondeu (soft); ausente do mapa == PAC não conhece o paciente.
        // Nos dois casos o nome vai nulo — mostrar o UUID no lugar de uma pessoa foi o defeito
        // anterior, e um id que se passa por nome é pior do que a ausência dele.
        String nome = nomes == null ? null : nomes.get(patientId);
        // `comAlarme` nulo == EVNT não respondeu (soft). null aqui significa "não sei", nunca
        // "não há": o painel existe para indicar pedido de ajuda em aberto.
        Boolean hasActiveEvent = comAlarme == null ? null : comAlarme.contains(patientId);
        return new PatientSummaryEntry(patientId, nome, hasActiveEvent, device.status());
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

    /**
     * @return {@code null} quando o PAC não respondeu — distinto de um mapa vazio
     */
    private Map<String, String> safeGetPatientNames(List<String> patientIds) {
        try {
            return patientDirectoryPort.getPatientNames(patientIds);
        } catch (Exception ex) {
            log.error(
                    "PAC indisponivel para nomes — o painel abre sem os nomes dos pacientes,"
                            + " mas NAO esconde alarme por isso: {}",
                    ex.getMessage());
            return null;
        }
    }

    /**
     * @return {@code null} quando o EVNT não respondeu — distinto de um conjunto vazio
     */
    private Set<String> safeFindPatientsWithActiveEvent(List<String> patientIds) {
        try {
            return eventStatePort.findPatientsWithActiveEvent(patientIds);
        } catch (Exception ex) {
            log.error(
                    "EVNT indisponivel — hasActiveEvent vai NULO ('nao sei'), jamais false"
                            + " ('nao ha'): {}",
                    ex.getMessage());
            return null;
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
