import { EVNT_ROUTES } from './evnt.routes';
import { evntBootstrap } from './evnt.bootstrap';
import { EVNT_NAV_CONFIG } from './evnt.nav-config';

// =============================================================================
// EVNT_MODULE_DESCRIPTOR — SPEC-05 §1 (tool-contract)
// Barrel export from @neuroassist/evnt.
// The shell reads this to register EVNT without importing internals.
// =============================================================================

export const EVNT_MODULE_DESCRIPTOR = {
  key: 'evnt',
  routes: EVNT_ROUTES,
  menu: EVNT_NAV_CONFIG,
  permissions: {
    view: 'evnt:eventos:read',
    confirm: 'evnt:confirm:write',
  },
  featureFlags: {
    liveChannel: 'on' as 'on' | 'off',    // off → forced polling-only
    routineNotices: 'on' as 'on' | 'off', // off → suppress discreet notices (FLAG-02)
  },
  bootstrap: evntBootstrap,
} as const;
