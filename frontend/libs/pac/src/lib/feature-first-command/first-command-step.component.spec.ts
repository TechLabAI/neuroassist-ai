import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { FirstCommandStepComponent } from './first-command-step.component';
import { PacComandoFacade } from '../pac-comando.facade';
import { TiposGestoReadFacade } from '../tipos-gesto-read.facade';
import { PAC_ACCESS_DECISION } from '../access/pac-access-tokens';
import { signal } from '@angular/core';

// =============================================================================
// FirstCommandStepComponent — o passo deixou de APONTAR e passou a FAZER.
//
// Era um `<a routerLink="/pac">` escrito no assistente: interrompia o fluxo para
// mandar a pessoa a outro lugar, de onde não há volta guiada. Estes testes
// seguram as três coisas que fazem dele um passo de verdade:
//
//   1. ele CRIA o comando (chama a facade), em vez de navegar;
//   2. sem paciente no contexto ele diz isso, em vez de desenhar um formulário
//      que falharia na escrita;
//   3. sem a authority de escrita ele bloqueia ANTES do clique.
//
// Cada uma com o seu controle negativo.
// =============================================================================

const TIPOS = [
  { id: 'tipo-piscar', nome: 'Piscar' },
  { id: 'tipo-sobrancelha', nome: 'Sobrancelha' },
];

describe('FirstCommandStepComponent', () => {
  let fixture: ComponentFixture<FirstCommandStepComponent>;
  let comp: FirstCommandStepComponent;

  const editorSignal = signal({
    pid: '', cid: null, rotulo: '', urgencia: 'rotina', primitivoPrincipal: '',
    primitivoSecundario: null, sensibilidade: 'media', tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500, estado: null, etag: '', dirty: false,
    saveState: 'idle' as string, emergencyGateError: null,
  });
  const catalogSignal = signal<unknown[]>([]);
  const tiposSignal = signal(TIPOS);

  const comandoFacade = {
    editor: editorSignal.asReadonly(),
    catalog: catalogSignal.asReadonly(),
    error: signal<string | null>(null).asReadonly(),
    load: jest.fn(),
    openNew: jest.fn(),
    patchEditor: jest.fn(),
    save: jest.fn(),
  };

  const tiposFacade = {
    tipos: tiposSignal.asReadonly(),
    firstTipoId: signal('tipo-piscar').asReadonly(),
    load: jest.fn(),
  };

  const decisao = { can: jest.fn().mockReturnValue(true) };

  async function montar(pacienteId: string | null): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [FirstCommandStepComponent],
      providers: [
        { provide: PacComandoFacade, useValue: comandoFacade },
        { provide: TiposGestoReadFacade, useValue: tiposFacade },
        { provide: PAC_ACCESS_DECISION, useValue: decisao },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FirstCommandStepComponent);
    comp = fixture.componentInstance;
    fixture.componentRef.setInput('pacienteHint', pacienteId);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function achar(testid: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testid}"]`);
  }
  function campoRotulo(): HTMLInputElement {
    return fixture.nativeElement.querySelector('pac-ds-input input') as HTMLInputElement;
  }
  /**
   * Preenche o rotulo pelo ESTADO, e nao pelo DOM.
   *
   * <p>Neste ambiente (ts-jest JIT) as entradas por `input()`/`model()` dos
   * componentes de biblioteca do PAC nao sao reconhecidas pelo template de quem
   * os usa — `[value]` e `[disabled]` caem em NG0303 e nao sao ligados (a mesma
   * nota esta em `command-editor.component.spec.ts`). Digitar no <input> interno
   * do `pac-ds-input` nao chega ao componente, e afirmar sobre o `disabled` do
   * botao mediria o ambiente, nao o codigo. O que continua honesto e observavel:
   * o estado, a chamada a facade e QUAL ramo foi renderizado.
   */
  function digitarRotulo(texto: string): void {
    comp.rotulo.set(texto);
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    editorSignal.update((e) => ({ ...e, saveState: 'idle' }));
    catalogSignal.set([]);
    tiposSignal.set(TIPOS);
    decisao.can.mockReturnValue(true);
  });

  // ---------------------------------------------------------------------------
  // 1. O passo FAZ a coisa
  // ---------------------------------------------------------------------------
  it('cria o comando pela facade — não navega para lugar nenhum', async () => {
    await montar('pac-marcio');
    digitarRotulo('Água');

    comp.criar();

    expect(comandoFacade.openNew).toHaveBeenCalledWith('pac-marcio', 'tipo-piscar');
    expect(comandoFacade.patchEditor).toHaveBeenCalledWith({ rotulo: 'Água' });
    expect(comandoFacade.save).toHaveBeenCalledTimes(1);
  });

  // CONTROLE NEGATIVO: sem rótulo não há escrita. Um botão que sempre chama a
  // facade passaria no teste acima e reprovaria aqui.
  it('sem rótulo, o passo não escreve nada', async () => {
    await montar('pac-marcio');
    expect(comp.podeSalvar()).toBe(false);
    comp.criar();
    expect(comandoFacade.save).not.toHaveBeenCalled();
  });

  it('carrega o catálogo do paciente do contexto ao montar', async () => {
    await montar('pac-marcio');
    expect(comandoFacade.load).toHaveBeenCalledWith('pac-marcio');
    expect(tiposFacade.load).toHaveBeenCalled();
  });

  // ---------------------------------------------------------------------------
  // 2. O contexto manda
  // ---------------------------------------------------------------------------
  it('sem paciente no contexto, diz o que falta em vez de desenhar o formulário', async () => {
    await montar(null);
    expect(achar('primeiro-comando-sem-paciente')).not.toBeNull();
    expect(campoRotulo()).toBeNull();
    expect(comandoFacade.load).not.toHaveBeenCalled();
  });

  // CONTROLE NEGATIVO do item acima: com paciente, o formulário aparece.
  it('com paciente no contexto, o formulário aparece', async () => {
    await montar('pac-marcio');
    expect(achar('primeiro-comando-sem-paciente')).toBeNull();
    expect(campoRotulo()).not.toBeNull();
  });

  it('catálogo já com comando: o passo se declara cumprido e não repete o formulário', async () => {
    catalogSignal.set([{ id: 'cmd-1' }]);
    await montar('pac-marcio');
    expect(achar('primeiro-comando-pronto')).not.toBeNull();
    expect(campoRotulo()).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // 3. Permissão
  // ---------------------------------------------------------------------------
  // A CHAVE DE TELA, e não a authority: quem traduz 'commandWrite' para
  // 'pac.comando-authoring.criar' é o provedor em app.config.ts. É a convenção
  // do PAC — e importar o mapa aqui criava um ciclo de import que desligava o
  // `[disabled]` do botão (NG0303) sem ninguém perceber.
  it('consulta a decisão de acesso pela chave de tela de escrita de comando', async () => {
    await montar('pac-marcio');
    expect(decisao.can).toHaveBeenCalledWith('commandWrite');
  });

  it('SEM a authority, a escrita fica bloqueada e a tela diz por quê', async () => {
    decisao.can.mockReturnValue(false);
    await montar('pac-marcio');
    digitarRotulo('Água');

    expect(comp.podeSalvar()).toBe(false);
    comp.criar();
    expect(comandoFacade.save).not.toHaveBeenCalled();
    expect(achar('primeiro-comando-negado')).not.toBeNull();
  });

  // CONTROLE NEGATIVO da permissão: com a authority, e só com ela, o botão
  // habilita. Sem este par, um botão permanentemente desabilitado por qualquer
  // outro motivo passaria pelo teste acima.
  it('COM a authority e rótulo preenchido, a escrita libera', async () => {
    await montar('pac-marcio');
    digitarRotulo('Água');
    expect(comp.podeSalvar()).toBe(true);
    expect(achar('primeiro-comando-negado')).toBeNull();
  });

  it('sem decisão de acesso provida, NEGA', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [FirstCommandStepComponent],
      providers: [
        { provide: PacComandoFacade, useValue: comandoFacade },
        { provide: TiposGestoReadFacade, useValue: tiposFacade },
        { provide: PAC_ACCESS_DECISION, useValue: null },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(FirstCommandStepComponent);
    f.componentRef.setInput('pacienteHint', 'pac-marcio');
    f.detectChanges();
    expect(f.componentInstance.podeEscrever()).toBe(false);
    expect(
      (f.nativeElement as HTMLElement).querySelector('[data-testid="primeiro-comando-negado"]'),
    ).not.toBeNull();
  });

  // ---------------------------------------------------------------------------
  // 4. O que a tela diz depois da escrita
  // ---------------------------------------------------------------------------
  it('confirma o comando como RASCUNHO pendente de sincronização, não como ativo', async () => {
    await montar('pac-marcio');
    editorSignal.update((e) => ({ ...e, saveState: 'saved-pending-sync' }));
    fixture.detectChanges();

    const confirmacao = achar('primeiro-comando-salvo');
    expect(confirmacao).not.toBeNull();
    expect(confirmacao!.textContent).toMatch(/rascunho/i);
    expect(confirmacao!.textContent).not.toMatch(/ativo no dispositivo/i);
  });

  it('erro de escrita aparece na própria superfície', async () => {
    await montar('pac-marcio');
    editorSignal.update((e) => ({ ...e, saveState: 'error' }));
    fixture.detectChanges();
    expect(achar('primeiro-comando-erro')).not.toBeNull();
    expect(achar('primeiro-comando-salvo')).toBeNull();
  });
});
