import { ACS_ROUTES } from './acs.routes';

// =============================================================================
// ACS_MODULE_DESCRIPTOR — SPEC-05 §1 (tool-contract)
// Barrel export from @neuroassist/acs.
// The shell reads this to register ACS without importing internals.
// showInNav:true — "Cuidadores" nav entry for coordinators.
// =============================================================================

export const ACS_MODULE_DESCRIPTOR = {
  key: 'acs',
  routes: ACS_ROUTES,
  menu: {
    showInNav: true as const,
    label: 'Cuidadores',
    icon: 'manage_accounts',
    route: '/acs',
    order: 40,
  },
  permissions: {
    view: 'acs.cuidador.read',
    write: 'acs.cuidador.write',
    linkWrite: 'acs.vinculo.write',
  },
} as const;
