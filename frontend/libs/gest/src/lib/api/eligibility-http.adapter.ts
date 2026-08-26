import { Injectable, InjectionToken, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';
import { EligibilityPort } from './eligibility.port';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// Multi-empresa wiring (P1 — GEST-FE-009 / GEST-INT-002)
//
// X-Empresa-Id is sent for traceability in logs and debug (multi-empresa.md §Propagação).
// Tenant isolation is enforced by the JWT claim (tenant_id), NOT by this header.
// The header is "redundante com JWT, ajuda em debug/log" (multi-empresa.md:100).
//
// TODO[MULTI-EMPRESA][stub]: replace this local interface + token with the DI token
// exported from @neuroassist/shared/empresa-context once that lib is buildable
// (currently it has no ng-package.json / tsconfig.lib.json — Wave-0 stub).
// Shape matches EmpresaContext from that lib: { readonly id: string | null }.
// The default factory returns { id: null } until the empresa selector has resolved.
// ---------------------------------------------------------------------------
export interface EmpresaContextLocal { readonly id: string | null; }

export const EMPRESA_CONTEXT_TOKEN = new InjectionToken<EmpresaContextLocal>(
  'GEST_EMPRESA_CONTEXT',
  {
    providedIn: 'root',
    factory: () => ({ id: null }),
  },
);

interface EligibilityResponseDto {
  eligible: boolean;
  missingStates: string[];
}

// Real adapter — POST /api/v1/gest/eligibility (GWY-normalized — ADR-008 / E2E-014).
// Previously: POST ${baseUrl}/gest-service/eligibility (direct service path, pre-ADR-008).
// In 'integrated' profile: baseUrl = '/api/v1/gest' (overridden in app.config.ts).
// In 'mock' profile: adapter not used (provideGest({ adapter: 'mock' }) bypasses this).
// Backend shape {eligible, missingStates} is mapped to
// the FE contract {eligible, reason, unsupportedPrimitiveIds} here.
@Injectable()
export class EligibilityHttpAdapter implements EligibilityPort {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(GEST_SERVICE_BASE_URL);
  private readonly empresaContext = inject(EMPRESA_CONTEXT_TOKEN);

  async check(urgencia: Urgencia): Promise<EligibilityResult> {
    // Path construction (option a, FE-E2E-004):
    //   baseUrl is overridden to '/api/v1/gest' in integrated profile via app.config.ts.
    //   ${baseUrl}/eligibility → /api/v1/gest/eligibility (proxied via proxy.conf.js).
    //   In the default (non-integrated) profile, baseUrl stays the direct gest-service origin
    //   and this adapter is not wired at all (provideGest uses the mock adapter instead).
    const headers: Record<string, string> = {};
    if (this.empresaContext.id !== null) {
      // Send for log/debug traceability — isolation is enforced by the JWT claim tenant_id.
      headers['X-Empresa-Id'] = this.empresaContext.id;
    }

    try {
      const res = await firstValueFrom(
        this.http.post<EligibilityResponseDto>(
          `${this.baseUrl}/eligibility`,
          { urgencyLevel: urgencia },
          Object.keys(headers).length > 0 ? { headers } : undefined,
        ),
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
