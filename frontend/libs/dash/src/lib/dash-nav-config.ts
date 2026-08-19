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

// =============================================================================
// DASH_BREADCRUMBS — FE-DASH-020 (breadcrumb trail per route — SPEC-01 §4)
//
// Defines the breadcrumb trail for each DASH route.
// The shell BreadcrumbService reads these from route `data.breadcrumb` or via
// DashShellIntegrationService.contributeBreadcrumbs() — whichever the shell adopts.
//
// /           → [Status]
// /notificacoes → [Status › Notificações]
// /entrada    → [] (chromeless — no breadcrumb)
// =============================================================================

export interface DashBreadcrumbItem {
  readonly label: string;
  readonly route: string;
}

export const DASH_BREADCRUMBS = {
  /** / — single-segment trail for the Home Status page. */
  home: [
    { label: 'Status', route: '/' },
  ] satisfies DashBreadcrumbItem[],

  /** /notificacoes — two-segment trail. */
  notifications: [
    { label: 'Status', route: '/' },
    { label: 'Notificações', route: '/notificacoes' },
  ] satisfies DashBreadcrumbItem[],

  /** /entrada — chromeless; no breadcrumb. */
  entrada: [] as DashBreadcrumbItem[],
} as const;
