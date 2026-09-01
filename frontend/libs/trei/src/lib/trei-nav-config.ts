// =============================================================================
// TREI_NAV_CONFIG — FE-TREI-006 (tool-contract / SPEC-05)
// Shell navigation configuration for the TREI module.
// showInNav: true — Treinamento is visible in the main nav for caregivers
//   with treinamento.sessao.conduzir scope (SPEC-05 shell integration).
//
// The sidebar entry is registered in menu-trabalho.ts (shell decides composition,
// not the module). This config is the module's declaration about itself;
// menu-trabalho.ts wires it into the nav with the correct authority check.
// =============================================================================

export const TREI_NAV_CONFIG = {
  showInNav: true as const,
  label: 'Treinamento',
  icon: 'chat',
  route: '/treinamento',
  order: 25,  // between "Comandos do paciente" (20) and "Histórico" (30)
} as const;
