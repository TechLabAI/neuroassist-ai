package com.neuroassist.edge.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DispositivoEdgeDomainService}. Pure POJO — no Spring context required. */
class DispositivoEdgeDomainServiceTest {

    private DispositivoEdgeDomainService sut;
    private static final long STALENESS_THRESHOLD = 90L; // 3 × 30s

    @BeforeEach
    void setUp() {
        sut = new DispositivoEdgeDomainService();
    }

    // ---- Helpers ----

    private DispositivoEdge device(PairingState state, Instant lastSync) {
        return new DispositivoEdge(
                UUID.randomUUID(),
                "t1",
                "label",
                null,
                state,
                "1.0",
                lastSync,
                null,
                0L,
                Instant.now(),
                Instant.now(),
                "op",
                "op");
    }

    // ---- deriveLivenessStatus ----

    @Nested
    @DisplayName("deriveLivenessStatus")
    class DeriveStatus {

        @Test
        @DisplayName("returns 'n/a' for unpaired device")
        void unpairedIsNa() {
            DispositivoEdge d = device(PairingState.unpaired, Instant.now());
            assertThat(sut.deriveLivenessStatus(d, Instant.now(), STALENESS_THRESHOLD))
                    .isEqualTo("n/a");
        }

        @Test
        @DisplayName("returns 'n/a' for retired device")
        void retiredIsNa() {
            DispositivoEdge d = device(PairingState.retired, Instant.now());
            assertThat(sut.deriveLivenessStatus(d, Instant.now(), STALENESS_THRESHOLD))
                    .isEqualTo("n/a");
        }

        @Test
        @DisplayName("returns 'offline' when no heartbeat ever received")
        void noHeartbeatIsOffline() {
            DispositivoEdge d = device(PairingState.paired, null);
            assertThat(sut.deriveLivenessStatus(d, Instant.now(), STALENESS_THRESHOLD))
                    .isEqualTo("offline");
        }

        @Test
        @DisplayName("returns 'online' within the staleness window")
        void freshHeartbeatIsOnline() {
            Instant lastSync = Instant.now().minusSeconds(30);
            DispositivoEdge d = device(PairingState.paired, lastSync);
            assertThat(sut.deriveLivenessStatus(d, Instant.now(), STALENESS_THRESHOLD))
                    .isEqualTo("online");
        }

        @Test
        @DisplayName("returns 'offline' just beyond the staleness threshold")
        void staleHeartbeatIsOffline() {
            Instant lastSync = Instant.now().minusSeconds(STALENESS_THRESHOLD + 1);
            DispositivoEdge d = device(PairingState.paired, lastSync);
            assertThat(sut.deriveLivenessStatus(d, Instant.now(), STALENESS_THRESHOLD))
                    .isEqualTo("offline");
        }

        @Test
        @DisplayName("returns 'online' exactly at the threshold boundary")
        void exactlyAtThresholdIsOnline() {
            Instant serverNow = Instant.now();
            Instant lastSync = serverNow.minusSeconds(STALENESS_THRESHOLD);
            DispositivoEdge d = device(PairingState.paired, lastSync);
            assertThat(sut.deriveLivenessStatus(d, serverNow, STALENESS_THRESHOLD))
                    .isEqualTo("online");
        }
    }

    // ---- canPair / canUnpair ----

    @Nested
    @DisplayName("pairing guards")
    class PairingGuards {

        @Test
        void registeredCanPair() {
            assertThat(sut.canPair(device(PairingState.registered, null))).isTrue();
        }

        @Test
        void unpairedCanPair() {
            assertThat(sut.canPair(device(PairingState.unpaired, null))).isTrue();
        }

        @Test
        void pairedCannotPair() {
            assertThat(sut.canPair(device(PairingState.paired, null))).isFalse();
        }

        @Test
        void retiredCannotPair() {
            assertThat(sut.canPair(device(PairingState.retired, null))).isFalse();
        }

        @Test
        void pairedCanUnpair() {
            assertThat(sut.canUnpair(device(PairingState.paired, null))).isTrue();
        }

        @Test
        void registeredCannotUnpair() {
            assertThat(sut.canUnpair(device(PairingState.registered, null))).isFalse();
        }

        @Test
        void retiredIsRetired() {
            assertThat(sut.isRetired(device(PairingState.retired, null))).isTrue();
        }

        @Test
        void pairedIsNotRetired() {
            assertThat(sut.isRetired(device(PairingState.paired, null))).isFalse();
        }
    }

    // ---- isOnlineAt ----

    @Nested
    @DisplayName("isOnlineAt")
    class IsOnlineAt {

        @Test
        void onlineWithinWindow() {
            Instant now = Instant.now();
            DispositivoEdge d = device(PairingState.paired, now.minusSeconds(10));
            assertThat(sut.isOnlineAt(d, now, STALENESS_THRESHOLD)).isTrue();
        }

        @Test
        void offlineBeyondWindow() {
            Instant now = Instant.now();
            DispositivoEdge d = device(PairingState.paired, now.minusSeconds(200));
            assertThat(sut.isOnlineAt(d, now, STALENESS_THRESHOLD)).isFalse();
        }
    }
}
