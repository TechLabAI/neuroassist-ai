import { InjectionToken } from '@angular/core';

// ---------------------------------------------------------------------------
// AdapterMode — build-time / runtime toggle between mock and HTTP adapters.
//
// APP_ADAPTER_MODE token:
//   Default factory → 'mock' (hermetic CI, no backend required).
//   In the 'integrated' Nx configuration, CURRENT_ADAPTER_MODE from
//   adapter-mode-env.ts is provided via fileReplacements so the token resolves
//   to 'http' at build time without modifying this file.
//
// Usage:
//   inject(APP_ADAPTER_MODE) — anywhere in DI context
//   CURRENT_ADAPTER_MODE    — static import in app.config.ts providers array
// ---------------------------------------------------------------------------

export type AdapterMode = 'mock' | 'http';

export const APP_ADAPTER_MODE = new InjectionToken<AdapterMode>('APP_ADAPTER_MODE', {
  providedIn: 'root',
  factory: () => 'mock',  // safe default; never requires backend
});
