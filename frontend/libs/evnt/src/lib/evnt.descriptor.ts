import { EVNT_ROUTES } from './evnt.routes';
import { evntBootstrap } from './evnt.bootstrap';

// =============================================================================
// EVNT_MODULE_DESCRIPTOR — SPEC-05 §1 (tool-contract)
// Barrel export from @neuroassist/evnt.
// The shell reads this to register EVNT without importing internals.
// =============================================================================

export const EVNT_MODULE_DESCRIPTOR = {
  key: 'evnt',
  routes: EVNT_ROUTES,
  menu: {
    showInNav: true as const,
    label: 'Eventos',
    icon: 'notifications_active',
    route: '/eventos',
    order: 20,
  },
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
