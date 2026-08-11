package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.api.dto.EligibilityRequest;
import com.neuroassist.gest.api.dto.EligibilityResponse;

/** Inbound port — emergency-command eligibility guard. */
public interface EligibilityUseCase {

    EligibilityResponse check(EligibilityRequest request);
}
