package com.neuroassist.evnt.application.port.in;

/**
 * Inbound port — reprocesses notification dispatches that were never delivered (defect C-45).
 *
 * <p>Both methods used to throw {@code UnsupportedOperationException}. That mattered more than it
 * looked: {@code ReportIngressService} leaves a dispatch {@code QUEUED} when dash-service is
 * unreachable and {@code NO_SESSION} when the caregiver had no panel open, and the comments called
 * both "retryable" — but nothing ever retried them. A caregiver who was away when the alarm fired
 * simply never received it, including for an emergency.
 */
public interface NotificationOrchestrationUseCase {

    /**
     * Re-attempts one batch of undelivered dispatches for the tenant bound to the current thread.
     *
     * <p>Takes no arguments because the scheduler binds the tenant before the transaction opens.
     */
    SweepResult retrySweep();

    record SweepResult(int attempted, int delivered, int stillPending, int givenUp) {}
}
