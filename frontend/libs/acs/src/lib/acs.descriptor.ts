import { ACS_ROUTES } from './acs.routes';
import { ACS_NAV_CONFIG } from './acs-nav-config';

// =============================================================================
// ACS_MODULE_DESCRIPTOR — SPEC-05 §1 (tool-contract)
// Barrel export from @neuroassist/acs.
// The shell reads this to register ACS without importing internals.
// showInNav:true — "Cuidadores" nav entry for coordinators.
// =============================================================================

// ---------------------------------------------------------------------------
// MODULE_PERMISSION_MAP — FE-ACS-001
// Maps UI-level permission keys to the corresponding BE authority strings.
// Consumed by AcsPermissionService and shell RBAC checks.
// ---------------------------------------------------------------------------
export const MODULE_PERMISSION_MAP = {
  'acs.cuidador.write': 'acs.cuidador.criar',
  'acs.cuidador.deactivate': 'acs.cuidador.desativar',
  'acs.vinculo.write': 'acs.vinculo.criar',
  'acs.cuidador.read': 'acs.cuidador.listar',
} as const;

// ---------------------------------------------------------------------------
// MODULE_FEATURE_FLAGS — FE-ACS-001
// Feature flag defaults for the ACS module.
// coverageGuard: 'on'              — ADR-005 invariant enforcement enabled
// inviteByEmail: 'off'             — email invite flow not yet implemented
// showSemCoberturaBanner: 'on'     — banner shown when patient has no coverage
// ---------------------------------------------------------------------------
export const MODULE_FEATURE_FLAGS = {
  'acs.coverageGuard': 'on' as 'on' | 'off',
  'acs.inviteByEmail': 'off' as 'on' | 'off',
  'acs.showSemCoberturaBanner': 'on' as 'on' | 'off',
} as const;

export const ACS_MODULE_DESCRIPTOR = {
  key: 'acs',
  routes: ACS_ROUTES,
  menu: ACS_NAV_CONFIG,
  permissionMap: MODULE_PERMISSION_MAP,
  featureFlags: MODULE_FEATURE_FLAGS,
  permissions: {
    view: 'acs.cuidador.read',
    write: 'acs.cuidador.write',
    linkWrite: 'acs.vinculo.write',
  },
} as const;
