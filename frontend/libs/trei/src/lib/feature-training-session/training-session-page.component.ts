import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TrainingSessionFacade } from '../trei.facade';
import { TreiLocale } from '../trei.types';
import { LocaleTabsComponent } from './locale-tabs.component';
import { ChatConversationPanelComponent } from './chat-conversation-panel.component';
import { ConversationErrorPanelComponent } from './conversation-error-panel.component';
import { EmptySessionStateComponent } from './empty-session-state.component';
import { EndSessionButtonComponent } from './end-session-button.component';
import { SessionEndedNoticeComponent } from './session-ended-notice.component';

// =============================================================================
// TrainingSessionPageComponent — Tela 08 (SPEC-02 §2, FE-TREI-013..017)
// Route: /treinamento/sessao
//
// SMART container — reads signals from TrainingSessionFacade and delegates
// all rendering to presentational components.
//
// Signal sources consumed:
//   - facade.screenVm  → session | messages | activeLocale | status | isAssistantThinking | error
//
// User actions delegated to facade:
//   - onStart()        → facade.startSession(pacienteId)
//   - onEnd()          → facade.endSession()
//   - onLocaleChange() → facade.setActiveLocale(locale)
//   - onTurnSubmit()   → facade.sendTurn(text, parentNoId, tipo)
//   - onRetry()        → facade.retry(pacienteId)
//
// P6: TreiAuthoringBannerComponent extracted to TreiRootComponent (TREI-FE-021).
// The banner now renders on all TREI routes via the root layout component.
// =============================================================================

@Component({
  selector: 'trei-training-session-page',
  standalone: true,
  imports: [
    RouterLink,
    LocaleTabsComponent,
    ChatConversationPanelComponent,
    ConversationErrorPanelComponent,
    EmptySessionStateComponent,
    EndSessionButtonComponent,
    SessionEndedNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-6">

      <!-- LocaleTabs (ADR-018 — locale-independent) -->
      <trei-locale-tabs
        [locales]="locales"
        [activeLocale]="activeLocale()"
        (localeChange)="onLocaleChange($event)" />

      <!-- P7 (FE-025): health-degraded affordance — visible when mock adapter is active -->
      @if (vm().isDegraded) {
        <div
          class="mb-3 flex items-center gap-2 rounded-xl border border-warning bg-warning-bg
                 px-3 py-2 text-xs text-warning-ink"
          data-testid="health-degraded-badge"
          role="status"
          aria-label="Sistema em modo simulado — sem conexão com o servidor de IA">
          <span class="material-symbols-outlined text-[14px]" aria-hidden="true">warning</span>
          Modo simulado — dados reais indisponíveis
        </div>
      }

      <!-- P7 (INT-005 / G5-link): embed model readiness for CONV integration -->
      @if (vm().embedModelId) {
        <div
          class="mb-3 flex items-center gap-2 rounded-xl border border-success bg-success-bg
                 px-3 py-2 text-xs text-success-ink"
          data-testid="embed-model-status"
          aria-label="Motor de IA disponível">
          <span class="material-symbols-outlined text-[14px]" aria-hidden="true">smart_toy</span>
          Motor de IA: disponível
        </div>
      }

      <!-- P7 (FE-025 / G4): patient-not-found banner — specific 404 case -->
      @if (vm().isPatientNotFound) {
        <div
          class="mb-4 rounded-xl border border-warning bg-warning-bg p-4"
          data-testid="patient-not-found-banner"
          role="alert">
          <p class="font-semibold text-warning-ink">Paciente não encontrado</p>
          <p class="mt-1 text-xs text-warning-ink">
            O perfil do paciente associado não foi localizado no sistema.
            Verifique se o paciente está ativo antes de iniciar uma sessão de treinamento.
          </p>
        </div>

      } @else if (vm().session === null && vm().status === 'idle') {
        <!-- EmptyState CTA -->
        <trei-empty-session-state (sessionStart)="onStart()" />

      } @else if (vm().status === 'loading') {
        <!-- Loading state -->
        <div class="flex items-center gap-3 rounded-xl bg-surface-2 p-4 text-sm text-ink-soft">
          <div
            class="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent"
            role="status"
            aria-label="Carregando"></div>
          Iniciando sessão…
        </div>

      } @else if (vm().status === 'error') {
        <!-- Error state (G5: ConversationErrorPanel never exposes correlationId) -->
        <trei-conversation-error-panel
          [error]="vm().error"
          (retry)="onRetry()" />

      } @else {
        <!-- Active session — conversation panel + controls -->
        <trei-chat-conversation-panel
          [turns]="vm().messages"
          [isThinking]="vm().isAssistantThinking"
          [showComposer]="vm().session?.estado === 'ativa'"
          (caregiverTurnSubmit)="onTurnSubmit($event)" />

        <!-- Navigation link + EndSession button -->
        @if (vm().messages.length > 0) {
          <div class="mt-4 flex items-center justify-between">
            <a
              routerLink="/treinamento/sessao/propostas"
              data-testid="review-proposals-link"
              class="text-sm font-semibold text-primary hover:underline">
              Ver propostas para aprovação →
            </a>
            @if (vm().session?.estado === 'ativa') {
              <trei-end-session-button (end)="onEnd()" />
            }
          </div>
        }

        <!-- SessionEndedNotice (INV-4: proposals retained as drafts) -->
        @if (vm().session?.estado === 'encerrada') {
          <trei-session-ended-notice />
        }
      }

    </div>
  `,
})
export class TrainingSessionPageComponent {
  private readonly facade = inject(TrainingSessionFacade);

  protected readonly locales: TreiLocale[] = ['pt', 'en'];

  /** Assembled screen VM — single signal source for all template bindings. */
  protected readonly vm = this.facade.screenVm;

  /** Active locale signal — passed to LocaleTabsComponent. */
  protected readonly activeLocale = this.facade.activeLocale;

  // Mock pacienteId — Layer 2 will wire from session context (DashHomeFacade)
  private readonly MOCK_PACIENTE_ID = 'pac-marcio';

  protected onStart(): void {
    void this.facade.startSession(this.MOCK_PACIENTE_ID);
  }

  protected onEnd(): void {
    this.facade.endSession();
  }

  protected onLocaleChange(locale: TreiLocale): void {
    this.facade.setActiveLocale(locale);
  }

  protected onTurnSubmit(text: string): void {
    void this.facade.sendTurn(text, 'n-root', 'frase');
  }

  protected onRetry(): void {
    void this.facade.retry(this.MOCK_PACIENTE_ID);
  }
}
