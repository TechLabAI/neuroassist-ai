package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.DirectoryOutboxDrainUseCase;
import com.neuroassist.pac.application.port.out.AcsLinkPort;
import com.neuroassist.pac.application.port.out.DirectoryOutboxPort;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers caregiver-patient links to the ACS directory (E2E-015 / defect C-30).
 *
 * <p>Modelled on {@link HistOutboxDrainService}, because the problem is the same one twice: a
 * durable row nobody reads back is worse than no row at all — its presence makes the queue look
 * healthy while the thing it represents never happens.
 *
 * <p><b>Bounded batch, and it stops on the first transient failure.</b> Both properties matter here
 * for a reason that does not apply to the audit outbox: these entries are <i>ordered</i>. A LINK
 * followed by an UNLINK of the same pair, delivered out of order, leaves the directory holding a
 * link the PAC already removed — a caregiver still receiving alarms for a patient no longer in
 * their care. Stopping the batch keeps delivery strictly oldest-first.
 *
 * <p>A terminal refusal is different: it will never be applied, so it cannot block what comes after
 * it. It is marked {@code failed} with its reason and the loop moves on.
 */
@Slf4j
@Service
public class DirectoryOutboxDrainService implements DirectoryOutboxDrainUseCase {

    private final DirectoryOutboxPort outboxPort;
    private final AcsLinkPort acsLinkPort;
    private final int batchSize;
    private final int maxRetries;

    public DirectoryOutboxDrainService(
            DirectoryOutboxPort outboxPort,
            AcsLinkPort acsLinkPort,
            @Value("${neuroassist.directory-outbox.batch-size:50}") int batchSize,
            @Value("${neuroassist.directory-outbox.max-retries:10}") int maxRetries) {
        this.outboxPort = outboxPort;
        this.acsLinkPort = acsLinkPort;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Override
    @Transactional
    public DrainResult drainCurrentTenant() {
        String tenantId = TenantContextHolder.require(); // RULE-07

        List<DirectoryOutboxPort.PendingEntry> pending = outboxPort.findPending(batchSize);
        if (pending.isEmpty()) {
            return new DrainResult(0, 0, 0);
        }

        int delivered = 0;
        int givenUp = 0;
        for (DirectoryOutboxPort.PendingEntry entry : pending) {
            if (entry.retryCount() >= maxRetries) {
                outboxPort.markFailed(entry.id(), "max retries exceeded");
                givenUp++;
                log.error(
                        "Directory {} given up after {} attempts — paciente={} cuidador={}"
                                + " tenant={}. The ACS directory does NOT reflect this link; an"
                                + " alarm for this patient may have no destination.",
                        entry.operation(),
                        entry.retryCount(),
                        entry.pacienteId(),
                        entry.cuidadorId(),
                        tenantId);
                continue;
            }

            AcsLinkPort.Outcome outcome =
                    "UNLINK".equals(entry.operation())
                            ? acsLinkPort.unlink(entry.pacienteId(), entry.cuidadorId())
                            : acsLinkPort.link(entry.pacienteId(), entry.cuidadorId());

            switch (outcome) {
                case ACCEPTED -> {
                    outboxPort.markSent(entry.id());
                    delivered++;
                }
                case REFUSED -> {
                    // Terminal by definition — it will be refused identically next tick. It cannot
                    // block the entries behind it, because it will never be applied at all.
                    outboxPort.markFailed(entry.id(), "refused by acs-service");
                    givenUp++;
                    log.error(
                            "acs-service refused directory {} — paciente={} cuidador={} tenant={}."
                                    + " Not retried. The two sides of this link now disagree.",
                            entry.operation(),
                            entry.pacienteId(),
                            entry.cuidadorId(),
                            tenantId);
                }
                case RETRY -> {
                    outboxPort.markRetried(entry.id(), "acs-service unavailable");
                    log.warn(
                            "Directory {} deferred — paciente={} cuidador={} tenant={} attempt={}",
                            entry.operation(),
                            entry.pacienteId(),
                            entry.cuidadorId(),
                            tenantId,
                            entry.retryCount() + 1);
                    // Stop the batch: the remaining entries would fail identically against a known
                    // outage, and delivering past this one would break the ordering the directory
                    // depends on.
                    return new DrainResult(pending.size(), delivered, givenUp);
                }
            }
        }

        log.info(
                "Directory outbox drain tenant={} attempted={} delivered={} givenUp={}",
                tenantId,
                pending.size(),
                delivered,
                givenUp);
        return new DrainResult(pending.size(), delivered, givenUp);
    }
}
