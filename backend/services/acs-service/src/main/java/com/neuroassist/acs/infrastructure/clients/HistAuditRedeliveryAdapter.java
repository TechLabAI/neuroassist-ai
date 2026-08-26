package com.neuroassist.acs.infrastructure.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.acs.application.port.out.HistAuditRedeliveryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reentrega um payload guardado no outbox ao hist-service (E2E-031).
 *
 * <p>Desserializa de volta na exata requisição que foi tentada originalmente — mesmo {@code factId}
 * acima de tudo, que é o que faz o hist-service tratar a reentrega como replay (200) em vez de um
 * segundo fato (201).
 *
 * <p>Nunca lança: o laço do dreno decide o que uma falha significa, e uma exceção escapando daqui
 * abortaria um lote no meio, com algumas entradas já marcadas como entregues.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistAuditRedeliveryAdapter implements HistAuditRedeliveryPort {

    private final HistAuditClient histAuditClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean redeliver(UUID tenantId, String payloadJson) {
        HistAuditClient.RecordAuditFactRequest request;
        try {
            request =
                    objectMapper.readValue(
                            payloadJson, HistAuditClient.RecordAuditFactRequest.class);
        } catch (Exception e) {
            // Payload ilegível nunca vai virar legível. Reportar sucesso o tira da fila de
            // propósito: retentar para sempre esconderia uma linha corrompida atrás de um
            // contador que só cresce.
            log.error(
                    "Payload do outbox nao e um RecordAuditFactRequest valido, descartando: {}",
                    e.getMessage());
            return true;
        }
        try {
            // O tenant da LINHA escopa o token — não o de nenhuma requisição em curso, porque o
            // dreno roda sem requisição alguma.
            HistAuditRequestInterceptor.forTenant(
                    tenantId, () -> histAuditClient.recordFact(request));
            return true;
        } catch (Exception e) {
            log.warn("Reentrega ao hist-service falhou: {}", e.getMessage());
            return false;
        }
    }
}
