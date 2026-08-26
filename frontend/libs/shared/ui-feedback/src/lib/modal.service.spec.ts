import { TestBed } from '@angular/core/testing';
import { ModalService } from './modal.service';

describe('ModalService', () => {
  let modais: ModalService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    modais = TestBed.inject(ModalService);
  });

  it('começa fechado', () => {
    expect(modais.modal()).toBeNull();
  });

  it('abrir guarda mensagem, título e ação', () => {
    const callback = jest.fn();
    modais.abrir({
      mensagem: 'O comando mudou desde que você abriu.',
      titulo: 'Conflito de versão',
      acao: { label: 'Recarregar', callback },
    });

    expect(modais.modal()?.titulo).toBe('Conflito de versão');
    expect(modais.modal()?.acao?.label).toBe('Recarregar');
    expect(modais.modal()?.bloqueante).toBe(false);
  });

  it('não é bloqueante por padrão', () => {
    // Bloquear é exceção, e tem de ser pedido: um modal que prende por descuido
    // é pior que um que fecha por descuido.
    modais.abrir({ mensagem: 'x' });
    expect(modais.modal()?.bloqueante).toBe(false);
  });

  it('o portão de emergência abre bloqueante', () => {
    modais.abrir({ mensagem: 'Este gesto não é detectável.', bloqueante: true });
    expect(modais.modal()?.bloqueante).toBe(true);
  });

  it('fechar limpa, e é idempotente', () => {
    modais.abrir({ mensagem: 'x' });
    modais.fechar();
    modais.fechar();
    expect(modais.modal()).toBeNull();
  });
});
