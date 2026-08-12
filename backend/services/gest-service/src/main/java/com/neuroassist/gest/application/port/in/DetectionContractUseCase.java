package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;

/** Inbound port — frozen detection contract (stateless). */
public interface DetectionContractUseCase {

    /** Returns the machine-readable descriptor of the current detection contract. */
    Object getContractDescriptor();

    /**
     * Validates a RecognizedCommandReport against the frozen contract. Stateless — nothing is
     * persisted.
     */
    DetectionContractResponse validateReport(DetectionContractRequest request);
}
