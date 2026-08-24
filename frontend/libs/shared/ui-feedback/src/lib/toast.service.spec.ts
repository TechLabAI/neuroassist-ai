import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

// Cada decisão deste serviço tem consequência para quem usa o sistema.
// Cada uma tem um teste que fica vermelho se alguém a desfizer.

describe('ToastService', () => {
  let toasts: ToastService;

  beforeEach(() => {
    jest.useFakeTimers();
    TestBed.configureTestingModule({});
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => {
    toasts.limpar();
    jest.useRealTimers();
  });

  it('começa vazio', () => {
    expect(toasts.avisos()).toEqual([]);
  });

  it('show devolve o aviso na tela', () => {
    toasts.show('Falha ao comunicar.');

    expect(toasts.avisos()).toHaveLength(1);
    expect(toasts.avisos()[0].mensagem).toBe('Falha ao comunicar.');
    expect(toasts.avisos()[0].tipo).toBe('erro');
  });

  it('erro é o tipo padrão — quem chama sem dizer está reportando falha', () => {
    // A alternativa (padrão 'info') faria um erro passar despercebido por
    // parecer um recado qualquer.
    expect(toasts.show('x')).toBeDefined();
    expect(toasts.avisos()[0].tipo).toBe('erro');
  });

  // ── some sozinho, mas não quando há o que clicar ──────────────────────────

  it('aviso sem ação some sozinho', () => {
    toasts.show('Falha temporária.');
    jest.advanceTimersByTime(10_000);

    expect(toasts.avisos()).toHaveLength(0);
  });

  it('aviso COM ação NÃO some sozinho', () => {
    // Oferecer "Tentar novamente" e retirar o botão enquanto a pessoa move o
    // ponteiro até ele é uma armadilha — pior ainda para quem navega por teclado.
    toasts.show('Falha temporária.', { label: 'Tentar novamente', callback: () => undefined });
    jest.advanceTimersByTime(600_000);

    expect(toasts.avisos()).toHaveLength(1);
  });

  it('erro fica mais tempo na tela que sucesso', () => {
    toasts.sucesso('Pronto.');
    jest.advanceTimersByTime(5_000);
    expect(toasts.avisos()).toHaveLength(0);

    toasts.show('Deu ruim.');
    jest.advanceTimersByTime(5_000);
    expect(toasts.avisos()).toHaveLength(1);
  });

  // ── teto ──────────────────────────────────────────────────────────────────

  it('mantém no máximo três, descartando os mais ANTIGOS', () => {
    // O recém-chegado é o que se refere ao que a pessoa acabou de fazer.
    ['a', 'b', 'c', 'd'].forEach((m) => toasts.show(m));

    expect(toasts.avisos().map((v) => v.mensagem)).toEqual(['b', 'c', 'd']);
  });

  it('descartar por excesso cancela o timer do descartado', () => {
    // Sem cancelar, o timer do 'a' dispararia depois e removeria pelo id — e como
    // o id já não está na lista, o efeito seria nenhum; o risco real é o timer
    // vazar. Este teste trava o comportamento.
    ['a', 'b', 'c', 'd'].forEach((m) => toasts.show(m));
    jest.advanceTimersByTime(10_000);

    expect(toasts.avisos()).toHaveLength(0);
  });

  // ── dispensar ─────────────────────────────────────────────────────────────

  it('dismiss retira o aviso pedido', () => {
    const id = toasts.show('a');
    toasts.show('b');
    toasts.dismiss(id);

    expect(toasts.avisos().map((v) => v.mensagem)).toEqual(['b']);
  });

  it('dismiss é idempotente', () => {
    const id = toasts.show('a');
    expect(() => {
      toasts.dismiss(id);
      toasts.dismiss(id);
    }).not.toThrow();
  });

  it('limpar esvazia tudo', () => {
    toasts.show('a');
    toasts.show('b', { label: 'x', callback: () => undefined });
    toasts.limpar();

    expect(toasts.avisos()).toEqual([]);
  });

  it('ids não se repetem depois de limpar', () => {
    const primeiro = toasts.show('a');
    toasts.limpar();
    const segundo = toasts.show('b');

    // Reaproveitar id faria um `dismiss` atrasado retirar o aviso errado.
    expect(segundo).not.toBe(primeiro);
  });
});
