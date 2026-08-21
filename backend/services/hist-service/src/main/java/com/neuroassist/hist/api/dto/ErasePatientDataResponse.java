package com.neuroassist.hist.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ErasePatientDataResponse(
        UUID subjectId,
        String tenantId,
        Instant erasedAt,
        String message) {}
