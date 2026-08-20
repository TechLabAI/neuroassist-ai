package com.neuroassist.acs.api.dto;

/**
 * Request DTO — JWK Set (GET /.well-known/jwks.json). Public endpoint — no authentication required.
 * Optional kid filter: if provided, returns only the key with the matching kid.
 */
public record JwkSetRequest(String kid) {}
