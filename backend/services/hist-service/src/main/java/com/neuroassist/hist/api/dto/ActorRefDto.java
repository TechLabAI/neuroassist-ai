package com.neuroassist.hist.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO — caregiver actor snapshot nested inside {@link RecordAuditFactRequest} and returned in
 * {@link RecordAuditFactResponse}.
 */
public record ActorRefDto(

        /** Reference to the acting caregiver in ACS. */
        @NotNull UUID caregiverId,

        /** Snapshot of the caregiver's display name at the time of the fact (≤ 200 chars). */
        @NotBlank @Size(max = 200) String displayNameSnapshot) {}
