import { Injectable, signal } from '@angular/core';

// =============================================================================
// Faixas (banners) — aviso que PERMANECE na tela.
//
// Diferença para o toast, e por que são coisas separadas: o toast é passageiro e
// fala de algo que acabou de acontecer; a faixa descreve uma CONDIÇÃO que
// continua valendo ("o catálogo está desatualizado", "esta tela está em modo
// leitura"). Some quando a condição muda, não quando o tempo passa.
// =============================================================================

export interface Faixa {
  readonly mensagem: string;
  /** Só informa; visual mais discreto. */
  readonly consultiva: boolean;
  /** Pode ser fechada pela pessoa. */
  readonly dispensavel: boolean;
}

@Injectable({ providedIn: 'root' })
export class BannerService {
  private readonly _faixa = signal<Faixa | null>(null);

  /** A faixa em cartaz, ou null. */
  readonly faixa = this._faixa.asReadonly();

  /**
   * Põe uma faixa. Há UMA de cada vez, e a nova substitui a anterior.
   *
   * <p>Empilhar faixas empurraria o conteúdo da página para baixo sem limite; e
   * duas condições simultâneas quase sempre têm a mesma causa.
   */
  mostrar(
    mensagem: string,
    opcoes: { consultiva?: boolean; dispensavel?: boolean } = {},
  ): void {
    this._faixa.set({
      mensagem,
      consultiva: opcoes.consultiva ?? false,
      // Uma faixa NÃO consultiva é, por padrão, indispensável: ela descreve algo
      // que continua verdadeiro, e fechá-la não muda o fato.
      dispensavel: opcoes.dispensavel ?? false,
    });
  }

  /** Retira a faixa. Idempotente. */
  dispensar(): void {
    this._faixa.set(null);
  }
}
