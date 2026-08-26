package com.neuroassist.acs.infrastructure.scheduling;

import com.neuroassist.acs.application.port.in.HistAuditOutboxDrainUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reentrega fatos de autenticação encalhados (E2E-031).
 *
 * <p><b>Mais simples que os equivalentes do PAC e do EVNT, e a diferença é o ponto.</b> Lá o
 * sweeper precisa vincular o tenant antes de abrir a transação — a tabela vive no schema do tenant
 * e, sem o vínculo, a consulta cai no schema global, não acha a tabela e reporta sucesso. Aqui a
 * tabela é global e cada linha carrega o seu tenant, então não há nada a vincular.
 *
 * <p>O tenant volta a importar na hora da entrega, para escopar o token — ver {@code
 * HistAuditRedeliveryAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "acs.hist-outbox.drain.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HistAuditOutboxScheduler {

    private final HistAuditOutboxDrainUseCase drainUseCase;

    @Scheduled(
            initialDelayString = "${acs.hist-outbox.drain.initial-delay-ms:30000}",
            fixedDelayString = "${acs.hist-outbox.drain.interval-ms:60000}")
    public void drain() {
        try {
            drainUseCase.drain();
        } catch (Exception e) {
            log.warn("Audit outbox drain falhou: {}", e.getMessage());
        }
    }
}
