package com.neuroassist.gest.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.neuroassist.gest.domain.event.PrimitiveDeprecated;
import com.neuroassist.gest.domain.event.PrimitiveRegistered;
import com.neuroassist.gest.domain.event.PrimitiveTransitioned;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Unit tests for {@link PrimitiveEventListener} (GEST-019). Verifies that:
 *
 * <ol>
 *   <li>Each handler is annotated with {@code @TransactionalEventListener(phase=AFTER_COMMIT)}.
 *   <li>Handlers do not throw when given a valid event (exception isolation — listener failure must
 *       never propagate to the originating HTTP response).
 * </ol>
 */
class PrimitiveEventListenerTest {

    private PrimitiveEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PrimitiveEventListener();
    }

    // -------------------------------------------------------------------------
    // Annotation verification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onPrimitiveRegistered deve ter @TransactionalEventListener(phase=AFTER_COMMIT)")
    void onPrimitiveRegistered_shouldHaveAfterCommitPhase() throws NoSuchMethodException {
        Method method =
                PrimitiveEventListener.class.getMethod(
                        "onPrimitiveRegistered", PrimitiveRegistered.class);
        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("onPrimitiveTransitioned deve ter @TransactionalEventListener(phase=AFTER_COMMIT)")
    void onPrimitiveTransitioned_shouldHaveAfterCommitPhase() throws NoSuchMethodException {
        Method method =
                PrimitiveEventListener.class.getMethod(
                        "onPrimitiveTransitioned", PrimitiveTransitioned.class);
        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("onPrimitiveDeprecated deve ter @TransactionalEventListener(phase=AFTER_COMMIT)")
    void onPrimitiveDeprecated_shouldHaveAfterCommitPhase() throws NoSuchMethodException {
        Method method =
                PrimitiveEventListener.class.getMethod(
                        "onPrimitiveDeprecated", PrimitiveDeprecated.class);
        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    // -------------------------------------------------------------------------
    // Exception isolation — listener must not throw on valid event
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onPrimitiveRegistered nao deve lancar excecao para evento valido")
    void onPrimitiveRegistered_doesNotThrowOnValidEvent() {
        PrimitiveRegistered event =
                new PrimitiveRegistered(
                        UUID.randomUUID(), "Piscar-Olho", "tenant-1", "actor-test", Instant.now());

        assertThatCode(() -> listener.onPrimitiveRegistered(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onPrimitiveTransitioned nao deve lancar excecao para evento valido")
    void onPrimitiveTransitioned_doesNotThrowOnValidEvent() {
        PrimitiveTransitioned event =
                new PrimitiveTransitioned(
                        UUID.randomUUID(),
                        "REGISTERED",
                        "PENDING_TRAINING",
                        "actor-test",
                        Instant.now());

        assertThatCode(() -> listener.onPrimitiveTransitioned(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onPrimitiveDeprecated nao deve lancar excecao para evento valido")
    void onPrimitiveDeprecated_doesNotThrowOnValidEvent() {
        PrimitiveDeprecated event =
                new PrimitiveDeprecated(
                        UUID.randomUUID(), "Piscar-Olho", "actor-test", Instant.now());

        assertThatCode(() -> listener.onPrimitiveDeprecated(event)).doesNotThrowAnyException();
    }
}
