package com.neuroassist.gest.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Unit tests for {@link DetectionProfilePublishedListener} (GEST-020). Verifies that:
 *
 * <ol>
 *   <li>Handler is annotated with {@code @TransactionalEventListener(phase=AFTER_COMMIT)}.
 *   <li>Handler does not throw when given a valid event (exception isolation — listener failure
 *       must never propagate to the originating HTTP response).
 * </ol>
 */
class DetectionProfilePublishedListenerTest {

    private DetectionProfilePublishedListener listener;

    @BeforeEach
    void setUp() {
        listener = new DetectionProfilePublishedListener();
    }

    // -------------------------------------------------------------------------
    // Annotation verification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "onDetectionProfilePublished deve ter @TransactionalEventListener(phase=AFTER_COMMIT)")
    void onDetectionProfilePublished_shouldHaveAfterCommitPhase() throws NoSuchMethodException {
        Method method =
                DetectionProfilePublishedListener.class.getMethod(
                        "onDetectionProfilePublished", DetectionProfilePublished.class);
        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    // -------------------------------------------------------------------------
    // Exception isolation — listener must not throw on valid event
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onDetectionProfilePublished nao deve lancar excecao para evento valido")
    void onDetectionProfilePublished_doesNotThrowOnValidEvent() {
        DetectionProfilePublished event =
                new DetectionProfilePublished(UUID.randomUUID(), 3L, Instant.now());

        assertThatCode(() -> listener.onDetectionProfilePublished(event))
                .doesNotThrowAnyException();
    }
}
