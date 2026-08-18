package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/edge/heartbeat. No deviceId field — extracted from the
 * authenticated device token (anti-spoofing invariant #2). No tenantId field — derived from the
 * device JWT. No biometric data — privacy invariant (doc 02 / Framing #6).
 */
public record HeartbeatRequest(@NotBlank String agentVersion, Long runningProfileVersion) {}
