package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.SigningKeyRepositoryPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.SigningKeyJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.SigningKeyJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter for SigningKeyRepositoryPort (CRUD operations on neuroassist_global.signing_key). Full
 * implementation including save() and findById() (ACS-011, P3). SECURITY: privateKeyEnc never
 * exposed via toDomain() — kept in infrastructure layer.
 */
@Component
@RequiredArgsConstructor
public class SigningKeyRepositoryAdapter implements SigningKeyRepositoryPort {

    private final SigningKeyJpaRepository repository;

    @Override
    public SigningKey save(SigningKey entity) {
        SigningKeyJpaEntity jpaEntity = toEntity(entity);
        SigningKeyJpaEntity saved = repository.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<SigningKey> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SigningKey> findActiveKey() {
        return repository
                .findFirstByStatusOrderByNotBeforeDesc(SigningKeyStatus.ACTIVE)
                .map(this::toDomain);
    }

    @Override
    public List<SigningKey> findPublicKeys() {
        return repository
                .findByStatusIn(List.of(SigningKeyStatus.ACTIVE, SigningKeyStatus.RETIRED))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Private mapping
    // -----------------------------------------------------------------------

    private SigningKeyJpaEntity toEntity(SigningKey key) {
        SigningKeyJpaEntity entity = new SigningKeyJpaEntity();
        if (key.id() != null) {
            entity.setId(key.id());
        }
        entity.setKid(key.kid());
        entity.setAlgorithm(key.algorithm() != null ? key.algorithm() : "RS256");
        entity.setPublicJwk(key.publicJwk());
        // privateKeyEnc: callers that need to store a real key must use a dedicated
        // key-management service; this adapter uses an empty placeholder for cases
        // where only public key data is being persisted (e.g., rotation metadata).
        entity.setPrivateKeyEnc(new byte[0]);
        entity.setStatus(key.status());
        entity.setNotBefore(
                key.notBefore() != null
                        ? LocalDateTime.ofInstant(key.notBefore(), ZoneOffset.UTC)
                        : null);
        entity.setRetiredAt(
                key.retiredAt() != null
                        ? LocalDateTime.ofInstant(key.retiredAt(), ZoneOffset.UTC)
                        : null);
        entity.setCriadoEm(LocalDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private SigningKey toDomain(SigningKeyJpaEntity entity) {
        // SECURITY: privateKeyEnc is NOT included in the domain model (RULE-02)
        return new SigningKey(
                entity.getId(),
                entity.getKid(),
                entity.getAlgorithm(),
                entity.getPublicJwk(),
                entity.getStatus(),
                entity.getNotBefore() != null
                        ? entity.getNotBefore().toInstant(ZoneOffset.UTC)
                        : null,
                entity.getRetiredAt() != null
                        ? entity.getRetiredAt().toInstant(ZoneOffset.UTC)
                        : null);
    }
}
