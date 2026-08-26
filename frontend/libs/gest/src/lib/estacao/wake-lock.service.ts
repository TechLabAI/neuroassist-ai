import { Injectable, signal } from '@angular/core';

// =============================================================================
// WakeLockService — impede o desligamento de tela durante a vigília (GEST-FE-031)
//
// Quando a estação está em 'vigiando', solicita um screen Wake Lock para evitar
// que o SO apague a tela e interrompa o requestAnimationFrame da detecção.
//
// Ausência da API (navegadores que não a implementam) é DEGRADAÇÃO DECLARADA,
// não exceção. O serviço expõe `apiIndisponivel` e continua operando — a estação
// roda normalmente, apenas sem a proteção adicional.
//
// O browser libera o lock automaticamente quando a aba fica oculta (visibilitychange).
// O EstacaoComponent deve chamar `reacquire()` ao retornar para 'vigiando'.
//
// Uso pelo EstacaoComponent:
//   onInit / ao entrar em 'vigiando' → wakeLock.acquire()
//   ao entrar em 'parada'            → wakeLock.release()  (opcional: browser já libera)
//   ao retornar de 'parada'          → wakeLock.reacquire()
//   ngOnDestroy                      → wakeLock.release()
// =============================================================================

@Injectable({ providedIn: 'root' })
export class WakeLockService {
  /** Sentinela atual; null quando não há lock ativo. */
  private wakeLock: WakeLockSentinel | null = null;

  private readonly _apiIndisponivel = signal(false);
  private readonly _ativo = signal(false);

  /** true quando a Wake Lock API não está disponível no browser (degradação). */
  readonly apiIndisponivel = this._apiIndisponivel.asReadonly();

  /** true quando o lock de tela está ativo. */
  readonly ativo = this._ativo.asReadonly();

  /**
   * Suporte verificado uma vez no carregamento.
   * Checamos `!!navigator.wakeLock` (não apenas `'wakeLock' in navigator`) para garantir
   * que o objeto existe e não é undefined — necessário em ambientes de teste onde a propriedade
   * pode ser definida como undefined para simular ausência da API.
   */
  readonly isSupported: boolean =
    typeof navigator !== 'undefined' &&
    'wakeLock' in navigator &&
    !!navigator.wakeLock;

  // ---------------------------------------------------------------------------
  // API pública
  // ---------------------------------------------------------------------------

  /**
   * Solicita screen Wake Lock.
   *
   * - API ausente → declara degradação (`apiIndisponivel = true`), retorna sem lançar.
   * - Aba oculta no momento da chamada → o browser não concede o lock (silencioso).
   * - Sucesso → `ativo = true`.
   */
  async acquire(): Promise<void> {
    if (!this.isSupported) {
      this._apiIndisponivel.set(true);
      return; // degradação declarada — não é exceção
    }

    try {
      this.wakeLock = await navigator.wakeLock.request('screen');
      this._ativo.set(true);

      // O browser emite 'release' quando libera o lock (aba oculta ou navegação).
      // Registrar antes de qualquer await para não perder o evento.
      this.wakeLock.addEventListener('release', () => {
        this.wakeLock = null;
        this._ativo.set(false);
      });
    } catch {
      // Falha se a aba já estava oculta no momento da solicitação.
      this.wakeLock = null;
      this._ativo.set(false);
    }
  }

  /**
   * Libera o lock explicitamente.
   * Chamar quando a estação entra em 'parada' ou em ngOnDestroy.
   */
  release(): void {
    if (this.wakeLock) {
      this.wakeLock.release().catch(() => {
        /* já liberado — ignorar */
      });
      this.wakeLock = null;
    }
    this._ativo.set(false);
  }

  /**
   * Tenta readquirir o lock após retorno de aba oculta.
   * Só age se não há lock ativo e o documento está visível.
   * Chamar no handler de visibilitychange quando `!document.hidden`.
   */
  async reacquire(): Promise<void> {
    if (this.wakeLock === null && !document.hidden) {
      await this.acquire();
    }
  }
}
