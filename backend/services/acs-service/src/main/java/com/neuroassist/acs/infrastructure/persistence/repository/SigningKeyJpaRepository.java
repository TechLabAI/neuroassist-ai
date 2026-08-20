package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.SigningKeyJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyJpaRepository extends JpaRepository<SigningKeyJpaEntity, UUID> {

    /**
     * Returns the active signing key (most recently activated).
     *
     * @return Optional SigningKeyJpaEntity with status ACTIVE
     */
    Optional<SigningKeyJpaEntity> findFirstByStatusOrderByNotBeforeDesc(SigningKeyStatus status);

    /**
     * Returns all keys with the given statuses (for JWKS endpoint).
     *
     * @param statuses list of statuses to include (ACTIVE, RETIRED)
     * @return list of matching signing key entities
     */
    List<SigningKeyJpaEntity> findByStatusIn(List<SigningKeyStatus> statuses);
}
