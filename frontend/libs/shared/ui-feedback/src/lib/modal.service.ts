import { Injectable, signal } from '@angular/core';

// =============================================================================
// Modais — quando o sistema precisa de uma DECISÃO antes de seguir.
//
// SOBRE O MODAL BLOQUEANTE, QUE É O CASO DELICADO
//
// O PAC usa um modal bloqueante quando alguém tenta publicar um comando de
// EMERGÊNCIA com um gesto que o motor de visão não consegue detectar (ADR-005 /
// SPEC-07 §4). É seguro por construção: a publicação já foi RECUSADA quando o
// modal aparece.
//
// `bloqueante` significa "não fecha por acidente" — nem com Esc, nem clicando
// fora. NÃO significa "não fecha nunca": um modal sem saída prende a pessoa
// diante do formulário que ela precisa corrigir, e a única saída vira recarregar
// a página, o que perde o que ela escreveu. A segurança está na recusa, não em
// tornar a caixa inescapável.
//
// Por isso todo modal tem ao menos um botão. Quando quem chama não oferece um, o
// componente põe "Entendi" — que fecha e devolve o foco a quem abriu.
// =============================================================================

export interface AcaoDeModal {
  readonly label: string;
  readonly callback: () => void;
}

export interface Modal {
  readonly titulo: string | null;
  readonly mensagem: string;
  /** Não fecha por Esc nem por clique fora. Ver a nota acima. */
  readonly bloqueante: boolean;
  readonly acao: AcaoDeModal | null;
  /**
   * Rótulo do botão que RECUSA — presente quando o modal é uma confirmação.
   *
   * <p>Sem isto, um modal bloqueante com uma única ação era uma "confirmação"
   * incapaz de dizer não: sem Esc, sem clique fora, e o único caminho para fora
   * da caixa era o botão que confirma. Quem só queria olhar ficava sem saída a
   * não ser aceitar.
   *
   * <p>Quando ausente, o modal é um AVISO (o portão de emergência, por exemplo):
   * não há decisão a tomar, e o botão único apenas fecha.
   */
  readonly cancelar: string | null;
}

@Injectable({ providedIn: 'root' })
export class ModalService {
  private readonly _modal = signal<Modal | null>(null);

  /** O modal aberto, ou null. */
  readonly modal = this._modal.asReadonly();

  abrir(opcoes: {
    mensagem: string;
    titulo?: string | null;
    bloqueante?: boolean;
    acao?: AcaoDeModal;
    cancelar?: string;
  }): void {
    this._modal.set({
      titulo: opcoes.titulo ?? null,
      mensagem: opcoes.mensagem,
      bloqueante: opcoes.bloqueante ?? false,
      acao: opcoes.acao ?? null,
      cancelar: opcoes.cancelar ?? null,
    });
  }

  /**
   * Atalho para CONFIRMAÇÃO: duas saídas, e a recusa é a saída barata.
   *
   * <p>É bloqueante de propósito — uma decisão não deve ser desfeita por um
   * clique fora do quadro — e por isso mesmo precisa do botão de recusar, que
   * este atalho garante que exista. Foi a lacuna que fez uma frente evitar
   * `bloqueante: true` nas confirmações de publicar e aposentar.
   */
  confirmar(opcoes: {
    mensagem: string;
    titulo?: string;
    rotuloConfirmar: string;
    aoConfirmar: () => void;
    rotuloCancelar?: string;
  }): void {
    this.abrir({
      mensagem: opcoes.mensagem,
      titulo: opcoes.titulo ?? null,
      bloqueante: true,
      acao: { label: opcoes.rotuloConfirmar, callback: opcoes.aoConfirmar },
      cancelar: opcoes.rotuloCancelar ?? 'Cancelar',
    });
  }

  /** Fecha. Idempotente. */
  fechar(): void {
    this._modal.set(null);
  }
}
