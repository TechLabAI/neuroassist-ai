import { Injectable, signal } from '@angular/core';

// =============================================================================
// @neuroassist/shared/ui-feedback — avisos passageiros (toasts).
//
// POR QUE ISTO PRECISOU EXISTIR
//
// A biblioteca era um stub de uma linha (`UI_FEEDBACK_VERSION = '0.0.0'`), e o
// efeito não era cosmético: o EDGE injeta `EDGE_TOAST_TOKEN` com
// `{optional:true}` e NINGUÉM o providenciava, então `this.toast?.show(...)`
// resolvia para `undefined?.show()` — silêncio. Uma cuidadora que perdesse a
// permissão de gerenciar dispositivo clicava, a chamada tomava 403, e a tela não
// dizia nada. O mesmo vale para o `PacFeedbackService`, cujo sinal de toast
// nenhum componente renderiza.
//
// Um aviso que não aparece é pior que aviso nenhum: o código *parece* tratar o
// erro, então ninguém procura o defeito.
// =============================================================================

/** O que um aviso pode ser. Governa urgência visual E anúncio no leitor de tela. */
export type TipoDeAviso = 'info' | 'sucesso' | 'erro';

/** Ação opcional oferecida junto ao aviso — tipicamente "Tentar novamente". */
export interface AcaoDeAviso {
  readonly label: string;
  readonly callback: () => void;
}

export interface Aviso {
  readonly id: number;
  readonly mensagem: string;
  readonly tipo: TipoDeAviso;
  readonly acao: AcaoDeAviso | null;
}

/** Quanto tempo um aviso sem ação permanece na tela. */
const DURACAO_MS: Record<TipoDeAviso, number> = {
  // Erro fica mais tempo: quem está lendo uma má notícia lê mais devevagar, e
  // reler é comum.
  erro: 10_000,
  sucesso: 4_000,
  info: 6_000,
};

/** Quantos avisos convivem na tela. */
const MAXIMO_VISIVEL = 3;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _avisos = signal<readonly Aviso[]>([]);
  private proximoId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  /** Os avisos na tela, do mais antigo ao mais novo. */
  readonly avisos = this._avisos.asReadonly();

  /**
   * Mostra um aviso.
   *
   * <p><b>A mensagem é `string` de propósito, e não `unknown`.</b> Foi passando
   * um `HttpErrorResponse` por um caminho que esperava texto que a tela de
   * dispositivo passou a exibir `[object Object]` para a cuidadora. Quem chama é
   * que traduz o erro em frase; esta camada não adivinha.
   *
   * <p><b>Um aviso COM ação não desaparece sozinho.</b> Oferecer "Tentar
   * novamente" e retirar o botão enquanto a pessoa move o ponteiro até ele é uma
   * armadilha — e é pior para quem navega por teclado ou leitor de tela, que
   * leva mais tempo até lá.
   */
  show(mensagem: string, acao?: AcaoDeAviso, tipo: TipoDeAviso = 'erro'): number {
    const id = this.proximoId++;
    const aviso: Aviso = { id, mensagem, tipo, acao: acao ?? null };

    this._avisos.update((atuais) => {
      const proximos = [...atuais, aviso];
      // Estourou o teto: descarta os mais ANTIGOS. O aviso recém-chegado é o que
      // se refere ao que a pessoa acabou de fazer.
      const excedente = proximos.length - MAXIMO_VISIVEL;
      if (excedente > 0) {
        proximos.splice(0, excedente).forEach((v) => this.cancelarTimer(v.id));
      }
      return proximos;
    });

    if (!aviso.acao) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), DURACAO_MS[tipo]),
      );
    }
    return id;
  }

  /** Atalhos de leitura — o tipo não fica solto no ponto de chamada. */
  sucesso(mensagem: string): number {
    return this.show(mensagem, undefined, 'sucesso');
  }

  info(mensagem: string): number {
    return this.show(mensagem, undefined, 'info');
  }

  /** Retira um aviso. Idempotente. */
  dismiss(id: number): void {
    this.cancelarTimer(id);
    this._avisos.update((atuais) => atuais.filter((v) => v.id !== id));
  }

  /** Limpa tudo — usado ao sair da sessão. */
  limpar(): void {
    this.timers.forEach((t) => clearTimeout(t));
    this.timers.clear();
    this._avisos.set([]);
  }

  private cancelarTimer(id: number): void {
    const t = this.timers.get(id);
    if (t !== undefined) {
      clearTimeout(t);
      this.timers.delete(id);
    }
  }
}
