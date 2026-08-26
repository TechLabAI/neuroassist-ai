import type { Papel } from '@neuroassist/shared/auth-session';

// =============================================================================
// menu-trabalho — o menu espelha O TRABALHO, não os módulos técnicos.
//
// Até aqui a barra lateral listava `gest`, `eventos`, `pac`, `edge`, `acs`,
// `historico` — os nomes dos módulos da arquitetura — e era idêntica para todo
// mundo. Ninguém que cuida de um paciente pensa "vou ao módulo GEST".
// A especificação deste arquivo é a §4 de docs/operacao/fluxo-e-perfis.md.
//
// -----------------------------------------------------------------------------
// Por que filtrar por AUTHORITY e não por PAPEL
// -----------------------------------------------------------------------------
// O papel é um rótulo; a authority é a permissão que o backend de fato exige.
// O mapa papel→authorities vive no ACS (RoleAuthorityPolicy) e já mudou duas
// vezes sem o frontend saber — a cuidadora perdeu `pac.paciente.criar`, nasceu
// `pac.paciente.vincular`, o apoio ganhou `evnt.event-confirmation.confirmar`.
// Um menu escrito contra o papel teria continuado a oferecer telas que passaram
// a devolver 403, e um item que leva a um 403 é pior que um item ausente.
// Escrito contra a authority, o menu acompanha a mudança sozinho.
//
// -----------------------------------------------------------------------------
// O que este arquivo NÃO é
// -----------------------------------------------------------------------------
// Não é segurança. Esconder um item é ergonomia — poupa a pessoa de bater numa
// porta trancada. Quem tranca a porta é o guard da rota e, sobretudo, o backend,
// que valida o token por conta própria em cada chamada. Alterar o que este
// arquivo devolve não concede acesso a nada.
// =============================================================================

/** Onde o item aparece na barra. */
export type GrupoMenu = 'trabalho' | 'clinica' | 'config';

/** Um destino do menu, já resolvido para uma sessão concreta. */
export interface ItemMenu {
  /** Identidade estável — é por ela que os testes afirmam quem vê o quê. */
  readonly id: string;
  readonly label: string;
  readonly icon: string;
  readonly route: string;
  readonly order: number;
  readonly grupo: GrupoMenu;
  /**
   * O item existe no menu mas ainda não tem tela própria: aponta para a
   * superfície existente mais próxima. Renderizado com marca visível para não
   * prometer um destino que não entrega.
   */
  readonly pendente?: boolean;
}

/** O mínimo que o menu precisa saber sobre quem está usando o sistema. */
export interface SessaoMenu {
  /**
   * A válvula de escape para o que authority nenhuma expressa.
   *
   * <p>Hoje nenhum item do catálogo a usa — e isso é o resultado desejado, não
   * um descuido: toda distinção da §4 coube em authority. Está aqui para que
   * quem precisar de uma distinção genuinamente de papel não seja empurrado a
   * inventar uma authority que o backend não exige.
   */
  readonly papel: Papel | null;
  /** `AuthSessionStore.pode` — comparação literal de authority. */
  readonly pode: (authority: string) => boolean;
}

interface EntradaCatalogo extends Omit<ItemMenu, 'label'> {
  readonly label: string;
  /** Quem pode ver este item, em termos das authorities do ACS. */
  readonly exige: (pode: (a: string) => boolean) => boolean;
  /** Rótulo mais forte quando a pessoa tem alcance maior (ex.: histórico da clínica inteira). */
  readonly labelAmpliado?: { readonly authority: string; readonly label: string };
}

// -----------------------------------------------------------------------------
// Por que rótulo, ícone e rota vivem AQUI e não nos `*-nav-config.ts`
// -----------------------------------------------------------------------------
// Cada módulo publica o seu `*-nav-config.ts`, e eles foram atualizados junto
// com este arquivo para falar a mesma língua (DASH "Meus pacientes", EVNT
// "Alarmes", ACS "Equipe"). Mas a composição do menu é decisão do shell:
//
//   - o menu não é a união dos módulos. Um item — "Pacientes sem cuidadora" —
//     não pertence a módulo nenhum, e dois módulos (EDGE, GEST) são
//     `showInNav:false` de propósito, porque foram concebidos para se compor
//     dentro de outras telas e ganharam porta de entrada só para a coordenadora;
//   - a ordem é o fluxo de trabalho atravessando módulos, não uma soma de
//     `order` que cada lib escolheu sem ver os outros;
//   - importar os barris dos módulos aqui puxaria o lib inteiro para dentro do
//     shell só para ler quatro strings — e hoje o barril do EVNT nem carrega
//     fora do bundler: `evnt.descriptor → routes → guard → providers → bootstrap
//     → facade → feature-flags → evnt.descriptor` é um ciclo (ver relatório).
//
// Os `*-nav-config.ts` seguem sendo a declaração de cada módulo sobre si mesmo
// (descriptor, breadcrumb). Quem muda um rótulo aqui deve mudar lá também.
const ROTA_PAINEL = '/';
const ROTA_ALARMES = '/eventos';
const ROTA_COMANDOS = '/pac';
const ROTA_HISTORICO = '/historico';
const ROTA_EQUIPE = '/acs';
const ROTA_DISPOSITIVOS = '/edge/device';
const ROTA_DETECCAO = '/gest';
const ROTA_ONBOARDING = '/onboarding';

const CATALOGO: readonly EntradaCatalogo[] = [
  // --- O dia a dia de quem cuida -------------------------------------------
  {
    id: 'painel',
    label: 'Meus pacientes',
    icon: 'patient_list',
    route: ROTA_PAINEL,
    order: 0,
    grupo: 'trabalho',
    // O painel é composto pelo DASH; sem esta authority ele não carrega nada.
    exige: (pode) => pode('dash.panel-composition.read'),
  },
  {
    id: 'alarmes',
    label: 'Alarmes',
    icon: 'notifications_active',
    route: ROTA_ALARMES,
    order: 10,
    grupo: 'trabalho',
    // Duas authorities distintas, e o OU é deliberado: o apoio ao paciente é o
    // ÚLTIMO degrau do escalonamento (ADR-011) e só tem `confirmar`. Se o menu
    // exigisse `event-query.read`, quem recebe o alarme quando mais ninguém
    // recebeu não teria por onde chegar até ele.
    exige: (pode) =>
      pode('evnt.event-query.read') || pode('evnt.event-confirmation.confirmar'),
  },
  {
    id: 'comandos',
    // O descriptor do PAC chama isto de "Calibração" — o nome da operação
    // técnica. Quem cuida vem aqui ver e recalibrar gesto → necessidade do SEU
    // paciente, e é assim que a §4 nomeia o item.
    label: 'Comandos do paciente',
    icon: 'record_voice_over',
    route: ROTA_COMANDOS,
    order: 20,
    grupo: 'trabalho',
    exige: (pode) => pode('pac.catalogo-ativo.read'),
  },
  {
    id: 'historico',
    label: 'Histórico',
    icon: 'history',
    route: ROTA_HISTORICO,
    order: 30,
    grupo: 'trabalho',
    exige: (pode) => pode('hist:read'),
    // A §4 pede "Histórico completo" para a coordenadora. É a MESMA tela: o que
    // muda é o alcance, e quem decide o alcance é o backend a partir de
    // `hist:read:all`. Um segundo item para a mesma rota fingiria dois destinos.
    labelAmpliado: { authority: 'hist:read:all', label: 'Histórico completo' },
  },

  // --- A clínica: só de quem responde por ela ------------------------------
  {
    id: 'equipe',
    label: 'Equipe',
    icon: 'diversity_3',
    route: ROTA_EQUIPE,
    order: 40,
    grupo: 'clinica',
    // `acs.cuidador.read` NÃO serve aqui: a cuidadora também a tem (usa-a para
    // ver colegas dentro de outras telas), e a §4 diz que ela não vê Equipe. O
    // que define o item é gerir a equipe — criar e desativar —, não lê-la.
    exige: (pode) => pode('acs.cuidador.criar'),
  },
  {
    id: 'pacientes-sem-cuidadora',
    label: 'Pacientes sem cuidadora',
    icon: 'person_alert',
    // PENDENTE: aponta para o roster do ACS, que é a única tela existente que
    // hoje mostra cobertura. A tela própria consumiria
    // GET /api/pac/v1/pacientes/sem-cobertura — o endpoint já existe.
    route: ROTA_EQUIPE,
    order: 50,
    grupo: 'clinica',
    pendente: true,
    // Um paciente descoberto é INVISÍVEL ao painel: o painel lista pacientes
    // pela cuidadora, e quem não tem vínculo não está na lista de ninguém. Quem
    // resolve isso é quem pode vincular — e só a coordenadora vincula.
    exige: (pode) => pode('pac.paciente.vincular'),
  },
  {
    id: 'dispositivos',
    label: 'Dispositivos',
    icon: 'videocam',
    // `/edge/device` mostra UM dispositivo — o do paciente em contexto —, não o
    // parque da clínica que a §4 descreve; o pareamento vive em `/edge/pairing`.
    // É a superfície de dispositivo que existe, e entrega saúde e drift.
    route: ROTA_DISPOSITIVOS,
    order: 60,
    grupo: 'clinica',
    // `edge.read` não serve: a cuidadora a tem para ver o estado do dispositivo
    // do paciente dela dentro do painel. O parque de dispositivos da clínica —
    // parear, saúde, drift — é `edge.manage`.
    exige: (pode) => pode('edge.manage'),
  },
  {
    id: 'deteccao',
    label: 'Configuração de detecção',
    icon: 'tune',
    // PENDENTE: `/gest` hoje é o host de desenvolvimento (GestDevHostComponent,
    // um harness de teste ao vivo protegido por `gestDeviceOnlineGuard`), não a
    // tela de tipos de gesto e publicação do perfil que a §4 descreve. O item
    // existe porque a capacidade é da coordenadora e precisa de porta; a tela
    // própria ainda não foi construída.
    route: ROTA_DETECCAO,
    order: 70,
    grupo: 'clinica',
    pendente: true,
    // Tipos de gesto e publicação do perfil da clínica. `gest.tipo-gesto.read` e
    // `detection-profile.read` a cuidadora tem (lê o perfil ao calibrar); o que
    // separa é publicar o perfil, que muda a detecção de todo mundo.
    exige: (pode) => pode('gest.detection-profile.publicar'),
  },

  // --- Configuração inicial: uso único, do condutor técnico -----------------
  {
    id: 'onboarding',
    label: 'Configuração inicial',
    icon: 'settings',
    route: ROTA_ONBOARDING,
    order: 100,
    grupo: 'config',
    // Cadastrar cuidadoras e pacientes é operação de coordenadora (§7).
    exige: (pode) => pode('acs.cuidador.criar'),
  },
];

/**
 * Monta o menu para uma sessão.
 *
 * <p><b>Sessão ausente devolve lista vazia, de propósito.</b> Enquanto ninguém
 * entrou não há authorities, e "sem authority" tem de significar "nenhum item" —
 * não "os itens básicos". Qualquer default diferente de vazio vaza estrutura da
 * clínica para uma aba recém-aberta e, pior, dá ao próximo leitor deste arquivo
 * a impressão de que existe um menu anônimo a manter.
 */
export function construirMenu(sessao: SessaoMenu | null): ItemMenu[] {
  if (sessao === null) return [];
  return CATALOGO.filter((e) => e.exige(sessao.pode))
    .map(({ exige: _exige, labelAmpliado, ...item }) => ({
      ...item,
      label:
        labelAmpliado && sessao.pode(labelAmpliado.authority)
          ? labelAmpliado.label
          : item.label,
    }))
    .sort((a, b) => a.order - b.order);
}

/** Como o papel do token se chama para quem lê a tela. */
export function rotuloDoPapel(papel: Papel | null): string {
  switch (papel) {
    case 'COORDENADOR_CLINICA':
      return 'Coordenadora';
    case 'CUIDADORA':
      return 'Cuidadora';
    case 'PACIENTE_APOIO':
      return 'Apoio ao paciente';
    default:
      return '';
  }
}
