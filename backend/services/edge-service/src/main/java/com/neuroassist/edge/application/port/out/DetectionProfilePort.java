package com.neuroassist.edge.application.port.out;

import java.util.UUID;

/**
 * Outbound port — the active detection-profile pointer, authored by GEST and transported by EDGE
 * (ADR-006).
 *
 * <p><b>Three outcomes, not two.</b> This port used to return {@code Optional<Long>}, which forced
 * two very different answers into the same empty value:
 *
 * <ul>
 *   <li>GEST answered, and there is no published profile yet — a legitimate state of the system;
 *   <li>GEST could not be reached — we simply do not know.
 * </ul>
 *
 * <p>The heartbeat reported both as {@code profilePointerDegraded = true}, so an ordinary tenant
 * that had not published a profile yet looked exactly like a GEST outage. That is the same shape as
 * defect C-19, where an authorization error was laundered into "service degraded", and it is why
 * HB-04 could not pass.
 */
public interface DetectionProfilePort {

    ProfilePointer currentProfileVersion(UUID deviceId, String tenantId);

    /**
     * @param version the active profile version, or {@code null} when there is none or it is
     *     unknown
     * @param degraded {@code true} only when GEST could not answer — never when it answered "no
     *     active profile"
     */
    record ProfilePointer(Long version, boolean degraded) {

        /** GEST answered with the active version. */
        public static ProfilePointer of(long version) {
            return new ProfilePointer(version, false);
        }

        /** GEST answered: this tenant has no published profile yet (404). Not a failure. */
        public static ProfilePointer noActiveProfile() {
            return new ProfilePointer(null, false);
        }

        /** GEST could not be reached, or refused. The pointer is unknown. */
        public static ProfilePointer unknown() {
            return new ProfilePointer(null, true);
        }

        public boolean hasVersion() {
            return version != null;
        }
    }
}
