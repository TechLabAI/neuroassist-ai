import { TestBed } from '@angular/core/testing';
import { BannerService } from './banner.service';

describe('BannerService', () => {
  let banners: BannerService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    banners = TestBed.inject(BannerService);
  });

  it('começa sem faixa', () => {
    expect(banners.faixa()).toBeNull();
  });

  it('mostrar põe a faixa', () => {
    banners.mostrar('O catálogo está desatualizado.');
    expect(banners.faixa()?.mensagem).toBe('O catálogo está desatualizado.');
  });

  it('há UMA faixa: a nova substitui a anterior', () => {
    // Empilhar empurraria o conteúdo da página para baixo sem limite.
    banners.mostrar('a');
    banners.mostrar('b');
    expect(banners.faixa()?.mensagem).toBe('b');
  });

  it('faixa não consultiva é indispensável por padrão', () => {
    // Ela descreve algo que continua verdadeiro; fechá-la não muda o fato.
    banners.mostrar('Esta tela está em modo leitura.');
    expect(banners.faixa()?.dispensavel).toBe(false);
    expect(banners.faixa()?.consultiva).toBe(false);
  });

  it('consultiva e dispensável quando pedido', () => {
    banners.mostrar('x', { consultiva: true, dispensavel: true });
    expect(banners.faixa()?.consultiva).toBe(true);
    expect(banners.faixa()?.dispensavel).toBe(true);
  });

  it('dispensar limpa, e é idempotente', () => {
    banners.mostrar('a');
    banners.dispensar();
    banners.dispensar();
    expect(banners.faixa()).toBeNull();
  });
});
