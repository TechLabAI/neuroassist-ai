import { InjectionToken } from '@angular/core';

// ---------------------------------------------------------------------------
// EdgeToastService — abstraction for user-facing toast notifications.
// Injected optionally by facades; the concrete implementation is provided
// by the host app (scope:web or shared/ui-feedback once available).
//
// G6 requirement: action operations (register/pairToPatient/unpair/retire)
// show a toast after 2 automatic retries are exhausted (SPEC-07 §3).
// Liveness poll failures are SILENT — they only set derivedState='unknown'.
// ---------------------------------------------------------------------------

export interface EdgeToastService {
  show(
    message: string,
    action?: { label: string; callback: () => void },
  ): void;
}

export const EDGE_TOAST_TOKEN = new InjectionToken<EdgeToastService>(
  'EdgeToastToken',
);

/**
 * Standard pt-BR message shown when an action operation exhausts its retries.
 * Matches SPEC-07 §3 wording.
 */
export const EDGE_RETRY_TOAST_MESSAGE =
  'Falha temporária de conexão. Tente novamente.';
