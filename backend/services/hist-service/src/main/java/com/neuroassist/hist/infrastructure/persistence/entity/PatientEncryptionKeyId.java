package com.neuroassist.hist.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.*;

/** Composite PK for {@link PatientEncryptionKeyJpaEntity}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PatientEncryptionKeyId implements Serializable {
    private UUID subjectId;
    private String tenantId;
}
