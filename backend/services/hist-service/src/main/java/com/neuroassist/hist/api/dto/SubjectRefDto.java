package com.neuroassist.hist.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO — subject reference nested inside {@link RecordAuditFactRequest} and returned in {@link
 * RecordAuditFactResponse}.
 */
public record SubjectRefDto(

        /** Subject type string: {@code paciente | comando | cuidador | dispositivo}. */
        @NotBlank String type,

        /** Opaque subject identifier owned by the producer. */
        @NotNull UUID id) {}
