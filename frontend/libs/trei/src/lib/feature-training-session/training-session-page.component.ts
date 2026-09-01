import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TrainingSessionFacade } from '../trei.facade';
import { TreiLocale } from '../trei.types';

// =============================================================================
// TrainingSessionPageComponent — Tela 08 (SPEC-02 §3, FE-TREI-004..012)
// Route: /treinamento/sessao
//
// DREI has no BA prototype (D-493 promote path does not apply — vision doc §5).
// This component is scaffolded from specs (generator-config.yml / SPEC-02).
//
// UI structure (SPEC-02 §3):
//   - "Treinamento — não é o app do paciente" affordance banner (TECH-007)
//   - LocaleTabs (PT | EN active locale selector — ADR-018)
//   - ChatConversationPanel (messages list + AssistantThinkingIndicator)
//   - ChatComposer (caregiver text input + Send button)
//   - EndSessionButton + SessionEndedNotice
//   - EmptyState CTA "Iniciar treinamento" when no session active
//
// TODO[FE-TREI-004..012]: flesh out full UI from SPEC-02 component hierarchy.
//   Each issue covers one sub-component (ChatBubble, LocaleTabs, ChatComposer, etc.)
//   This stub covers the structural skeleton: session init + message display.
// =============================================================================

@Component({
  selector: 'lib-training-session-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-6">

      <!-- AI authoring surface affordance (TECH-007 / vision §4) -->
      <div
        class="mb-4 flex items-center gap-2 rounded-xl border border-warning bg-warning-bg px-4 py-2.5 text-sm font-semibold text-warning-ink"
        data-testid="authoring-banner"
        aria-label="Esta é a superfície de autoria — não é o app do paciente">
        <span class="material-symbols-outlined text-[18px]">psychology</span>
        <span>Treinamento — não é o app do paciente</span>
      </div>

      <!-- LocaleTabs (ADR-018 — locale-independent per locale) -->
      <div class="mb-4 flex gap-2" role="tablist" aria-label="Idioma de autoria">
        @for (locale of locales; track locale) {
          <button
            role="tab"
            [attr.aria-selected]="activeLocale() === locale"
            [attr.data-testid]="'locale-tab-' + locale"
            (click)="setLocale(locale)"
            class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
            [class]="activeLocale() === locale
              ? 'bg-primary text-white'
              : 'bg-surface-2 text-ink-soft hover:text-ink'">
            {{ locale.toUpperCase() }}
          </button>
        }
      </div>

      @if (vm().session === null && vm().status === 'idle') {
        <!-- EmptyState CTA — no active session (SPEC-02 §4) -->
        <div
          class="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed border-line bg-surface p-12 text-center"
          data-testid="empty-state">
          <span class="material-symbols-outlined mb-3 text-4xl text-ink-faint">chat</span>
          <h2 class="font-display text-xl font-bold text-ink">Iniciar treinamento</h2>
          <p class="mt-2 max-w-sm text-sm text-ink-soft">
            Converse com o assistente para propor novas categorias e frases
            para a árvore de fala de Márcio.
          </p>
          <button
            (click)="startSession()"
            data-testid="start-session-btn"
            class="mt-6 rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-dark">
            Iniciar sessão
          </button>
        </div>

      } @else if (vm().status === 'loading') {
        <!-- Loading state -->
        <div class="flex items-center gap-3 rounded-xl bg-surface-2 p-4 text-sm text-ink-soft">
          <div class="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
          Iniciando sessão…
        </div>

      } @else if (vm().status === 'error') {
        <!-- Error state -->
        <div role="alert" class="rounded-2xl border border-error bg-error-bg p-4 text-sm text-error-ink"
             data-testid="session-error">
          <p class="font-semibold">Erro ao iniciar sessão</p>
          <p class="mt-1 text-xs">{{ vm().error?.message }}</p>
          <button
            (click)="startSession()"
            class="mt-3 rounded-lg border border-error px-3 py-1.5 text-xs font-semibold text-error-ink hover:bg-error-bg">
            Tentar novamente
          </button>
        </div>

      } @else {
        <!-- Active session — conversation panel + composer -->
        <div class="flex flex-col gap-4">

          <!-- ChatConversationPanel (SPEC-02 §3) -->
          <div
            class="flex h-[400px] flex-col gap-3 overflow-y-auto rounded-2xl border border-line bg-surface p-4"
            data-testid="conversation-panel"
            aria-label="Conversa com o assistente">

            @for (msg of vm().messages; track msg.id) {
              <!-- ChatBubble — caregiver vs assistant (SPEC-02 §3) -->
              <div
                [attr.data-testid]="'msg-' + msg.role"
                [attr.data-message-id]="msg.id"
                class="flex gap-2"
                [class]="msg.role === 'caregiver' ? 'justify-end' : 'justify-start'">
                <div
                  class="max-w-[75%] rounded-2xl px-4 py-2.5 text-sm"
                  [class]="msg.role === 'caregiver'
                    ? 'bg-primary text-white'
                    : 'border border-line bg-surface-2 text-ink'">

                  {{ msg.content }}

                  <!-- AI disclosure badge (TECH-007 / ADR-017) -->
                  @if (msg.aiDisclosed) {
                    <div class="mt-2 flex items-center gap-1 text-[11px] opacity-70">
                      <span class="material-symbols-outlined text-[12px]">psychology</span>
                      Gerado por IA — verifique antes de aprovar
                    </div>
                  }

                  <!-- Proposals list (if any) -->
                  @if (msg.proposals && msg.proposals.length > 0) {
                    <div class="mt-3 flex flex-col gap-1" data-testid="proposals-list">
                      @for (p of msg.proposals; track p.propostaId) {
                        <div
                          class="flex items-center justify-between gap-3 rounded-xl border border-line-soft bg-surface px-3 py-2 text-ink"
                          [attr.data-proposal-id]="p.propostaId">
                          <span class="text-sm">{{ p.texto }}</span>
                          <span class="shrink-0 rounded-md bg-surface-2 px-1.5 py-0.5 text-[10px] text-ink-soft">
                            {{ p.tipo }}
                          </span>
                        </div>
                      }
                    </div>
                  }
                </div>
              </div>
            }

            <!-- AssistantThinkingIndicator (SPEC-02 §4) -->
            @if (vm().isAssistantThinking) {
              <div class="flex justify-start" data-testid="assistant-thinking">
                <div class="rounded-2xl border border-line bg-surface-2 px-4 py-2.5">
                  <div class="flex gap-1.5 items-center text-sm text-ink-soft">
                    <div class="flex gap-1">
                      <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint [animation-delay:0ms]"></span>
                      <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint [animation-delay:150ms]"></span>
                      <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint [animation-delay:300ms]"></span>
                    </div>
                    O assistente está pensando…
                  </div>
                </div>
              </div>
            }
          </div>

          <!-- ChatComposer (SPEC-02 §3) -->
          @if (vm().session?.estado === 'ativa') {
            <div class="flex gap-2" data-testid="chat-composer">
              <input
                [(ngModel)]="instrucao"
                (keyup.enter)="sendTurn()"
                [disabled]="vm().isAssistantThinking"
                placeholder="Descreva o que Márcio precisa falar…"
                data-testid="composer-input"
                class="flex-1 rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:outline-none disabled:opacity-50" />
              <button
                (click)="sendTurn()"
                [disabled]="!instrucao || vm().isAssistantThinking"
                data-testid="send-btn"
                class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-dark disabled:opacity-50">
                <span class="material-symbols-outlined text-[18px]">send</span>
              </button>
            </div>
          }

          <!-- Navigation to proposal review -->
          @if (vm().messages.length > 0) {
            <div class="flex items-center justify-between">
              <a
                routerLink="/treinamento/sessao/propostas"
                data-testid="review-proposals-link"
                class="text-sm font-semibold text-primary hover:underline">
                Ver propostas para aprovação →
              </a>

              <!-- EndSessionButton (SPEC-02 §4 — no-loss guarantee) -->
              @if (vm().session?.estado === 'ativa') {
                <button
                  (click)="endSession()"
                  data-testid="end-session-btn"
                  class="rounded-xl border border-line px-4 py-2 text-sm text-ink-soft hover:bg-surface-2">
                  Encerrar sessão
                </button>
              }
            </div>
          }

          <!-- SessionEndedNotice -->
          @if (vm().session?.estado === 'encerrada') {
            <div
              class="rounded-xl border border-success bg-success-bg p-3 text-sm text-success-ink"
              data-testid="session-ended-notice">
              Sessão encerrada. As propostas pendentes continuam como rascunhos.
            </div>
          }

        </div>
      }

    </div>
  `,
})
export class TrainingSessionPageComponent implements OnInit {
  private readonly facade = inject(TrainingSessionFacade);

  protected readonly locales: TreiLocale[] = ['pt', 'en'];
  protected readonly vm = this.facade.screenVm;
  protected readonly activeLocale = this.facade.activeLocale;
  protected instrucao = '';

  // Mock pacienteId — Layer 2 will wire from session context (DashHomeFacade)
  private readonly MOCK_PACIENTE_ID = 'pac-marcio';

  ngOnInit(): void {
    // Session is started on CTA click, not on route enter
  }

  protected startSession(): void {
    void this.facade.startSession(this.MOCK_PACIENTE_ID);
  }

  protected setLocale(locale: TreiLocale): void {
    this.facade.setActiveLocale(locale);
  }

  protected sendTurn(): void {
    if (!this.instrucao.trim() || this.vm().isAssistantThinking) return;
    const text = this.instrucao.trim();
    this.instrucao = '';
    void this.facade.sendTurn(text, 'n-root', 'frase');
  }

  protected endSession(): void {
    this.facade.endSession();
  }
}
