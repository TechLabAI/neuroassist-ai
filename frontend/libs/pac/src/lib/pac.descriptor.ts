import { PAC_ROUTES } from './pac.routes';

// ---------------------------------------------------------------------------
// PAC_MODULE_DESCRIPTOR — SPEC-06 §1 / generator-config.yml module_contract
// The shell reads this to register PAC in nav and route table.
// ---------------------------------------------------------------------------

export const PAC_MENU_METADATA = {
  showInNav: true as const,
  label: 'Calibração',
  icon: 'tune',
} as const;

export const PAC_PERMISSION_MAP = {
  read: 'pac.paciente.read',
  write: 'pac.paciente.write',
  commandRead: 'pac.comando.read',
  commandWrite: 'pac.comando.write',
  commandPublish: 'pac.comando.publish',
} as const;

export const PAC_FEATURE_FLAGS = {
  inlineNewTipoGesto: 'on' as 'on' | 'off',
  deferredEdgeSync: 'on' as 'on' | 'off',
  emergencyGateAdvisoryHint: 'on' as 'on' | 'off',
} as const;

export const PAC_MODULE_DESCRIPTOR = {
  key: 'pac',
  routes: PAC_ROUTES,
  permissionMap: PAC_PERMISSION_MAP,
  featureFlags: PAC_FEATURE_FLAGS,
  menu: PAC_MENU_METADATA,
} as const;
