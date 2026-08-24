import { Injectable, inject, signal } from '@angular/core';
import { ToastService } from '@neuroassist/shared/ui-feedback';

// ---------------------------------------------------------------------------
// PacFeedbackService — PAC-FE-021 (P7 UX Polish)
// Local feedback service for the PAC module.
//
// DECISION (R2 mitigation, plan P7):
//   @neuroassist/shared/ui-feedback is a Wave-0 stub with no FeedbackService.
//   Since the emergency gate blocking modal is safety-critical (PAC-FE-021
//   SPEC-07 §3), a local PacFeedbackService is implemented here to avoid
//   blocking on the shared lib. When @neuroassist/shared/ui-feedback is
//   fleshed out, this service should be replaced with the shared FeedbackService.
//
// ATUALIZAÇÃO (2026-08-24): o `shared/ui-feedback` deixou de ser stub e ganhou um
// `ToastService` com componente que de fato desenha na tela. `showToast` passa a
// encaminhar para ele.
//
// Por que isto importava: o sinal `toast` deste serviço NÃO ERA RENDERIZADO POR
// NINGUÉM — nenhum componente do PAC o lia. O `PacErrorHandler` chamava
// `showToast(...)`, o sinal mudava, e a tela não dizia nada. O código parecia
// tratar o erro. O mesmo defeito do EDGE, cujo `EDGE_TOAST_TOKEN` não tinha
// provider: erro tratado no papel, silêncio na prática.
//
// O sinal local continua existindo — quem já dependia dele não quebra —, mas
// quem faz a mensagem chegar à pessoa é o serviço compartilhado.
// O `banner` e o `modal` seguem sem renderizador: dívida registrada, não
// resolvida aqui.
//
// Design: signal-based state store. Components bind to readonly signals
//   (toast, banner, modal) and call dismiss* to clear them.
//   PacErrorHandler injects this service and calls showToast/showBanner/showModal.
// ---------------------------------------------------------------------------

export interface ToastNotification {
  readonly message: string;
  readonly retryable?: boolean;
}

export interface BannerNotification {
  readonly message: string;
  readonly persistent?: boolean;
  readonly advisory?: boolean;
  readonly dismissible?: boolean;
}

export interface ModalNotification {
  readonly title?: string;
  readonly message: string;
  /** true → non-dismissible via ESC or outside click (safety-critical) */
  readonly blocking?: boolean;
  readonly action?: 'reload';
}

@Injectable({ providedIn: 'root' })
export class PacFeedbackService {
  private readonly _toast = signal<ToastNotification | null>(null);
  private readonly _banner = signal<BannerNotification | null>(null);
  private readonly _modal = signal<ModalNotification | null>(null);

  /** Current toast notification (null when none). */
  readonly toast = this._toast.asReadonly();

  /** Current banner notification (null when none). */
  readonly banner = this._banner.asReadonly();

  /** Current modal notification (null when none).
   * When modal.blocking === true, the modal MUST NOT be dismissible via ESC
   * or outside click — fail-closed safety gate (emergency-not-detectable). */
  readonly modal = this._modal.asReadonly();

  private readonly toasts = inject(ToastService);

  showToast(message: string, options?: Omit<ToastNotification, 'message'>): void {
    this._toast.set({ message, ...options });
    // `retryable` não vira botão: o contrato local é um booleano sem callback, e
    // um "Tentar novamente" que não sabe o que repetir é pior que nenhum.
    this.toasts.show(message);
  }

  showBanner(message: string, options?: Omit<BannerNotification, 'message'>): void {
    this._banner.set({ message, ...options });
  }

  showModal(options: ModalNotification): void {
    this._modal.set(options);
  }

  dismissToast(): void {
    this._toast.set(null);
  }

  dismissBanner(): void {
    this._banner.set(null);
  }

  /** Dismiss modal — blocked when modal.blocking === true (call only after resolution). */
  dismissModal(): void {
    this._modal.set(null);
  }
}
