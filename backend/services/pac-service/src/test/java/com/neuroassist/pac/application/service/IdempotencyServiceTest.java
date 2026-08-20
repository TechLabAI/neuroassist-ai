package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.application.port.out.IdempotencyKeyRepositoryPort;
import com.neuroassist.pac.domain.model.IdempotencyKeyRecord;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PAC-023 unit tests — IdempotencyService (SPEC-05 §3).
 *
 * <p>Verifies the three checkKey outcomes:
 *
 * <ol>
 *   <li>Key not found → Optional.empty() (proceed with creation)
 *   <li>Key found + same hash + unexpired → Optional.of(existingId) (replay 200)
 *   <li>Key found + different hash + unexpired → 409 CodedException
 * </ol>
 *
 * And the registerKey happy path.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String TENANT_ID = "empresa-abc";
    private static final String RESOURCE_TYPE = "paciente";
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();
    private static final String BODY_HASH = "abc123deadbeef";
    private static final String COMPOSITE_KEY =
            IdempotencyService.compositeKey(RESOURCE_TYPE, IDEMPOTENCY_KEY);

    @Mock private IdempotencyKeyRepositoryPort idempotencyKeyRepository;

    @InjectMocks private IdempotencyService idempotencyService;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // checkKey
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("checkKey: key not found → returns Optional.empty() (new request)")
    void checkKey_notFound_returnsEmpty() {
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.empty());

        Optional<UUID> result =
                idempotencyService.checkKey(IDEMPOTENCY_KEY, RESOURCE_TYPE, BODY_HASH);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
            "checkKey: key found, same hash, unexpired → returns existing resourceId (replay 200)")
    void checkKey_sameHashUnexpired_returnsExistingId() {
        UUID existingId = UUID.randomUUID();
        IdempotencyKeyRecord record =
                new IdempotencyKeyRecord(COMPOSITE_KEY, existingId, BODY_HASH, Instant.now());
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.of(record));

        Optional<UUID> result =
                idempotencyService.checkKey(IDEMPOTENCY_KEY, RESOURCE_TYPE, BODY_HASH);

        assertThat(result).contains(existingId);
    }

    @Test
    @DisplayName("checkKey: key found, different hash → throws 409 idempotency-mismatch")
    void checkKey_differentHash_throws409() {
        UUID existingId = UUID.randomUUID();
        IdempotencyKeyRecord record =
                new IdempotencyKeyRecord(
                        COMPOSITE_KEY, existingId, "different-hash", Instant.now());
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.of(record));

        assertThatThrownBy(
                        () ->
                                idempotencyService.checkKey(
                                        IDEMPOTENCY_KEY, RESOURCE_TYPE, BODY_HASH))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining("different request body")
                .satisfies(
                        ex -> {
                            CodedException ce = (CodedException) ex;
                            assertThat(ce.code()).isEqualTo("pac.idempotency-mismatch");
                        });
    }

    @Test
    @DisplayName("checkKey: key found but expired (>24h) → returns Optional.empty() (treat as new)")
    void checkKey_expired_returnsEmpty() {
        UUID existingId = UUID.randomUUID();
        Instant expiredAt = Instant.now().minusSeconds(IdempotencyService.WINDOW_SECONDS + 60);
        IdempotencyKeyRecord record =
                new IdempotencyKeyRecord(COMPOSITE_KEY, existingId, BODY_HASH, expiredAt);
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.of(record));

        Optional<UUID> result =
                idempotencyService.checkKey(IDEMPOTENCY_KEY, RESOURCE_TYPE, BODY_HASH);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // registerKey
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("registerKey: key not in DB → saves new entry")
    void registerKey_notFound_saves() {
        UUID resourceId = UUID.randomUUID();
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        idempotencyService.registerKey(IDEMPOTENCY_KEY, RESOURCE_TYPE, resourceId, BODY_HASH);

        verify(idempotencyKeyRepository).save(any(IdempotencyKeyRecord.class));
    }

    @Test
    @DisplayName("registerKey: key already in DB (concurrent race) → skips insert silently")
    void registerKey_alreadyExists_skipsInsert() {
        UUID resourceId = UUID.randomUUID();
        IdempotencyKeyRecord existing =
                new IdempotencyKeyRecord(COMPOSITE_KEY, resourceId, BODY_HASH, Instant.now());
        when(idempotencyKeyRepository.findByKey(COMPOSITE_KEY)).thenReturn(Optional.of(existing));

        idempotencyService.registerKey(IDEMPOTENCY_KEY, RESOURCE_TYPE, resourceId, BODY_HASH);

        verify(idempotencyKeyRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // compositeKey helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("compositeKey: builds deterministic composite from resourceType + key")
    void compositeKey_deterministic() {
        String key = IdempotencyService.compositeKey("paciente", "abc-123");
        assertThat(key).isEqualTo("paciente:abc-123");
    }
}
