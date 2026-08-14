// =============================================================================
// EVNT_NAV_CONFIG — FE-EVNT-006
// Shell navigation configuration for the EVNT module.
// Extracted from EVNT_MODULE_DESCRIPTOR.menu for standalone re-use by the Shell
// (e.g. sidebar generation, breadcrumb registration, route-to-label mapping).
// =============================================================================

export const EVNT_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Eventos',
  icon: 'notifications_active',
  route: '/eventos',
  order: 20,
} as const;
