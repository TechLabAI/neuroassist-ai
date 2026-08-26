package com.neuroassist.edge.application.port.in;

import java.util.UUID;

/**
 * Inbound port — resolves the active detection-profile pointer from GEST and computes the profile
 * drift for a given device. Extracted from HeartbeatService and LivenessService to honour the
 * c4-component spec (ProfilePointerService as collaborator of HeartbeatService;
 * c4-component.puml:24/:58 — "fetches active profile version pointer from GEST; computes drift").
 *
 * <p>Three-state drift semantics (preserved from {@code DetectionProfilePort.ProfilePointer}):
 *
 * <ul>
 *   <li>{@code profileDrift = null} — GEST unreachable: we cannot determine drift.
 *   <li>{@code profileDrift = false} — GEST answered "no profile published yet": device is not out
 *       of date with anything.
 *   <li>{@code profileDrift = true/false} — GEST answered with a version: compare against the
 *       version the device is currently running.
 * </ul>
 */
public interface ProfilePointerUseCase {

    /**
     * Resolves the active profile pointer from GEST and derives the drift state.
     *
     * @param deviceId the device whose profile should be checked
     * @param tenantId the tenant that owns the device
     * @param runningProfileVersion the profile version the device reported it is running (may be
     *     {@code null} if the device has not reported one yet)
     * @return a {@link ProfileDriftResult} containing the active version, drift state, and
     *     degradation flag
     */
    ProfileDriftResult resolveProfileDrift(
            UUID deviceId, String tenantId, Long runningProfileVersion);

    /**
     * Result of a profile drift resolution.
     *
     * @param activeProfileVersion the version published in GEST, or {@code null} when there is no
     *     active profile or GEST is degraded
     * @param profileDrift {@code null} when GEST is unreachable, {@code false} when no profile is
     *     published, or a boolean comparison result
     * @param profilePointerDegraded {@code true} only when GEST could not be reached
     */
    record ProfileDriftResult(
            Long activeProfileVersion, Boolean profileDrift, boolean profilePointerDegraded) {}
}
