package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyId;
import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientEncryptionKeyJpaRepository
        extends JpaRepository<PatientEncryptionKeyJpaEntity, PatientEncryptionKeyId> {

    /** Erases key material: nulls ciphertext + iv, stamps erased_at. */
    @Modifying
    @Query(
            """
            UPDATE PatientEncryptionKeyJpaEntity k
               SET k.keyCiphertext = NULL,
                   k.keyIv = NULL,
                   k.erasedAt = CURRENT_TIMESTAMP
             WHERE k.subjectId = :subjectId
               AND k.tenantId = :tenantId
            """)
    int eraseKey(@Param("subjectId") UUID subjectId, @Param("tenantId") String tenantId);
}
