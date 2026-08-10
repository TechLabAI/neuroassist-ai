package com.neuroassist.gest.api.dto;

import java.util.List;

public record DetectionContractRequest(
        int profileVersion,
        List<PrimitiveRef> primitives) {

    public record PrimitiveRef(String tipoGestoId) {}
}
