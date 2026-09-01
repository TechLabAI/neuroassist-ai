import { TREI_ROUTES } from './trei.routes';
import { TREI_NAV_CONFIG } from './trei-nav-config';

// =============================================================================
// TREI_MODULE_DESCRIPTOR — FE-TREI-006 (tool-contract / SPEC-05)
// Barrel export from @neuroassist/trei.
// The shell reads this to register TREI without importing internals.
// =============================================================================

export const TREI_MODULE_PERMISSION_MAP = {
  'treinamento.conduzir':         'treinamento.sessao.conduzir',
  'treinamento.propor':           'treinamento.no.propor',
  'treinamento.aprovar.cuidadora': 'treinamento.no.aprovar.cuidadora',
  'treinamento.descartar':        'treinamento.no.descartar',
} as const;

export const TREI_MODULE_FEATURE_FLAGS = {
  /** Kill-switch: 'on' = module registered; 'off' = no menu item + no routes (SPEC-05). */
  'treinamento.enabled': 'on' as 'on' | 'off',
  /** Streaming conversation: 'off' = request/response turns; 'on' = SSE (ADR-005). */
  'treinamento.streaming': 'off' as 'on' | 'off',
  /** Voice input for caregiver: deferred (vision doc §5). */
  'treinamento.voiceInput': 'off' as 'on' | 'off',
} as const;

export const TREI_MODULE_DESCRIPTOR = {
  key: 'trei',
  routes: TREI_ROUTES,
  menu: TREI_NAV_CONFIG,
  permissionMap: TREI_MODULE_PERMISSION_MAP,
  featureFlags: TREI_MODULE_FEATURE_FLAGS,
  permissions: {
    view: 'treinamento.conduzir',
    propose: 'treinamento.propor',
    approve: 'treinamento.aprovar.cuidadora',
    discard: 'treinamento.descartar',
  },
} as const;
