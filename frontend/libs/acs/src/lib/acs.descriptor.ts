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
//
// CADA VALOR À DIREITA FOI CONFERIDO no `RoleAuthorityPolicy` do acs-service e
// numa resposta real de `POST /oauth2/token`. Duas entradas estavam erradas, e
// erradas de um jeito que nada acusava — porque os guards nem consultavam este
// mapa: comparavam a CHAVE DE TELA com as authorities do token, e chave de tela
// nunca é uma authority. O mapa existia e não era aplicado.
//
//   'acs.cuidador.read' apontava para 'acs.cuidador.listar' — o backend emite
//   'acs.cuidador.read'. A tradução invertia o sentido.
//
//   'acs.vinculo.write' apontava para 'acs.vinculo.criar' — o backend NÃO emite
//   nenhuma authority 'acs.vinculo.*'. Vincular cuidadora a paciente passou a
//   ser competência do PAC quando ficou decidido que só a coordenadora vincula:
//   a authority é 'pac.paciente.vincular'. Apontar para um nome inexistente
//   trancaria a coordenadora fora da tela que ela é a única a poder usar.
export const MODULE_PERMISSION_MAP = {
  'acs.cuidador.write': 'acs.cuidador.criar',
  'acs.cuidador.deactivate': 'acs.cuidador.desativar',
  'acs.vinculo.write': 'pac.paciente.vincular',
  'acs.cuidador.read': 'acs.cuidador.read',
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
