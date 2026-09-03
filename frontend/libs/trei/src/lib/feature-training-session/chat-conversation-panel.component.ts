import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { ChatMessage } from '../trei.types';
import { ChatBubbleComponent } from './chat-bubble.component';
import { AssistantThinkingIndicatorComponent } from './assistant-thinking-indicator.component';
import { ChatComposerComponent } from './chat-composer.component';

// =============================================================================
// ChatConversationPanelComponent — SPEC-02 §2 (FE-TREI-013 / FE-TREI-014)
//
// Presentational: scrollable conversation area + composer.
// Contains: ChatBubble rows, AssistantThinkingIndicator, ChatComposer.
// Output: caregiverTurnSubmit relays the composer's submit to the smart parent.
// =============================================================================

@Component({
  selector: 'trei-chat-conversation-panel',
  standalone: true,
  imports: [
    ChatBubbleComponent,
    AssistantThinkingIndicatorComponent,
    ChatComposerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">

      <!-- Scrollable message list -->
      <div
        class="flex h-[400px] flex-col gap-3 overflow-y-auto rounded-2xl border border-line bg-surface p-4"
        data-testid="conversation-panel"
        aria-label="Conversa com o assistente">

        @for (turn of turns; track turn.id) {
          <trei-chat-bubble [turn]="turn" />
        }

        @if (isThinking) {
          <trei-assistant-thinking-indicator />
        }
      </div>

      <!-- Composer — only when the session is still active -->
      @if (showComposer) {
        <trei-chat-composer
          [disabled]="isThinking"
          (submitTurn)="caregiverTurnSubmit.emit($event)" />
      }
    </div>
  `,
})
export class ChatConversationPanelComponent {
  /** Conversation turns from the facade (ChatMessage preserves proposals + aiDisclosed). */
  @Input({ required: true }) turns: ChatMessage[] = [];

  /** True while the LLM is generating — disables the composer and shows thinking indicator. */
  @Input() isThinking = false;

  /**
   * True when the session estado === 'ativa'.
   * When false (ended or not yet started) the composer is hidden.
   */
  @Input() showComposer = false;

  /** Relayed from ChatComposer.submitTurn → TrainingSessionPageComponent → facade.sendTurn(). */
  @Output() readonly caregiverTurnSubmit = new EventEmitter<string>();
}
