import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TREI_AUTH_SESSION, TREI_ACCESS_DECISION } from './trei-access-tokens';

// =============================================================================
// TREI Guards — SPEC-01 §3 (FE-TREI-001..003)
//
// caregiverSessionGuard : verifies that a valid caregiver session token is present.
//                         Failure → redirect to /.
// treiGuard             : verifies treinamento.sessao.conduzir scope.
//                         Failure → redirect to /.
// treiProporGuard       : verifies treinamento.no.propor scope (for /propostas sub-route).
//                         Failure → keeps /treinamento/sessao reachable (review-surface
//                         remains accessible without propor scope — SPEC-01 gap G1).
//
// Wave-0 bypass: tokens default to null. Guards pass through when null — preserving
// dev/test/mock environments without backend auth running.
// =============================================================================

/** caregiverSessionGuard — session presence check (TREI-FE-001). */
export const caregiverSessionGuard: CanActivateFn = () => {
  const session = inject(TREI_AUTH_SESSION, { optional: true });

  // Wave-0 bypass: not wired — allow through
  if (session === null) {
    return true;
  }

  if (session.token === null) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};

/**
 * treiGuard — treinamento.sessao.conduzir scope check (TREI-FE-002).
 * Guards the /treinamento/sessao route (Tela 08 — ChatConversationPanel).
 */
export const treiGuard: CanActivateFn = () => {
  const access = inject(TREI_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: not wired — allow through
  if (access === null) {
    return true;
  }

  if (!access.can('treinamento.sessao.conduzir')) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};

/**
 * treiProporGuard — treinamento.no.propor scope check (TREI-FE-003).
 * Guards the /treinamento/sessao/propostas sub-route (ProposalReview).
 * Gap G1 (specs-validate): even without treinamento.no.propor the review surface
 * must remain reachable (read-only, no new proposals). This guard blocks only when
 * access decision is explicitly wired AND the scope is absent.
 */
export const treiProporGuard: CanActivateFn = () => {
  const access = inject(TREI_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: not wired — allow through (review is always readable)
  if (access === null) {
    return true;
  }

  // Allow: propor OR conduzir (conduzir is the baseline; propor extends it)
  if (access.can('treinamento.no.propor') || access.can('treinamento.sessao.conduzir')) {
    return true;
  }

  return inject(Router).createUrlTree(['/treinamento/sessao']);
};
