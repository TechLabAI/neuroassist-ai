// =============================================================================
// EVNT_NAV_CONFIG — FE-EVNT-006
// Shell navigation configuration for the EVNT module.
// Extracted from EVNT_MODULE_DESCRIPTOR.menu for standalone re-use by the Shell
// (e.g. sidebar generation, breadcrumb registration, route-to-label mapping).
// =============================================================================

// O rotulo deixou de ser "Eventos" (fluxo-e-perfis.md §4): "evento" e o nome do
// dominio, "alarme" e o que a pessoa esta procurando — o que chegou e o que
// falta confirmar. O alarme de EMERGENCIA continua sem ser destino de navegacao:
// ele substitui a tela ativa via outlet global do app-shell. Este item e a fila.
export const EVNT_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Alarmes',
  icon: 'notifications_active',
  route: '/eventos',
  order: 20,
} as const;
