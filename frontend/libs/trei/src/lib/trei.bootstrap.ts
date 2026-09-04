import { Injector } from '@angular/core';

// =============================================================================
// trei.bootstrap.ts — SPEC-06 §3 (FE-TREI-006)
//
// Lifecycle functions for TREI module init/destroy.
// Called by scope:web (APP_INITIALIZER or DestroyRef) when TREI is loaded/unloaded.
//
// treiInit:
//   - TREI providers are wired in app.config.ts via provideTrei({ adapter: 'mock' }).
//   - No SSE streams to open in Wave-0 (all communication via request/response turns).
//   - Kill-switch: TREI_MODULE_FEATURE_FLAGS['treinamento.enabled'] controls visibility.
//
// treiDestroy:
//   - No SSE or persistent connections to tear down in Wave-0.
//   - Facade signals are reset via Angular's DI lifecycle automatically.
//
// Usage in app.config.ts (scope:web):
//   provideAppInitializer(() => treiInit(inject(Injector)))
//
// NOTE: treiInit is idempotent (calling multiple times is safe).
// =============================================================================

/**
 * treiInit — initialize the TREI module.
 *
 * Idempotent: calling multiple times is safe.
 * Wave-0: no SSE, no shell registry calls — providers handle all wiring.
 *
 * @param injector - Angular injector (typically from inject(Injector) in app context).
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function treiInit(_injector: Injector): void {
  // Wave-0: providers wired in app.config.ts via provideTrei({ adapter: 'mock' }).
  // No explicit initialization needed — facades initialize lazily on first inject().
  // P8: TreiTelemetryService.init() will be called here for session-start telemetry.
}

/**
 * treiDestroy — tear down the TREI module.
 *
 * Safe to call when module was never initialized (no-op).
 * Wave-0: no persistent connections to close.
 *
 * @param injector - Angular injector (same instance as treiInit).
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function treiDestroy(_injector: Injector): void {
  // Wave-0: nothing to tear down.
  // P8: TreiTelemetryService.flush() will be called here before teardown.
}
