import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { CommandEditorComponent } from './command-editor.component';
import { PacComandoFacade, ComandoRowVM } from '../pac-comando.facade';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { TiposGestoReadFacade } from '../tipos-gesto-read.facade';
import { PAC_COMANDO_PORT, Comando, ComandoEstado } from '../api/comando.port';
import { PAC_PACIENTE_PORT } from '../api/paciente.port';
import { TIPOS_GESTO_READ_PORT } from '../api/tipos-gesto-read.port';
import { EdgeDeviceAckStubAdapter } from '../api/edge-device-ack-stub.adapter';
import { PAC_ACCESS_DECISION, PacAccessDecision } from '../access/pac-access-tokens';
import { ModalService } from '@neuroassist/shared/ui-feedback';

// =============================================================================
// CommandEditorComponent — ciclo de vida do comando (ADR-006 / SPEC-03 / SPEC-05)
//
// O QUE ESTES TESTES AFIRMAM, E POR QUE NESTA ORDEM
//
// 1. A AFORDÂNCIA. Antes desta mudança o editor tinha dois botões — "Salvar
//    comando" e "Cancelar". O ciclo de vida estava contratado no porto,
//    implementado no adaptador e inalcançável pela pessoa que usa o produto: um
//    comando nascia 'drafted' e não havia tela que o levasse adiante. Por isso a
//    primeira coisa verificada é que os quatro botões EXISTEM.
//
// 2. A TABELA DE ESTADO do SPEC-03, célula a célula. Habilitar é metade da
//    afirmação; a outra metade — o que tem de ficar DESABILITADO em cada estado —
//    é a que impede que "ligar o botão" vire "ligar todos os botões".
//
// 3. O PAR DE PERMISSÃO. Para cada afordância há dois testes: quem pode a vê
//    habilitada, quem não pode a vê desabilitada. Um teste só do lado que passa
//    documenta a ausência da guarda como se fosse a especificação dela — foi
//    assim que o `pacAuthGuard` chegou devolvendo `true` incondicionalmente.
//
// 4. A CONFIRMAÇÃO. Publicar emergência e aposentar não podem acontecer no
//    primeiro clique. O teste que importa não é "abriu o modal": é que o facade
//    NÃO foi chamado enquanto ninguém confirmou.
// =============================================================================

const TIPOS = [
  { id: 'tg-sobrancelha', nome: 'Levantar sobrancelha' },
  { id: 'tg-piscar-duplo', nome: 'Piscar duas vezes' },
];

function buildComando(overrides?: Partial<Comando>): Comando {
  return {
    id: 'c1',
    pacienteId: 'p1',
    rotulo: 'Ajuda',
    urgencia: 'rotina',
    primitivoPrincipal: 'tg-sobrancelha',
    primitivoSecundario: null,
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    estado: 'drafted',
    etag: 'etag-v1',
    ...overrides,
  };
}

function buildRowVM(overrides?: Partial<ComandoRowVM>): ComandoRowVM {
  return {
    id: 'c1',
    rotulo: 'Ajuda',
    urgencia: 'rotina',
    primitivoPrincipalNome: 'Levantar sobrancelha',
    primitivoSecundarioNome: null,
    primitivoPrincipalId: 'tg-sobrancelha',
    primitivoSecundarioId: null,
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    sensibilidade: 'media',
    estado: 'drafted',
    syncState: 'pending-sync',
    testedState: 'not-tested',
    etag: 'etag-v1',
    ...overrides,
  };
}

/** Decisão de acesso que concede tudo, menos as chaves negadas. */
function acessoMenos(...negadas: string[]): PacAccessDecision {
  return { can: (chave: string) => !negadas.includes(chave) };
}

const ROTULOS = ['Validar', 'Publicar', 'Recalibrar', 'Aposentar'] as const;
type Rotulo = (typeof ROTULOS)[number];

describe('CommandEditorComponent — ciclo de vida', () => {
  let fixture: ComponentFixture<CommandEditorComponent>;
  let facade: PacComandoFacade;
  let modais: ModalService;
  let porto: {
    list: jest.Mock;
    get: jest.Mock;
    create: jest.Mock;
    updateDraft: jest.Mock;
    validate: jest.Mock;
    publish: jest.Mock;
    recalibrate: jest.Mock;
    retire: jest.Mock;
  };

  async function montar(acesso: PacAccessDecision | null = acessoMenos()): Promise<void> {
    porto = {
      list: jest.fn().mockReturnValue(of([])),
      get: jest.fn().mockReturnValue(of(buildComando())),
      create: jest.fn().mockReturnValue(of({ body: buildComando() })),
      updateDraft: jest.fn().mockReturnValue(of(buildComando())),
      validate: jest.fn().mockReturnValue(of(buildComando({ estado: 'validated' }))),
      publish: jest.fn().mockReturnValue(of(buildComando({ estado: 'published' }))),
      recalibrate: jest.fn().mockReturnValue(of(buildComando({ estado: 'published' }))),
      retire: jest.fn().mockReturnValue(of(buildComando({ estado: 'retired' }))),
    };

    TestBed.configureTestingModule({
      imports: [CommandEditorComponent],
      providers: [
        provideRouter([]),
        PacComandoFacade,
        PacPacienteFacade,
        TiposGestoReadFacade,
        EdgeDeviceAckStubAdapter,
        { provide: PAC_COMANDO_PORT, useValue: porto },
        { provide: PAC_PACIENTE_PORT, useValue: { list: jest.fn().mockReturnValue(of([])) } },
        { provide: TIPOS_GESTO_READ_PORT, useValue: { list: jest.fn().mockReturnValue(of(TIPOS)) } },
        { provide: PAC_ACCESS_DECISION, useValue: acesso },
        // Sem `comandoId` na rota o componente entra em modo "novo comando" e
        // não arma o efeito de abertura reativa — o estado do editor passa a ser
        // decidido só pelo `openEdit()` explícito de cada teste.
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
      ],
    });

    // `compileComponents()` é o padrão da casa (ver sync-status-banner.component.spec.ts):
    // sem ele, neste ambiente ts-jest JIT, os `imports` do componente standalone
    // não são resolvidos e cada filho vira elemento desconhecido (NG0303).
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(CommandEditorComponent);
    facade = TestBed.inject(PacComandoFacade);
    modais = TestBed.inject(ModalService);
    fixture.detectChanges();
  }

  /** Abre o editor num estado do ciclo de vida e redesenha. */
  function abrirCom(estado: ComandoEstado, extra?: Partial<ComandoRowVM>): void {
    facade.openEdit('p1', buildRowVM({ estado, ...extra }));
    fixture.detectChanges();
  }

  /** O <button> interno do pac-ds-button cujo rótulo visível é `rotulo`. */
  function botao(rotulo: Rotulo): HTMLButtonElement {
    const hosts = Array.from(
      fixture.nativeElement.querySelectorAll('pac-ds-button'),
    ) as HTMLElement[];
    const host = hosts.find((h) => (h.textContent ?? '').trim() === rotulo);
    if (!host) {
      throw new Error(
        `Botão "${rotulo}" não existe no editor. Rótulos presentes: ` +
          hosts.map((h) => `"${(h.textContent ?? '').trim()}"`).join(', '),
      );
    }
    return host.querySelector('button') as HTMLButtonElement;
  }

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // 1. A afordância existe
  // -------------------------------------------------------------------------

  describe('afordância', () => {
    it.each(ROTULOS)('o editor oferece o botão "%s"', async (rotulo) => {
      await montar();
      abrirCom('drafted');

      expect(botao(rotulo)).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // 2. Tabela de estado — SPEC-03 §"lifecycle actions"
  //
  //   estado     | Validar | Publicar | Recalibrar | Aposentar
  //   drafted    |   sim   |    —     |     —      |    sim
  //   validated  |    —    |   sim    |     —      |    sim
  //   published  |    —    |    —     |    sim     |    sim
  //   retired    |    —    |    —     |     —      |     —
  //
  // POR QUE A ASSERÇÃO É NO SINAL E NÃO NO ATRIBUTO `disabled` DO DOM
  //
  // `PacDsButtonComponent` declara `disabled` como `input()` — entrada baseada
  // em inicializador. O ambiente ts-jest deste repositório compila em JIT, e o
  // compilador JIT não enxerga entradas assim: a ligação `[disabled]` vira
  // NG0303 e o <button> renderiza sempre habilitado. A limitação já está
  // documentada em `ui/command-row.component.spec.ts:13-16`, e é do AMBIENTE DE
  // TESTE, não do produto — os quatro cenários e2e (`apps/pac-e2e/`) rodam sobre
  // o build AOT real, e é lá que `toBeDisabled()` é verificado de verdade.
  //
  // Afirmar aqui `botao(x).disabled === true` seria afirmar o que o ambiente
  // garante por acidente e não por mérito: passaria mesmo que o template ligasse
  // o botão errado. O que dá para verificar sem mentir é a DECISÃO — os sinais
  // que o template consome —, e é isso que está abaixo.
  // -------------------------------------------------------------------------

  /** Os quatro sinais de habilitação que o template consome. */
  function habilitacao(): Record<Rotulo, boolean> {
    const c = fixture.componentInstance;
    return {
      Validar: c.podeValidar(),
      Publicar: c.podePublicar(),
      Recalibrar: c.podeRecalibrar(),
      Aposentar: c.podeAposentar(),
    };
  }

  const NENHUMA: Record<Rotulo, boolean> = {
    Validar: false,
    Publicar: false,
    Recalibrar: false,
    Aposentar: false,
  };

  describe('habilitação por estado', () => {
    const TABELA: ReadonlyArray<[ComandoEstado, Record<Rotulo, boolean>]> = [
      ['drafted', { Validar: true, Publicar: false, Recalibrar: false, Aposentar: true }],
      ['validated', { Validar: false, Publicar: true, Recalibrar: false, Aposentar: true }],
      ['published', { Validar: false, Publicar: false, Recalibrar: true, Aposentar: true }],
      ['retired', NENHUMA],
    ];

    for (const [estado, esperado] of TABELA) {
      for (const rotulo of ROTULOS) {
        const habilitado = esperado[rotulo];
        it(`${estado}: "${rotulo}" ${habilitado ? 'habilitado' : 'desabilitado'}`, async () => {
          await montar();
          abrirCom(estado);

          expect(habilitacao()[rotulo]).toBe(habilitado);
        });
      }
    }

    it('comando novo, ainda não gravado, não oferece nenhuma transição', async () => {
      await montar(); // openNew() no ngOnInit — estado é null

      expect(habilitacao()).toEqual(NENHUMA);
    });

    it('enquanto uma transição está no ar, as quatro ficam desabilitadas', async () => {
      await montar();
      abrirCom('drafted');
      // Observable que nunca emite: o editor permanece em saveState 'saving'.
      porto.validate.mockReturnValue(new Observable<Comando>(() => undefined));

      botao('Validar').click();
      fixture.detectChanges();

      expect(habilitacao()).toEqual(NENHUMA);
    });
  });

  // -------------------------------------------------------------------------
  // 3. Defesa em profundidade: nem o clique que escapa produz a transição
  //
  // O botão desabilitado é a primeira barreira, e é a que o e2e verifica. Aqui
  // vale a segunda: se o clique CHEGAR ao manipulador — porque o [disabled] saiu
  // do template, porque o ambiente não o aplicou (é o caso deste JIT), ou porque
  // alguém chamou o método direto —, o facade recusa a transição que não parte
  // do estado certo. Duas barreiras independentes, uma verificada em cada camada.
  // -------------------------------------------------------------------------

  describe('clique que escapa da desabilitação', () => {
    it('publicar em drafted não chega ao porto', async () => {
      await montar();
      abrirCom('drafted');

      botao('Publicar').click();

      expect(porto.publish).not.toHaveBeenCalled();
    });

    it('validar em published não chega ao porto', async () => {
      await montar();
      abrirCom('published');

      botao('Validar').click();

      expect(porto.validate).not.toHaveBeenCalled();
    });

    it('nenhuma das quatro ações age sobre um comando aposentado', async () => {
      await montar();
      abrirCom('retired');

      for (const rotulo of ROTULOS) {
        botao(rotulo).click();
        // Aposentar e publicar-emergência passam por confirmação; confirmar é o
        // caminho mais permissivo, e nem por ele a transição pode acontecer.
        modais.modal()?.acao?.callback();
      }
      fixture.detectChanges();

      expect(porto.validate).not.toHaveBeenCalled();
      expect(porto.publish).not.toHaveBeenCalled();
      expect(porto.recalibrate).not.toHaveBeenCalled();
      expect(porto.retire).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // 4. Permissão — o PAR, para cada afordância
  //
  // SPEC-05: "WHILE the caller lacks the comando publish permission, THE SYSTEM
  // SHALL disable the validar publicar recalibrar and retirar lifecycle actions."
  // -------------------------------------------------------------------------

  describe('permissão', () => {
    const ESTADO_QUE_HABILITA: Record<Rotulo, ComandoEstado> = {
      Validar: 'drafted',
      Publicar: 'validated',
      Recalibrar: 'published',
      Aposentar: 'published',
    };

    it.each(ROTULOS)('quem tem permissão vê "%s" habilitado no estado dele', async (rotulo) => {
      await montar(acessoMenos());
      abrirCom(ESTADO_QUE_HABILITA[rotulo]);

      expect(habilitacao()[rotulo]).toBe(true);
    });

    it.each(ROTULOS)(
      'sem a permissão de publicar, "%s" fica desabilitado mesmo no estado dele',
      async (rotulo) => {
        await montar(acessoMenos('commandPublish'));
        abrirCom(ESTADO_QUE_HABILITA[rotulo]);

        expect(habilitacao()[rotulo]).toBe(false);
      },
    );

    it('sem a permissão de recalibrar: Recalibrar desabilitado, Aposentar segue habilitado', async () => {
      await montar(acessoMenos('commandRecalibrate'));
      abrirCom('published');

      expect(habilitacao().Recalibrar).toBe(false);
      expect(habilitacao().Aposentar).toBe(true);
    });

    it('sem a permissão de aposentar: Aposentar desabilitado, Recalibrar segue habilitado', async () => {
      await montar(acessoMenos('commandRetire'));
      abrirCom('published');

      expect(habilitacao().Aposentar).toBe(false);
      expect(habilitacao().Recalibrar).toBe(true);
    });

    it('a tela avisa quem não pode gerir o ciclo, em vez de só apagar os botões', async () => {
      await montar(acessoMenos('commandPublish'));
      abrirCom('drafted');

      expect(fixture.nativeElement.textContent).toContain(
        'Seu perfil não permite mudar o estado de um comando',
      );
    });
  });

  // -------------------------------------------------------------------------
  // 5. Confirmação
  // -------------------------------------------------------------------------

  describe('confirmação', () => {
    it('publicar rotina vai direto, sem modal', async () => {
      await montar();
      abrirCom('validated', { urgencia: 'rotina' });

      botao('Publicar').click();

      expect(modais.modal()).toBeNull();
      expect(porto.publish).toHaveBeenCalledTimes(1);
    });

    it('publicar emergência abre confirmação e NÃO publica antes dela', async () => {
      await montar();
      abrirCom('validated', { urgencia: 'emergencia' });

      botao('Publicar').click();

      expect(modais.modal()).not.toBeNull();
      expect(porto.publish).not.toHaveBeenCalled();
    });

    it('publicar emergência só chama o porto depois de confirmado', async () => {
      await montar();
      abrirCom('validated', { urgencia: 'emergencia' });

      botao('Publicar').click();
      modais.modal()!.acao!.callback();

      expect(porto.publish).toHaveBeenCalledTimes(1);
    });

    it('aposentar abre confirmação e NÃO aposenta antes dela', async () => {
      await montar();
      abrirCom('published');

      botao('Aposentar').click();

      expect(modais.modal()).not.toBeNull();
      expect(porto.retire).not.toHaveBeenCalled();
    });

    it('aposentar só chama o porto depois de confirmado', async () => {
      await montar();
      abrirCom('published');

      botao('Aposentar').click();
      modais.modal()!.acao!.callback();

      expect(porto.retire).toHaveBeenCalledTimes(1);
    });

    // A confirmação PRECISA poder dizer não — a afirmação original, e a certa.
    //
    // A primeira versão garantia isso com `bloqueante: false`, deixando o Esc e o
    // clique fora servirem de cancelar. Era o que dava para fazer: o modal só
    // sabia desenhar UM botão, e um bloqueante de botão único é uma confirmação
    // sem saída a não ser aceitar.
    //
    // O `ModalService` ganhou `confirmar()`, com as duas saídas explícitas. A
    // afirmação fica mais forte: bloqueante (uma decisão não se desfaz por um
    // clique distraído fora do quadro) E com recusa declarada.
    it.each([
      ['Publicar' as const, 'validated' as ComandoEstado, 'emergencia' as const],
      ['Aposentar' as const, 'published' as ComandoEstado, 'rotina' as const],
    ])('a confirmação de "%s" bloqueia E oferece recusa', async (rotulo, estado, urgencia) => {
      await montar();
      abrirCom(estado, { urgencia });

      botao(rotulo).click();

      expect(modais.modal()!.bloqueante).toBe(true);
      expect(modais.modal()!.acao).not.toBeNull();
      expect(modais.modal()!.cancelar).not.toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // 6. Portão de emergência no publicar (SPEC-07)
  //
  // "IF the backend responds emergency-not-detectable on publicar, THEN THE
  // SYSTEM SHALL block the publish with a blocking modal and leave the emergency
  // comando validated." Até agora o gate só era exercido no `create` — que é
  // onde o backend NÃO o aplica.
  // -------------------------------------------------------------------------

  describe('portão de emergência no publicar', () => {
    it('422 emergency-not-detectable abre o modal bloqueante do editor', async () => {
      await montar();
      abrirCom('validated', { urgencia: 'emergencia' });
      porto.publish.mockReturnValue(
        throwError(() => ({
          status: 422,
          error: {
            type: 'https://neuroassist.ai/problems/emergency-not-detectable',
            status: 422,
          },
        })),
      );

      botao('Publicar').click();
      modais.modal()!.acao!.callback();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(facade.editor().emergencyGateError).not.toBeNull();
      // O comando permanece validado — a publicação foi recusada, não parcial.
      expect(facade.editor().estado).toBe('validated');
      expect(fixture.nativeElement.textContent).toContain('Gesto não detectável');
    });
  });
});
