import { inject } from '@angular/core';
import {
  HttpContextToken,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  HttpEvent,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { AcsEmpresaContextService } from './acs-empresa-context.service';

// =============================================================================
// acsHttpInterceptorFn — INT-FE-ACS-001
//
// Functional HTTP interceptor responsible for attaching `X-Empresa-Id` to all
// ACS API requests once a tenant has been provisioned.
//
// X-Empresa-Id is read from AcsEmpresaContextService (the ACS-local signal
// store). It is NULL until AcsTenantFacade.create() resolves with status 'active'.
//
// Exclusion: Tenant provisioning routes (POST /tenants, POST /tenants/:id/provisioning:retry)
// MUST NOT carry X-Empresa-Id (no active tenant exists yet). Mark those requests
// with SKIP_EMPRESA_ID_HEADER context token.
//
// Wiring: register in app.config.ts → provideHttpClient(withInterceptors([acsHttpInterceptorFn]))
//
// Note: Authorization header is managed by @neuroassist/shared/auth-session interceptor (Wave-0
// stub — will be promoted in a future wave). X-Correlation-Id is managed by
// @neuroassist/shared/access-http-base when that lib is promoted from Wave-0 stub.
// =============================================================================

/**
 * HttpContextToken used to signal that a request should NOT carry the X-Empresa-Id header.
 * Set to true for tenant provisioning requests (POST /tenants, retry) where no active
 * tenant exists yet.
 *
 * Usage in AcsTenantHttpAdapter:
 *   this.http.post(url, body, { context: new HttpContext().set(SKIP_EMPRESA_ID_HEADER, true) })
 */
export const SKIP_EMPRESA_ID_HEADER = new HttpContextToken<boolean>(() => false);

/**
 * Functional interceptor that attaches X-Empresa-Id to ACS HTTP requests.
 *
 * Behavior:
 * - If AcsEmpresaContextService.empresaId() is null → pass through unchanged.
 * - If request.context has SKIP_EMPRESA_ID_HEADER = true → pass through unchanged.
 * - Otherwise → clone request with header X-Empresa-Id: <empresaId>.
 *
 * Register in app.config.ts:
 *   provideHttpClient(withInterceptors([acsHttpInterceptorFn, ...]))
 */
export const acsHttpInterceptorFn: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const empresaContext = inject(AcsEmpresaContextService);
  const empresaId = empresaContext.empresaId();

  // No empresa yet (pre-provisioning) or route is explicitly excluded
  if (!empresaId || req.context.get(SKIP_EMPRESA_ID_HEADER)) {
    return next(req);
  }

  const enriched = req.clone({
    setHeaders: { 'X-Empresa-Id': empresaId },
  });

  return next(enriched);
};
