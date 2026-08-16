import { HIST_ROUTES } from './hist.routes';
import { HIST_NAV_CONFIG } from './hist-nav-config';

// =============================================================================
// HIST_MODULE_DESCRIPTOR — FE-HIST-006 (tool-contract)
// Barrel export from @neuroassist/hist.
// The shell reads this to register HIST without importing internals.
// =============================================================================

export const HIST_MODULE_PERMISSION_MAP = {
  'hist.read':       'hist:read',
  'hist.read.all':   'hist:read:all',
} as const;

export const HIST_MODULE_FEATURE_FLAGS = {
  /** G1: 'evnt' = POST /evnt/events/{id}/feedback; 'hist' = hist-service proxy */
  'hist.feedbackTarget': 'evnt' as 'evnt' | 'hist',
  /** Chain-verification badge visible only when hist:read:all scope present */
  'hist.showVerifyBadge': 'off' as 'on' | 'off',
} as const;

export const HIST_MODULE_DESCRIPTOR = {
  key: 'hist',
  routes: HIST_ROUTES,
  menu: HIST_NAV_CONFIG,
  permissionMap: HIST_MODULE_PERMISSION_MAP,
  featureFlags: HIST_MODULE_FEATURE_FLAGS,
  permissions: {
    view: 'hist.read',
    viewAll: 'hist.read.all',
  },
} as const;
