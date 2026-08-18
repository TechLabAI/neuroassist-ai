// =============================================================================
// DASH_NAV_CONFIG — FE-DASH-006
// Shell navigation configuration for the DASH module.
// DASH is order:0 (top of sidebar) and the default landing (shell_route: /).
// showInNav: true — Status is the primary caregiver surface.
// =============================================================================

export const DASH_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Status',
  icon: 'shield',
  route: '/',
  order: 0,
} as const;
