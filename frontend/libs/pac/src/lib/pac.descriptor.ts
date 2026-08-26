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

// Chave de tela → authority que o acs-service REALMENTE emite. Conferido no
// `RoleAuthorityPolicy` e numa resposta real de `POST /oauth2/token`.
//
// Quatro das cinco entradas citavam nomes que não existem em lugar nenhum:
// 'pac.paciente.write', 'pac.comando.read', 'pac.comando.write' e
// 'pac.comando.publish'. Foram escritas pelo formato que parecia razoável
// (<recurso>.<verbo em inglês>), e o backend usa outro (<recurso>-<aspecto>.<verbo
// em português>). Nada acusava, porque nenhum guard do PAC chegava a comparar:
// todos devolvem `true` incondicionalmente.
//
// 'write' de paciente é CRIAR: a tela que consome esta chave é a de cadastro. A
// cuidadora tem 'pac.paciente.editar' e não tem 'criar' — é justamente a
// distinção que foi decidida (só a coordenadora cadastra paciente).
export const PAC_PERMISSION_MAP = {
  read: 'pac.paciente.read',
  write: 'pac.paciente.criar',
  commandRead: 'pac.comando-lifecycle.read',
  commandWrite: 'pac.comando-authoring.criar',
  commandPublish: 'pac.comando-lifecycle.publicar',
  // Faltavam as duas: os botões de recalibrar e aposentar não tinham chave de
  // tela, e a regra do SPEC-05 §"lifecycle actions" não tinha como ser aplicada
  // a elas. Nomes conferidos em arch/decisions/permission-catalogue.arch.json —
  // não são o formato <recurso>.<verbo em inglês> que parece razoável.
  commandRecalibrate: 'pac.comando-lifecycle.recalibrar',
  commandRetire: 'pac.comando-lifecycle.aposentar',
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
