import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';
import { EligibilityPort } from './eligibility.port';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

interface EligibilityResponseDto {
  eligible: boolean;
  missingStates: string[];
}

// Real adapter — POST /gest-service/eligibility (api-contract §3; backend
// EligibilityController). Backend shape {eligible, missingStates} is mapped to
// the FE contract {eligible, reason, unsupportedPrimitiveIds} here.
@Injectable()
export class EligibilityHttpAdapter implements EligibilityPort {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(GEST_SERVICE_BASE_URL);

  async check(urgencia: Urgencia): Promise<EligibilityResult> {
    try {
      const res = await firstValueFrom(
        this.http.post<EligibilityResponseDto>(`${this.baseUrl}/gest-service/eligibility`, {
          urgencyLevel: urgencia,
        }),
      );
      return {
        eligible: res.eligible,
        reason: res.eligible
          ? null
          : `Primitivos ainda não detectáveis: ${res.missingStates.join(', ')}`,
        unsupportedPrimitiveIds: res.missingStates,
      };
    } catch {
      // Backend unreachable — fail open on non-emergency, honest-block on emergency
      // (never silently claim eligibility for a command that could be an emergency alarm).
      return urgencia === 'emergencia'
        ? {
            eligible: false,
            reason: 'gest-service indisponível — não foi possível confirmar elegibilidade.',
            unsupportedPrimitiveIds: [],
          }
        : { eligible: true, reason: null, unsupportedPrimitiveIds: [] };
    }
  }
}
