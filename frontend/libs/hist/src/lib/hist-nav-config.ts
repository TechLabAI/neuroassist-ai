// =============================================================================
// HIST_NAV_CONFIG — FE-HIST-006
// Shell navigation configuration for the HIST module.
// Extracted from HIST_MODULE_DESCRIPTOR.menu for standalone use by the Shell
// (sidebar generation, breadcrumb registration, route-to-label mapping).
// showInNav: true — Histórico is visible in the main nav for caregivers/coordinators.
// =============================================================================

export const HIST_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Histórico',
  icon: 'history',
  route: '/historico',
  order: 5,
} as const;
