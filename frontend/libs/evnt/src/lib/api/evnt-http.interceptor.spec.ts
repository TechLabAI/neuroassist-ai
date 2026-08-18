import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpHandlerFn, HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { EVNT_EMPRESA_CONTEXT } from '../access/evnt-access-tokens';
import { evntHttpInterceptorFn } from './evnt-http.interceptor';

// =============================================================================
// evntHttpInterceptorFn spec — INT-FE-EVNT-001 / P6-A
// Verifies that X-Empresa-Id is attached when a non-null empresa context exists,
// and that requests pass through unchanged when context is null.
// =============================================================================

function runInterceptor(req: HttpRequest<unknown>, _empresaId: string | null) {
  let capturedRequest: HttpRequest<unknown> | undefined;

  const next: HttpHandlerFn = (interceptedReq) => {
    capturedRequest = interceptedReq as HttpRequest<unknown>;
    return of(new HttpResponse({ status: 200 }));
  };

  TestBed.runInInjectionContext(() => {
    evntHttpInterceptorFn(req, next).subscribe();
  });

  return capturedRequest;
}

describe('evntHttpInterceptorFn', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient()],
    });
  });

  it('adds X-Empresa-Id header when empresa context is non-null', () => {
    TestBed.overrideProvider(EVNT_EMPRESA_CONTEXT, { useValue: { id: 'empresa-42' } });

    const req = new HttpRequest('GET', '/api/v1/events/active');
    const enriched = runInterceptor(req, null);

    expect(enriched?.headers.get('X-Empresa-Id')).toBe('empresa-42');
  });

  it('passes request unchanged when empresa context is null', () => {
    TestBed.overrideProvider(EVNT_EMPRESA_CONTEXT, { useValue: null });

    const req = new HttpRequest('GET', '/api/v1/events/active');
    const passed = runInterceptor(req, null);

    expect(passed?.headers.has('X-Empresa-Id')).toBe(false);
  });
});
