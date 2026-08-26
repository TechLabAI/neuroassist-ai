// =============================================================================
// acs.descriptor.spec.ts — FE-ACS-020 / FE-ACS-001
//
// Tests: MODULE_PERMISSION_MAP values, MODULE_FEATURE_FLAGS values,
// ACS_MODULE_DESCRIPTOR shape and key.
// =============================================================================

import {
  ACS_MODULE_DESCRIPTOR,
  MODULE_PERMISSION_MAP,
  MODULE_FEATURE_FLAGS,
} from './acs.descriptor';

describe('MODULE_PERMISSION_MAP — FE-ACS-001', () => {
  it("'acs.cuidador.write' maps to 'acs.cuidador.criar'", () => {
    expect(MODULE_PERMISSION_MAP['acs.cuidador.write']).toBe('acs.cuidador.criar');
  });

  it("'acs.cuidador.deactivate' maps to 'acs.cuidador.desativar'", () => {
    expect(MODULE_PERMISSION_MAP['acs.cuidador.deactivate']).toBe('acs.cuidador.desativar');
  });

  // Estes dois casos fixavam o LADO ERRADO da divergência: afirmavam
  // 'acs.vinculo.criar' e 'acs.cuidador.listar', nomes que o acs-service nunca
  // emitiu. Enquanto os guards não consultavam o mapa, o teste ficava verde
  // guardando valores que não existiam em lugar nenhum — um catálogo fixado
  // inline contendo a própria divergência que deveria detectar.
  //
  // Os valores abaixo estão conferidos no RoleAuthorityPolicy do acs-service:
  //   - vincular cuidadora a paciente virou competência do PAC (fluxo-e-perfis
  //     §6, "as duas portas do vínculo"), logo `pac.paciente.vincular`;
  //   - a listagem de cuidadores é `acs.cuidador.read`, e não `.listar`.
  it("'acs.vinculo.write' maps to 'pac.paciente.vincular'", () => {
    expect(MODULE_PERMISSION_MAP['acs.vinculo.write']).toBe('pac.paciente.vincular');
  });

  it("'acs.cuidador.read' maps to 'acs.cuidador.read'", () => {
    expect(MODULE_PERMISSION_MAP['acs.cuidador.read']).toBe('acs.cuidador.read');
  });

  // 'tenantWrite' é a entrada que faz o passo 1 do assistente NEGAR criar
  // empresa a papel humano. O valor à direita está conferido em
  // `TenantController:45` (@PreAuthorize) e no `RoleAuthorityPolicyTest:32` do
  // acs-service, que o lista entre as authorities do cliente de serviço.
  it("'tenantWrite' maps to 'acs.tenant.provisionar'", () => {
    expect(MODULE_PERMISSION_MAP.tenantWrite).toBe('acs.tenant.provisionar');
  });

  // CONTROLE NEGATIVO do mapa: nenhuma authority de tenant pode aparecer como
  // VALOR de outra chave. Se alguém reintroduzir 'acs.tenant.provisionar' numa
  // chave que a coordenadora possui, o botão Criar volta a habilitar para ela
  // sem que nenhum outro teste perceba.
  it('nenhuma outra chave traduz para uma authority de tenant', () => {
    const outras = Object.entries(MODULE_PERMISSION_MAP).filter(([k]) => k !== 'tenantWrite');
    expect(outras.map(([, v]) => v).filter((v) => v.startsWith('acs.tenant.'))).toEqual([]);
  });

  it('has exactly 5 entries', () => {
    expect(Object.keys(MODULE_PERMISSION_MAP)).toHaveLength(5);
  });
});

describe('MODULE_FEATURE_FLAGS — FE-ACS-001', () => {
  it("'acs.coverageGuard' defaults to 'on' (ADR-005 enforcement enabled)", () => {
    expect(MODULE_FEATURE_FLAGS['acs.coverageGuard']).toBe('on');
  });

  it("'acs.inviteByEmail' defaults to 'off' (invite flow not yet implemented)", () => {
    expect(MODULE_FEATURE_FLAGS['acs.inviteByEmail']).toBe('off');
  });

  it("'acs.showSemCoberturaBanner' defaults to 'on'", () => {
    expect(MODULE_FEATURE_FLAGS['acs.showSemCoberturaBanner']).toBe('on');
  });

  it('has exactly 3 entries', () => {
    expect(Object.keys(MODULE_FEATURE_FLAGS)).toHaveLength(3);
  });
});

describe('ACS_MODULE_DESCRIPTOR — FE-ACS-001', () => {
  it("key === 'acs'", () => {
    expect(ACS_MODULE_DESCRIPTOR.key).toBe('acs');
  });

  it('permissionMap matches MODULE_PERMISSION_MAP', () => {
    expect(ACS_MODULE_DESCRIPTOR.permissionMap).toEqual(MODULE_PERMISSION_MAP);
  });

  it('featureFlags matches MODULE_FEATURE_FLAGS', () => {
    expect(ACS_MODULE_DESCRIPTOR.featureFlags).toEqual(MODULE_FEATURE_FLAGS);
  });

  it("permissions.view === 'acs.cuidador.read'", () => {
    expect(ACS_MODULE_DESCRIPTOR.permissions.view).toBe('acs.cuidador.read');
  });

  it("permissions.write === 'acs.cuidador.write'", () => {
    expect(ACS_MODULE_DESCRIPTOR.permissions.write).toBe('acs.cuidador.write');
  });

  it("permissions.linkWrite === 'acs.vinculo.write'", () => {
    expect(ACS_MODULE_DESCRIPTOR.permissions.linkWrite).toBe('acs.vinculo.write');
  });

  // A chave de TELA precisa existir no mapa: quem consulta a decisão de acesso
  // passa este valor, e `app.config.ts` NEGA chave desconhecida. Um descuido
  // aqui não abriria a porta — trancaria em silêncio, que é igualmente ruim de
  // diagnosticar.
  it("permissions.tenantWrite é uma chave presente no permissionMap", () => {
    const chave = ACS_MODULE_DESCRIPTOR.permissions.tenantWrite;
    expect(Object.keys(MODULE_PERMISSION_MAP)).toContain(chave);
  });

  it('routes is a non-empty array (ACS_ROUTES)', () => {
    expect(Array.isArray(ACS_MODULE_DESCRIPTOR.routes)).toBe(true);
    expect(ACS_MODULE_DESCRIPTOR.routes.length).toBeGreaterThan(0);
  });

  it('menu.route === /acs', () => {
    expect(ACS_MODULE_DESCRIPTOR.menu.route).toBe('/acs');
  });
});
