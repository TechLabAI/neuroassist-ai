// =============================================================================
// DASH_NAV_CONFIG — FE-DASH-006
// Shell navigation configuration for the DASH module.
// DASH is order:0 (top of sidebar) and the default landing (shell_route: /).
// showInNav: true — e a primeira superficie da cuidadora.
//
// O rotulo deixou de ser "Status" (fluxo-e-perfis.md §4). "Status" nomeia o que
// a tela mostra por dentro; quem cuida chega aqui para ver OS PACIENTES DELA —
// com o estado do dispositivo e se ha alarme ativo. O menu passa a espelhar o
// trabalho, e nao a arquitetura.
// =============================================================================

export const DASH_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Meus pacientes',
  icon: 'patient_list',
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
