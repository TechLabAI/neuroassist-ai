// =============================================================================
// ACS_NAV_CONFIG — FE-ACS-006
// Shell navigation configuration for the ACS module.
// Extracted from ACS_MODULE_DESCRIPTOR.menu for standalone re-use by the Shell
// (e.g. sidebar generation, breadcrumb registration, route-to-label mapping).
// =============================================================================

// O rotulo deixou de ser "Cuidadores" (fluxo-e-perfis.md §4): a coordenadora nao
// vem aqui listar uma entidade, vem cuidar da EQUIPE — criar, desativar e
// vincular a pacientes. Item exclusivo da coordenadora; quem monta o menu
// filtra por `acs.cuidador.criar` (ver shell/menu-trabalho.ts).
export const ACS_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Equipe',
  icon: 'diversity_3',
  route: '/acs',
  order: 3,
} as const;
