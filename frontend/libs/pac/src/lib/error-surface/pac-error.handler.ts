import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AppError } from '@neuroassist/shared/error-model';
import { PacFeedbackService } from './pac-feedback.service';

// ---------------------------------------------------------------------------
// PacErrorHandler — PAC-FE-021 (P7 UX Polish)
// Orchestrates the 12 ProblemDetail error types to the 4 UX surfaces
// (inline / toast / banner / modal) per SPEC-07 §1/§2/§3/§4.
//
// Surface hierarchy (SPEC-07 §3):
//   inline (field) < toast (transient) < banner (session-persistent) < modal (decisão)
//
// Safety-critical:
//   'emergency-not-detectable' → modal bloqueante, DESENHADO PELO EDITOR
//   (fail-closed, não fecha por ESC nem por clique fora) — SPEC-07 §4 / ADR-005.
//   Este handler devolve 'inline' nesse caso: ver a justificativa no `case`.
//
// Usage:
//   inject(PacErrorHandler).handle(appError, 'editor')
//
// The facade (PacComandoFacade) catches HTTP errors, maps them via
// ProblemDetailMapper (PAC-FE-013) to AppError, then delegates to this handler.
// The 'inline' codes are returned as-is — the calling component handles them
// via the AppError signal in the facade's state.
// ---------------------------------------------------------------------------

export type ErrorContext = 'catalog' | 'editor' | 'patient-register';

@Injectable({ providedIn: 'root' })
export class PacErrorHandler {
  private readonly feedback = inject(PacFeedbackService);
  private readonly router = inject(Router);

  /**
   * Route an AppError to the appropriate UX surface.
   *
   * Returns:
   *   'inline'  — caller must display the error inline (e.g. field validation).
   *   'handled' — the handler displayed the error via toast/banner/modal/navigation.
   */
  handle(error: AppError, _context: ErrorContext): 'inline' | 'handled' {
    switch (error.code) {
      // -----------------------------------------------------------------------
      // Inline — parent component handles via AppError signal
      // -----------------------------------------------------------------------
      case 'validation':
        // Inline field error — parent handles via AppError in facade signal.
        return 'inline';

      case 'duplicate-active-mapping':
        // Inline on the gesture picker — parent handles.
        return 'inline';

      // -----------------------------------------------------------------------
      // Banner — persistent informational (session-scoped)
      // -----------------------------------------------------------------------
      case 'missing-tenant':
      case 'tenant-mismatch':
        this.feedback.showBanner(error.message, { persistent: true });
        return 'handled';

      // -----------------------------------------------------------------------
      // Navigation — redirect
      // -----------------------------------------------------------------------
      case 'unauthorized':
        void this.router.navigate(['/login']);
        return 'handled';

      case 'not-found':
        void this.router.navigate(['/pac']);
        return 'handled';

      // -----------------------------------------------------------------------
      // Toast — transient, non-blocking
      // -----------------------------------------------------------------------
      case 'insufficient-scope':
      case 'invalid-lifecycle-transition':
        this.feedback.showToast(error.message);
        return 'handled';

      case 'eligibility-gate-unavailable':
        this.feedback.showToast(error.message, { retryable: true });
        return 'handled';

      // -----------------------------------------------------------------------
      // Modal — conscious decision required
      // -----------------------------------------------------------------------
      case 'conflict-precondition':
      case 'idempotency-mismatch':
        this.feedback.showModal({
          title: 'Conflito de versão',
          message: error.message,
          action: 'reload',
        });
        return 'handled';

      // -----------------------------------------------------------------------
      // Blocking modal — SAFETY-CRITICAL (fail-closed, non-dismissible)
      // ADR-005 / SPEC-07 §4: emergency command publication blocked when gesto
      // is not detectable by the vision engine. User MUST change urgency or
      // select a detectable gesto before the modal resolves.
      // -----------------------------------------------------------------------
      case 'emergency-not-detectable':
        // DONO: o CommandEditorComponent, e não este handler.
        //
        // Ele já desenha o próprio modal bloqueante a partir de
        // `editor().emergencyGateError`, e o dele tem o que importa: o botão
        // "Alterar para Rotina", que É a saída. Um modal genérico aqui diria
        // "Entendi" e devolveria a pessoa exatamente onde ela estava — a mesma
        // tela, o mesmo comando recusado, nenhum caminho à frente. Pior: seriam
        // DOIS modais sobre o mesmo fato, um deles sem serventia.
        //
        // O comentário do bloco acima diz que a pessoa "MUST change urgency or
        // select a detectable gesto"; quem oferece essas duas coisas é o editor.
        // Rotear para cá era mandar a decisão para quem não pode tomá-la.
        return 'inline';

      // -----------------------------------------------------------------------
      // Default — advisory rotina warnings → dismissible banner
      // -----------------------------------------------------------------------
      default:
        this.feedback.showBanner(error.message, { advisory: true, dismissible: true });
        return 'handled';
    }
  }
}
