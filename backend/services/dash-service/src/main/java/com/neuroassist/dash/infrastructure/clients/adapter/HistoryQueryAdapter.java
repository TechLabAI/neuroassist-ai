package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.HistoryQueryPort;
import com.neuroassist.dash.domain.model.HistoryEntry;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.infrastructure.clients.HistClient;
import com.neuroassist.dash.infrastructure.clients.dto.HistoryEntryResponse;
import com.neuroassist.dash.infrastructure.clients.dto.HistoryPageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
    @CircuitBreaker(name = "hist-service")
    public HistoryPage getHistory(String patientId, int page, int size) {
        log.debug(
                "Consultando HIST: historico do paciente [{}] pagina={} size={}",
                patientId,
                page,
                size);
        HistoryPageResponse response = histClient.getHistory("paciente", patientId, page, size);
        List<HistoryEntry> entries = response.items().stream().map(this::toHistoryEntry).toList();
        return new HistoryPage(entries, response.page(), totalPages(response));
    }

    /**
     * O hist-service devolve o total de <b>registros</b>; o painel raciocina em páginas. A
     * conversão é feita aqui, e não no produtor, porque paginação é uma decisão de apresentação.
     */
    private int totalPages(HistoryPageResponse response) {
        int size = response.size() > 0 ? response.size() : 1;
        return (int) ((response.total() + size - 1) / size);
    }

    private HistoryEntry toHistoryEntry(HistoryEntryResponse response) {
        return new HistoryEntry(
                response.id(), response.factType(), Instant.parse(response.occurredAt()));
    }
}
