import { Injectable, InjectionToken, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';
import { EligibilityPort } from './eligibility.port';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// Multi-empresa seam (P5 preparatory — GEST-INT-002)
//
// TODO[MULTI-EMPRESA]: replace this local token with the DI token exported from
// @neuroassist/shared/empresa-context once that lib is elevated to buildable.
// The interface shape (id: string | null) mirrors EmpresaContext from that lib.
// When @neuroassist/shared/access-http-base implements the HttpInterceptorFn,
// remove this injection entirely and let the interceptor handle X-Empresa-Id.
// ---------------------------------------------------------------------------
interface EmpresaContextStub { readonly id: string | null; }
const EMPRESA_CONTEXT_SEAM = new InjectionToken<EmpresaContextStub>('GEST_EMPRESA_CONTEXT_SEAM', {
  providedIn: 'root',
  factory: () => ({ id: null }), // Wave-0 stub: empresa id unknown until auth session resolves
});

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

  // TODO[MULTI-EMPRESA]: quando @neuroassist/shared/access-http-base implementar
  // o interceptor HttpInterceptorFn, remover esta injeção manual e confiar no interceptor.
  // Por ora, empresaContext.id é injetado mas não enviado (stub retorna null).
  // A injeção está aqui para preparar o ponto de wiring — sem bloquear a chamada HTTP.
  private readonly empresaContext = inject(EMPRESA_CONTEXT_SEAM);

  async check(urgencia: Urgencia): Promise<EligibilityResult> {
    // Path construction (option a, FE-E2E-004):
    //   baseUrl is overridden to '/api/v1/gest' in integrated profile via app.config.ts.
    //   ${baseUrl}/eligibility → /api/v1/gest/eligibility (proxied via proxy.conf.js).
    // Seam preparatório multi-empresa: quando empresaContext.id não é null, o interceptor de
    // access-http-base será responsável por injetar o header X-Empresa-Id automaticamente.
    try {
      const res = await firstValueFrom(
        this.http.post<EligibilityResponseDto>(`${this.baseUrl}/eligibility`, {
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
