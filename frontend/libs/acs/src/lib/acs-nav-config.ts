// =============================================================================
// ACS_NAV_CONFIG — FE-ACS-006
// Shell navigation configuration for the ACS module.
// Extracted from ACS_MODULE_DESCRIPTOR.menu for standalone re-use by the Shell
// (e.g. sidebar generation, breadcrumb registration, route-to-label mapping).
// =============================================================================

export const ACS_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Cuidadores',
  icon: 'group',
  route: '/acs',
  order: 3,
} as const;
