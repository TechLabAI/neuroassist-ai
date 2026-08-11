package com.neuroassist.gest.api.dto;

import java.util.List;

/** Response for the recognition report validation. */
public record DetectionContractResponse(boolean valid, List<String> errors) {}
