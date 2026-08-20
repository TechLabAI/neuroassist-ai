package com.neuroassist.acs.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTO — JWK Set (GET /.well-known/jwks.json). RFC 7517 compliant JWK Set format. SECURITY:
 * Only public key material is returned — no private key fields (d, p, q, dp, dq, qi).
 */
public record JwkSetResponse(List<Map<String, Object>> keys) {}
