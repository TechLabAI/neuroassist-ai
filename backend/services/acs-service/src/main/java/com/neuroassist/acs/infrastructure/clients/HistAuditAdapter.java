package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.HistAuditPort.AuditFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing HistAuditPort via HistAuditClient (Feign).
 *
 * <p>Fire-and-forget: failures are logged but never propagated to the caller. Full Feign method
 * implementations deferred to ACS-017 (P3) when the HIST contract is stable.
 *
 * <p>RULE-06: @FeignClient only in infrastructure.clients (HistAuditClient).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistAuditAdapter implements HistAuditPort {

    private final HistAuditClient histAuditClient;

    /**
     * Sends an audit fact to hist-service (fire-and-forget). Failures are swallowed — audit must
     * never block business operations.
     */
    @Override
    public void recordFact(AuditFact fact) {
        try {
            // TODO (ACS-017/P3): call histAuditClient.recordFact(fact) when Feign contract is
            // stable
            log.debug(
                    "Audit fact recorded (stub): fact={} empresaId={} actorId={}",
                    fact.fact(),
                    fact.empresaId(),
                    fact.actorId());
        } catch (Exception e) {
            // Best-effort — never propagate audit failures
            log.warn(
                    "Failed to send audit fact to hist-service (suppressed): fact={} error={}",
                    fact.fact(),
                    e.getMessage());
        }
    }
}
