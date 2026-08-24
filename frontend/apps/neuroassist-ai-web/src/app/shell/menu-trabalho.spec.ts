import { construirMenu, rotuloDoPapel, type SessaoMenu } from './menu-trabalho';
import type { Papel } from '@neuroassist/shared/auth-session';

// =============================================================================
// O menu por perfil — testado contra as authorities REAIS do ACS.
//
// As três listas abaixo não foram escritas de memória nem deduzidas do papel:
// foram tiradas do corpo de `POST http://localhost:8088/oauth2/token` para
// coord@ / cuidadora@ / apoio@neuroassist.dev, e conferem literalmente com
// backend/.../acs/application/service/RoleAuthorityPolicy.java.
//
// Por que isso importa: um teste que inventasse as authorities passaria
// exatamente quando o menu estivesse errado — seria o menu conferido contra a
// suposição que o produziu, e não contra o que o backend concede.
//
// E por que os testes afirmam QUAIS itens, e não quantos: contar itens é o
// clássico "guarda que não guarda nada" desta casa. Um filtro trocado que
// mostrasse Equipe à cuidadora e escondesse Alarmes manteria a contagem e
// passaria. Cada caso abaixo compara a lista inteira, em ordem.
// =============================================================================

const AUTHORITIES_COORDENADORA = [
  'ROLE_CAREGIVER',
  'acs.cuidador.criar',
  'acs.cuidador.read',
  'acs.cuidador.desativar',
  'acs.directory.read',
  'pac.paciente.vincular',
  'pac.paciente.criar',
  'pac.paciente.read',
  'pac.paciente.editar',
  'pac.paciente.deletar',
  'pac.catalogo-ativo.read',
  'pac.comando-authoring.criar',
  'pac.comando-authoring.editar',
  'pac.comando-lifecycle.read',
  'pac.comando-lifecycle.publicar',
  'pac.comando-lifecycle.aposentar',
  'pac.comando-lifecycle.recalibrar',
  'gest.tipo-gesto.read',
  'gest.tipo-gesto.criar',
  'gest.tipo-gesto.editar',
  'gest.detection-profile.read',
  'gest.detection-profile.publicar',
  'gest.detection-contract.read',
  'gest.detection-contract.validar',
  'gest.eligibility.read',
  'edge.read',
  'edge.manage',
  'evnt.event-query.read',
  'evnt.event-confirmation.confirmar',
  'dash.panel-composition.read',
  'hist:read',
  'hist:read:all',
];

const AUTHORITIES_CUIDADORA = [
  'ROLE_CAREGIVER',
  'acs.cuidador.read',
  'acs.directory.read',
  'pac.paciente.read',
  'pac.paciente.editar',
  'pac.catalogo-ativo.read',
  'pac.comando-authoring.criar',
  'pac.comando-authoring.editar',
  'pac.comando-lifecycle.read',
  'pac.comando-lifecycle.publicar',
  'pac.comando-lifecycle.aposentar',
  'pac.comando-lifecycle.recalibrar',
  'gest.tipo-gesto.read',
  'gest.detection-profile.read',
  'gest.detection-contract.read',
  'gest.detection-contract.validar',
  'gest.eligibility.read',
  'edge.read',
  'evnt.event-query.read',
  'evnt.event-confirmation.confirmar',
  'dash.panel-composition.read',
  'hist:read',
];

const AUTHORITIES_APOIO = [
  'acs.directory.read',
  'dash.panel-composition.read',
  'hist:read',
  'evnt.event-confirmation.confirmar',
];

function sessao(papel: Papel, authorities: readonly string[]): SessaoMenu {
  return { papel, pode: (a) => authorities.includes(a) };
}

const ids = (s: SessaoMenu | null) => construirMenu(s).map((i) => i.id);
const labels = (s: SessaoMenu | null) => construirMenu(s).map((i) => i.label);

describe('construirMenu — cuidadora', () => {
  const s = sessao('CUIDADORA', AUTHORITIES_CUIDADORA);

  it('vê exatamente o dia a dia da §4, nesta ordem', () => {
    expect(ids(s)).toEqual(['painel', 'alarmes', 'comandos', 'historico']);
  });

  it('usa nomes de trabalho, não nomes de módulo', () => {
    expect(labels(s)).toEqual([
      'Meus pacientes',
      'Alarmes',
      'Comandos do paciente',
      'Histórico',
    ]);
    // A lista antiga — a que espelhava a arquitetura.
    expect(labels(s)).not.toContain('Status');
    expect(labels(s)).not.toContain('Eventos');
    expect(labels(s)).not.toContain('Calibração');
  });

  // Cada um destes é um item que a §4 nega à cuidadora. Um por asserção, para
  // que o teste diga QUAL vazou quando quebrar.
  it.each([
    ['equipe', 'a equipe da clínica'],
    ['pacientes-sem-cuidadora', 'os pacientes descobertos'],
    ['dispositivos', 'o parque de dispositivos'],
    ['deteccao', 'a configuração de detecção'],
    ['onboarding', 'a configuração inicial'],
  ])('não vê %s (%s)', (id) => {
    expect(ids(s)).not.toContain(id);
  });

  it('vê "Histórico" e não "Histórico completo" — não tem hist:read:all', () => {
    expect(labels(s)).toContain('Histórico');
    expect(labels(s)).not.toContain('Histórico completo');
  });
});

describe('construirMenu — coordenadora', () => {
  const s = sessao('COORDENADOR_CLINICA', AUTHORITIES_COORDENADORA);

  it('vê o dia a dia e mais a clínica, nesta ordem', () => {
    expect(ids(s)).toEqual([
      'painel',
      'alarmes',
      'comandos',
      'historico',
      'equipe',
      'pacientes-sem-cuidadora',
      'dispositivos',
      'deteccao',
      'onboarding',
    ]);
  });

  it('vê "Histórico completo" — tem hist:read:all', () => {
    expect(labels(s)).toContain('Histórico completo');
    expect(labels(s)).not.toContain('Histórico');
  });

  // Os itens sem tela própria têm de dizer isso na estrutura, e não só num
  // comentário: é o que faz a barra desenhar a marca "em breve" e não prometer
  // um destino. Se alguém construir a tela e esquecer de tirar a marca, ou
  // marcar como pendente um item que já entrega, este teste reprova.
  it.each([
    ['pacientes-sem-cuidadora', true],
    ['deteccao', true],
    ['painel', undefined],
    ['alarmes', undefined],
    ['comandos', undefined],
    ['historico', undefined],
    ['equipe', undefined],
    ['dispositivos', undefined],
    ['onboarding', undefined],
  ])('%s tem pendente=%s', (id, esperado) => {
    const item = construirMenu(s).find((i) => i.id === id);
    expect(item).toBeDefined();
    expect(item?.pendente).toBe(esperado);
  });

  it('nenhum item aponta para rota vazia ou fora do mapa de rotas do app', () => {
    // Rotas que app.routes.ts realmente registra. Um item de menu que leve a
    // uma rota inexistente cai no `**` e devolve a home em silêncio — parece
    // funcionar e não funciona.
    const ROTAS_REAIS = ['/', '/eventos', '/pac', '/historico', '/acs', '/edge/device', '/gest', '/onboarding'];
    for (const item of construirMenu(s)) {
      expect(ROTAS_REAIS).toContain(item.route);
    }
  });
});

describe('construirMenu — apoio ao paciente', () => {
  const s = sessao('PACIENTE_APOIO', AUTHORITIES_APOIO);

  it('vê o painel, o alarme e o histórico — e nada mais', () => {
    expect(ids(s)).toEqual(['painel', 'alarmes', 'historico']);
  });

  it('enxerga o alarme apesar de não ter evnt.event-query.read', () => {
    // É o último degrau do escalonamento (ADR-011): recebe o pedido quando mais
    // ninguém recebeu, e `evnt.event-confirmation.confirmar` é a única saída de
    // um alarme de ajuda. Sem este item o pedido ficaria preso na fila.
    expect(AUTHORITIES_APOIO).not.toContain('evnt.event-query.read');
    expect(ids(s)).toContain('alarmes');
  });

  it('não vê nenhuma ação de configuração', () => {
    for (const id of ['comandos', 'equipe', 'dispositivos', 'deteccao', 'onboarding']) {
      expect(ids(s)).not.toContain(id);
    }
  });
});

describe('construirMenu — sessão ausente', () => {
  it('não devolve item algum', () => {
    expect(construirMenu(null)).toEqual([]);
  });

  it('não vaza item de coordenadora com authorities vazias', () => {
    const anonimo: SessaoMenu = { papel: null, pode: () => false };
    expect(construirMenu(anonimo)).toEqual([]);
  });

  it('uma sessão que responde "sim" a tudo é o oposto do caso vazio', () => {
    // Guarda contra o filtro invertido: se `exige` fosse ignorado, o caso
    // anônimo acima passaria por acidente (lista vazia) — este caso não.
    const onipotente: SessaoMenu = { papel: null, pode: () => true };
    expect(construirMenu(onipotente).length).toBeGreaterThan(
      construirMenu(sessao('CUIDADORA', AUTHORITIES_CUIDADORA)).length,
    );
  });
});

describe('rotuloDoPapel', () => {
  it('nomeia os três papéis em português', () => {
    expect(rotuloDoPapel('COORDENADOR_CLINICA')).toBe('Coordenadora');
    expect(rotuloDoPapel('CUIDADORA')).toBe('Cuidadora');
    expect(rotuloDoPapel('PACIENTE_APOIO')).toBe('Apoio ao paciente');
  });

  it('sem papel, não inventa um', () => {
    expect(rotuloDoPapel(null)).toBe('');
  });
});
