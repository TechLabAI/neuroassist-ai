import { DASH_ROUTES } from './dash.routes';
import { DASH_NAV_CONFIG } from './dash-nav-config';

// =============================================================================
// DASH_MODULE_DESCRIPTOR — FE-DASH-006 (tool-contract)
// Barrel export from @neuroassist/dash.
// The shell reads this to register DASH without importing internals.
// =============================================================================

export const DASH_MODULE_PERMISSION_MAP = {
  'dash.view':             'dash:view',
  'dash.onboarding.entry': 'dash:onboarding.entry',
} as const;

export const DASH_MODULE_FEATURE_FLAGS = {
  /** ADR-005: server-driven summary via GET /panel/summary */
  'dash.serverSummary': 'on' as 'on' | 'off',
  /** Notification center (/notificacoes) */
  'dash.notificationCenter': 'on' as 'on' | 'off',
  /** SSE stream (off → long-poll fallback) */
  'dash.sseStream': 'on' as 'on' | 'off',
} as const;

export const DASH_MODULE_DESCRIPTOR = {
  key: 'dash',
  routes: DASH_ROUTES,
  menu: DASH_NAV_CONFIG,
  permissionMap: DASH_MODULE_PERMISSION_MAP,
  featureFlags: DASH_MODULE_FEATURE_FLAGS,
  permissions: {
    view: 'dash.view',
    onboarding: 'dash.onboarding.entry',
  },
} as const;
