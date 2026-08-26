import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalOutletComponent } from './modal-outlet.component';
import { ModalService } from './modal.service';

// O que se testa aqui é o que quebra em silêncio: sem tratamento de foco um
// modal continua parecendo um modal e deixa de ser um para quem usa teclado.

describe('ModalOutletComponent', () => {
  let fixture: ComponentFixture<ModalOutletComponent>;
  let modais: ModalService;
  let botaoDeOrigem: HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ModalOutletComponent] }).compileComponents();
    modais = TestBed.inject(ModalService);
    fixture = TestBed.createComponent(ModalOutletComponent);

    // Quem abriu o modal. O foco tem de voltar para cá ao fechar.
    botaoDeOrigem = document.createElement('button');
    botaoDeOrigem.textContent = 'Publicar';
    document.body.appendChild(botaoDeOrigem);
    botaoDeOrigem.focus();

    fixture.detectChanges();
  });

  afterEach(() => {
    modais.fechar();
    botaoDeOrigem.remove();
  });

  function caixa(): HTMLElement | null {
    return fixture.nativeElement.querySelector('[data-testid="modal"]');
  }

  it('não desenha nada quando não há modal', () => {
    expect(caixa()).toBeNull();
  });

  it('desenha a mensagem quando abre', () => {
    modais.abrir({ mensagem: 'O comando mudou.' });
    fixture.detectChanges();

    expect(caixa()?.textContent).toContain('O comando mudou.');
  });

  // ── foco ──────────────────────────────────────────────────────────────────

  it('move o foco para dentro ao abrir', () => {
    // Sem isto, quem usa teclado segue navegando a página atrás, que está inerte.
    modais.abrir({ mensagem: 'x' });
    fixture.detectChanges();
    fixture.detectChanges();

    expect(caixa()?.contains(document.activeElement)).toBe(true);
  });

  it('devolve o foco a quem abriu ao fechar', () => {
    // Jogar o foco para o começo da página faria perder o lugar no formulário
    // que se está corrigindo.
    modais.abrir({ mensagem: 'x' });
    fixture.detectChanges();
    // Sem esta verificação o teste passaria por VACUIDADE: se o foco nunca sai
    // do botão de origem, ele "volta" para onde já estava e a asserção final
    // acerta sem que nada tenha sido devolvido. Foi exatamente o que aconteceu
    // enquanto a entrada de foco estava quebrada.
    expect(document.activeElement).not.toBe(botaoDeOrigem);

    modais.fechar();
    fixture.detectChanges();

    expect(document.activeElement).toBe(botaoDeOrigem);
  });

  // ── bloqueante ────────────────────────────────────────────────────────────

  it('Esc fecha um modal comum', () => {
    modais.abrir({ mensagem: 'x' });
    fixture.detectChanges();

    caixa()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(modais.modal()).toBeNull();
  });

  it('Esc NÃO fecha o modal bloqueante', () => {
    // Portão de emergência: fechar por acidente esconderia a recusa.
    modais.abrir({ mensagem: 'Gesto não detectável.', bloqueante: true });
    fixture.detectChanges();

    caixa()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(modais.modal()).not.toBeNull();
  });

  it('clique fora fecha o comum e não fecha o bloqueante', () => {
    const fundo = () => fixture.nativeElement.querySelector('.fixed.inset-0') as HTMLElement;

    modais.abrir({ mensagem: 'x' });
    fixture.detectChanges();
    fundo().click();
    fixture.detectChanges();
    expect(modais.modal()).toBeNull();

    modais.abrir({ mensagem: 'y', bloqueante: true });
    fixture.detectChanges();
    fundo().click();
    fixture.detectChanges();
    expect(modais.modal()).not.toBeNull();
  });

  it('o bloqueante É fechável pelo botão — bloquear não é prender', () => {
    // Um modal sem saída deixa a pessoa diante do formulário que precisa
    // corrigir sem poder tocá-lo; a única saída seria recarregar, perdendo o que
    // ela escreveu. A segurança está na RECUSA, que já aconteceu.
    modais.abrir({ mensagem: 'Gesto não detectável.', bloqueante: true });
    fixture.detectChanges();

    const acao = fixture.nativeElement.querySelector('[data-testid="modal-acao"]') as HTMLButtonElement;
    expect(acao.textContent?.trim()).toBe('Entendi');
    acao.click();
    fixture.detectChanges();

    expect(modais.modal()).toBeNull();
  });

  it('Tab circula dentro do diálogo', () => {
    // Sem a armadilha, o Tab sai para a página atrás, que está visualmente
    // inerte: a pessoa perde o modal de vista sem tê-lo fechado.
    modais.abrir({ mensagem: 'x', acao: { label: 'Recarregar', callback: () => undefined } });
    fixture.detectChanges();

    const focaveis = Array.from(
      caixa()!.querySelectorAll<HTMLElement>('button'),
    );
    const ultimo = focaveis[focaveis.length - 1];
    ultimo.focus();

    const evento = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    caixa()!.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(focaveis[0]);
  });

  // ── confirmação: bloquear sem prender ─────────────────────────────────────

  it('confirmar() abre bloqueante E com botão de recusar', () => {
    // Um modal bloqueante com UMA ação era uma confirmação incapaz de dizer não:
    // sem Esc, sem clique fora, e a única saída era o botão que confirma. Quem só
    // queria olhar ficava sem alternativa a não ser aceitar.
    const aoConfirmar = jest.fn();
    modais.confirmar({
      mensagem: 'Publicar este comando de emergência?',
      rotuloConfirmar: 'Publicar',
      aoConfirmar,
    });
    fixture.detectChanges();

    expect(modais.modal()?.bloqueante).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="modal-cancelar"]')).not.toBeNull();
    expect(aoConfirmar).not.toHaveBeenCalled();
  });

  it('recusar fecha sem executar a ação', () => {
    const aoConfirmar = jest.fn();
    modais.confirmar({ mensagem: 'x', rotuloConfirmar: 'Publicar', aoConfirmar });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="modal-cancelar"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(modais.modal()).toBeNull();
    expect(aoConfirmar).not.toHaveBeenCalled();
  });

  it('a recusa recebe o foco, e não a confirmação', () => {
    // Confirmar por engano ao apertar Enter é o erro que custa caro; recusar não
    // custa nada.
    modais.confirmar({ mensagem: 'x', rotuloConfirmar: 'Aposentar', aoConfirmar: () => undefined });
    fixture.detectChanges();

    expect((document.activeElement as HTMLElement)?.dataset['testid']).toBe('modal-cancelar');
  });

  it('aviso sem decisão NÃO ganha botão de recusar', () => {
    // O portão de emergência é aviso, não pergunta: a publicação já foi negada.
    // Oferecer "Cancelar" ali sugeriria uma escolha que não existe.
    modais.abrir({ mensagem: 'Gesto não detectável.', bloqueante: true });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="modal-cancelar"]')).toBeNull();
  });

  it('usa alertdialog quando bloqueante e dialog quando não', () => {
    modais.abrir({ mensagem: 'x' });
    fixture.detectChanges();
    expect(caixa()?.getAttribute('role')).toBe('dialog');

    modais.fechar();
    fixture.detectChanges();
    modais.abrir({ mensagem: 'y', bloqueante: true });
    fixture.detectChanges();
    expect(caixa()?.getAttribute('role')).toBe('alertdialog');
  });

  it('a ação declarada roda depois de fechar', () => {
    const callback = jest.fn();
    modais.abrir({ mensagem: 'x', acao: { label: 'Recarregar', callback } });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="modal-acao"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(callback).toHaveBeenCalledTimes(1);
    expect(modais.modal()).toBeNull();
  });

  it('o título rotula o diálogo', () => {
    modais.abrir({ mensagem: 'x', titulo: 'Conflito de versão' });
    fixture.detectChanges();

    expect(caixa()?.getAttribute('aria-labelledby')).toBe('ui-modal-titulo');
    expect(caixa()?.getAttribute('aria-modal')).toBe('true');
  });
});
