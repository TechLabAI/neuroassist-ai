// =============================================================================
// HIST_NAV_CONFIG — FE-HIST-006
// Shell navigation configuration for the HIST module.
// Extracted from HIST_MODULE_DESCRIPTOR.menu for standalone use by the Shell
// (sidebar generation, breadcrumb registration, route-to-label mapping).
// showInNav: true — Histórico is visible in the main nav for caregivers/coordinators.
//
// O rótulo permanece "Histórico" — já é o nome do trabalho, não o do módulo. A
// coordenadora vê "Histórico completo" (fluxo-e-perfis.md §4), mas isso é o
// MESMO destino com alcance maior: quem decide o alcance é o backend, a partir
// de `hist:read:all`. O shell troca o rótulo quando a authority está presente —
// ver apps/neuroassist-ai-web/src/app/shell/menu-trabalho.ts.
// =============================================================================

export const HIST_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Histórico',
  icon: 'history',
  route: '/historico',
  order: 5,
} as const;
