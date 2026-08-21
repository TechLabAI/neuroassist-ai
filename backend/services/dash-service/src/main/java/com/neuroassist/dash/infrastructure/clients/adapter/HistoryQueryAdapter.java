package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.HistoryQueryPort;
import com.neuroassist.dash.domain.model.HistoryEntry;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.infrastructure.clients.HistClient;
import com.neuroassist.dash.infrastructure.clients.dto.HistoryEntryResponse;
import com.neuroassist.dash.infrastructure.clients.dto.HistoryPageResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link HistoryQueryPort} delegando ao {@link HistClient}. Consulta
 * historico paginado de eventos de um paciente via HIST. Mapeia occurredAt (ISO-8601 String) para
 * {@link Instant}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HistoryQueryAdapter implements HistoryQueryPort {

    private final HistClient histClient;

    @Override
    public HistoryPage getHistory(String patientId, int page, int size) {
        log.debug(
                "Consultando HIST: historico do paciente [{}] pagina={} size={}",
                patientId,
                page,
                size);
        HistoryPageResponse response = histClient.getHistory(patientId, page, size);
        List<HistoryEntry> entries = response.entries().stream().map(this::toHistoryEntry).toList();
        return new HistoryPage(entries, response.page(), response.totalPages());
    }

    private HistoryEntry toHistoryEntry(HistoryEntryResponse response) {
        return new HistoryEntry(
                response.eventId(), response.type(), Instant.parse(response.occurredAt()));
    }
}
