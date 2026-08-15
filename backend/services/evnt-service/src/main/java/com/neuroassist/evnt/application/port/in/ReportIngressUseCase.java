package com.neuroassist.evnt.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportIngressUseCase {

    ReportIngressResponse ingest(ReportIngressRequest request);

    record ReportIngressRequest(
            String reportId,
            String pacienteId,
            String commandRef,
            List<PrimitiveEntry> primitives,
            double confidence,
            int profileVersion,
            Instant recognizedAt,
            String deviceId,
            /** Non-null presence indicates biometric payload — rejected with 400 (EVNT-023). */
            String rawFramePayload,
            /** Tenant the patient belongs to — used to detect device token tenant mismatch. */
            String patientTenant,
            /**
             * Tenant the device token is scoped to — must match patientTenant or request is
             * rejected with 403.
             */
            String deviceTenant) {

        public record PrimitiveEntry(String tipoGestoId, double confidence) {}
    }

    record ReportIngressResponse(
            UUID eventoId, String status, NotificationInfo notification, Instant createdAt) {

        public record NotificationInfo(String tier, String caregiverId) {}
    }
}
