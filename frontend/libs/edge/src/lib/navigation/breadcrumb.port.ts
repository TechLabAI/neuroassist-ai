import { InjectionToken } from '@angular/core';

// ---------------------------------------------------------------------------
// BreadcrumbPort — FE-EDGE-016
//
// Push-only port to the shell breadcrumb trail.  Implemented by
// @neuroassist/shared/ui-layout BreadcrumbService.  When that lib is not yet
// available the default factory provides a no-op stub so nothing breaks.
//
// To wire the real service:
//   { provide: EDGE_BREADCRUMB_PORT, useExisting: BreadcrumbService }
// ---------------------------------------------------------------------------

/** One segment in a breadcrumb trail. */
export interface BreadcrumbItem {
  /** Human-readable label shown in the trail (pt-BR). */
  label: string;
  /** Absolute route URL for the link.  Omit for the current (leaf) segment. */
  url?: string;
}

/**
 * Push-only contract used by EdgeBreadcrumbService.
 * The host shell BreadcrumbService must satisfy this interface.
 */
export interface BreadcrumbPort {
  push(items: BreadcrumbItem[]): void;
}

/**
 * InjectionToken for the BreadcrumbPort.
 * Default factory: no-op stub (keeps EDGE self-contained until ui-layout is ready).
 */
export const EDGE_BREADCRUMB_PORT = new InjectionToken<BreadcrumbPort>(
  'EDGE_BREADCRUMB_PORT',
  {
    factory: (): BreadcrumbPort => ({
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      push: (_items: BreadcrumbItem[]) => {},
    }),
  },
);
