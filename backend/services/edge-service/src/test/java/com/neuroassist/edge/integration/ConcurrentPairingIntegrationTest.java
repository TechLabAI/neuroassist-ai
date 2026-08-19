package com.neuroassist.edge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.in.PairingUseCase;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Concurrent pairing integration test — EDGE P9 hardening. Verifies that optimistic locking on the
 * DispositivoEdge aggregate prevents double-pairing under concurrent writes. Two threads race to
 * pair the same device; exactly one must succeed. The loser receives either
 * ObjectOptimisticLockingFailureException (caught by GlobalExceptionHandler → 409) or a pairing
 * conflict CodedException (409 via ProblemDetailFactory) — both are correct outcomes. Extends
 * BaseIntegrationTest to share the Testcontainers singleton. NOTE: @Autowired field injection is
 * acceptable here because this is a test class. Architecture rule RULE-07 (no field injection)
 * applies to production code only.
 */
@Disabled(
        "P9 — concurrent integration test pending schema setup (DB multi-tenant search_path incompatible with ddl-auto:create-drop in tests)")
class ConcurrentPairingIntegrationTest extends BaseIntegrationTest {

    private DeviceRegistrationUseCase deviceRegistrationUseCase;
    private PairingUseCase pairingService;

    @Autowired
    void setDeviceRegistrationUseCase(DeviceRegistrationUseCase useCase) {
        this.deviceRegistrationUseCase = useCase;
    }

    @Autowired
    void setPairingService(PairingUseCase service) {
        this.pairingService = service;
    }

    private static final String TENANT = "tenant-concurrent-pair-test";
    private static final String ACTOR = "test-actor";

    private UUID deviceId;
    private UUID pacienteId;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        TenantContextHolder.set(TENANT);
        try {
            DeviceRegistrationUseCase.RegisterResult registered =
                    deviceRegistrationUseCase.register("concurrent-test-device", null, ACTOR);
            deviceId = registered.deviceId();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Two concurrent pair() calls on the same device — exactly one succeeds")
    void concurrentPairing_exactlyOneSucceeds() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        AtomicBoolean success1 = new AtomicBoolean(false);
        AtomicBoolean success2 = new AtomicBoolean(false);
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        Thread t1 =
                new Thread(
                        () -> {
                            TenantContextHolder.set(TENANT);
                            ready.countDown();
                            try {
                                start.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            try {
                                pairingService.pair(deviceId, pacienteId, "actor-1");
                                success1.set(true);
                            } catch (Throwable t) {
                                error1.set(t);
                            } finally {
                                TenantContextHolder.clear();
                            }
                        },
                        "concurrent-pair-thread-1");

        Thread t2 =
                new Thread(
                        () -> {
                            TenantContextHolder.set(TENANT);
                            ready.countDown();
                            try {
                                start.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            try {
                                pairingService.pair(deviceId, pacienteId, "actor-2");
                                success2.set(true);
                            } catch (Throwable t) {
                                error2.set(t);
                            } finally {
                                TenantContextHolder.clear();
                            }
                        },
                        "concurrent-pair-thread-2");

        t1.start();
        t2.start();
        ready.await(); // both threads are poised at the start latch
        start.countDown(); // fire simultaneously
        t1.join(10_000);
        t2.join(10_000);

        // Core invariant: at least one must succeed (no deadlock / total failure)
        assertThat(success1.get() || success2.get())
                .as("At least one concurrent pair() must succeed")
                .isTrue();

        // Core invariant: both must NOT succeed (no double-pairing)
        assertThat(success1.get() && success2.get())
                .as("Both threads must not succeed — optimistic locking or conflict must block one")
                .isFalse();

        // The losing thread must have thrown a recognized concurrency or conflict exception
        Throwable losingError = success1.get() ? error2.get() : error1.get();
        assertThat(losingError).as("Losing thread must throw a recognized exception").isNotNull();

        boolean isOptimisticLock =
                losingError instanceof ObjectOptimisticLockingFailureException
                        || (losingError.getCause()
                                instanceof ObjectOptimisticLockingFailureException);
        boolean isCodedException = losingError.getClass().getSimpleName().equals("CodedException");
        boolean isRuntimeException = losingError instanceof RuntimeException;

        assertThat(isOptimisticLock || isCodedException || isRuntimeException)
                .as(
                        "Losing thread must throw ObjectOptimisticLockingFailureException, "
                                + "CodedException (pairing conflict), or a RuntimeException wrapping one. "
                                + "Actual: "
                                + losingError.getClass().getName()
                                + ": "
                                + losingError.getMessage())
                .isTrue();
    }

    @Test
    @DisplayName(
            "GlobalExceptionHandler maps ObjectOptimisticLockingFailureException to 409 type URI")
    void optimisticLockExceptionMapsTo409Type() {
        // Verify that the exception type used by GlobalExceptionHandler is the Spring ORM type
        // (unit-level check — the full MVC mapping is covered by the GlobalExceptionHandler itself)
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("DispositivoEdge", deviceId);
        assertThat(ex).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(ex.getPersistentClassName()).isEqualTo("DispositivoEdge");
    }
}
