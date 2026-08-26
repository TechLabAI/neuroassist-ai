package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.PatientKeyPort;
import com.neuroassist.hist.domain.model.PatientEncryptionKey;
import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyId;
import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.repository.PatientEncryptionKeyJpaRepository;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — per-patient AES key persistence (P10, HIST-024, RULE-07).
 *
 * <p>{@code TenantContextHolder.require()} is the first call in every public method (RULE-07).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientKeyAdapter implements PatientKeyPort {

    private final PatientEncryptionKeyJpaRepository repository;

    @Override
    public Optional<PatientEncryptionKey> find(UUID subjectId, String tenantId) {
        TenantContextHolder.require();
        return repository
                .findById(new PatientEncryptionKeyId(subjectId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public PatientEncryptionKey create(PatientEncryptionKey key) {
        TenantContextHolder.require();
        PatientEncryptionKeyJpaEntity entity =
                PatientEncryptionKeyJpaEntity.builder()
                        .subjectId(key.subjectId())
                        .tenantId(key.tenantId())
                        .keyCiphertext(key.keyCiphertext())
                        .keyIv(key.keyIv())
                        .createdAt(key.createdAt())
                        .build();
        return toDomain(repository.save(entity));
    }

    @Override
    public void erase(UUID subjectId, String tenantId) {
        TenantContextHolder.require();
        int rows = repository.eraseKey(subjectId, tenantId);
        log.info(
                "Patient key erased: subjectId={} tenant={} rowsUpdated={}",
                subjectId,
                tenantId,
                rows);
    }

    private PatientEncryptionKey toDomain(PatientEncryptionKeyJpaEntity e) {
        return new PatientEncryptionKey(
                e.getSubjectId(), e.getTenantId(),
                e.getKeyCiphertext(), e.getKeyIv(),
                e.getCreatedAt(), e.getErasedAt());
    }
}
