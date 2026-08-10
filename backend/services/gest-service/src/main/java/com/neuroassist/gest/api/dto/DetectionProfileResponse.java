package com.neuroassist.gest.api.dto;

import java.util.List;
import java.util.UUID;

public record DetectionProfileResponse(
        UUID id,
        int version,
        String contractVersion,
        List<PrimitiveInfo> primitives) {

    public record PrimitiveInfo(
            UUID id,
            String tipoGestoId,
            String nome,
            String engineFeatureRef) {}
}
