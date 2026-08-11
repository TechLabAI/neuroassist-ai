package com.neuroassist.gest.api.dto;

import java.util.Map;

/**
 * Request payload for POST /recognized-commands — validate a RecognizedCommandReport against the
 * frozen detection contract. Stateless; nothing is persisted.
 */
public record DetectionContractRequest(Map<String, Object> report) {}
