import { inject } from '@angular/core';
import {
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  HttpEvent,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { EVNT_EMPRESA_CONTEXT } from '../access/evnt-access-tokens';

// =============================================================================
// evntHttpInterceptorFn — INT-FE-EVNT-001 / P6-A
//
// Functional HTTP interceptor that attaches X-Empresa-Id to every EVNT HTTP
// request once a tenant has been provisioned.
//
// X-Empresa-Id is read from EVNT_EMPRESA_CONTEXT (Wave-0: null).
// When null (pre-provisioning), request passes through unchanged.
//
// Wiring: registered via makeEnvironmentProviders + withInterceptors([evntHttpInterceptorFn])
// in evnt.providers.ts so it only applies within the EVNT provider scope.
//
// Note: fetch()-based SSE (openStream()) does not go through HttpClient, so
// EVNT_EMPRESA_CONTEXT is also read directly in EvntClientHttpAdapter constructor.
// =============================================================================

/**
 * Functional interceptor that attaches X-Empresa-Id to EVNT HttpClient requests.
 *
 * Behaviour:
 * - EVNT_EMPRESA_CONTEXT null → pass request unchanged (Wave-0, pre-provisioning).
 * - empresa.id present → clone request with header X-Empresa-Id: <id>.
 */
export const evntHttpInterceptorFn: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const empresaContext = inject(EVNT_EMPRESA_CONTEXT, { optional: true });
  const empresaId = empresaContext?.id ?? null;

  if (!empresaId) {
    return next(req);
  }

  const enriched = req.clone({
    setHeaders: { 'X-Empresa-Id': empresaId },
  });

  return next(enriched);
};
