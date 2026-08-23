// @neuroassist/shared/access-http-base — tokens (P1 FE-E2E-002)
//
// ACCESS_HTTP_BASE: configurable base URL token (replaces the Wave-0 constant).
//   Default factory: '/api' — override per environment in app.config.ts.
//
// EMPRESA_ID_TOKEN: injection token that returns Signal<string | null>.
//   The accessHttpInterceptor reads this to attach X-Empresa-Id.
//   Wiring in app.config.ts:
//     { provide: EMPRESA_ID_TOKEN,
//       useFactory: () => inject(AcsEmpresaContextService).empresaId }
//   Nx boundary note: shared/* → shared/* is legal (scope:shared → scope:shared).
//   scope:web (app.config.ts) wires the concrete service (scope:acs) to this token.

import { InjectionToken, Signal } from '@angular/core';

export const ACCESS_HTTP_BASE = new InjectionToken<string>('ACCESS_HTTP_BASE', {
  providedIn: 'root',
  factory: () => '/api',
});

/**
 * InjectionToken that provides the current active empresa (tenant) ID as a Signal.
 * The signal returns null when no empresa is active (pre-provisioning or logged out).
 *
 * Required by accessHttpInterceptor to attach X-Empresa-Id header.
 * Must be provided in the root ApplicationConfig (app.config.ts).
 */
export const EMPRESA_ID_TOKEN = new InjectionToken<Signal<string | null>>(
  'EMPRESA_ID_TOKEN',
);
