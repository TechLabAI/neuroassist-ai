import { ChangeDetectionStrategy, Component } from '@angular/core';

// =============================================================================
// AssistantThinkingIndicatorComponent — SPEC-02 §2 (FE-TREI-013)
//
// Presentational: animated "typing" indicator shown while the LLM is generating.
// Shown by parent (ChatConversationPanel) when isThinking=true.
// No inputs or outputs — purely presentational.
// =============================================================================

@Component({
  selector: 'trei-assistant-thinking-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex justify-start" data-testid="assistant-thinking">
      <div class="rounded-2xl border border-line bg-surface-2 px-4 py-2.5">
        <div class="flex items-center gap-1.5 text-sm text-ink-soft">
          <div class="flex gap-1">
            <span
              class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint"
              style="animation-delay: 0ms"></span>
            <span
              class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint"
              style="animation-delay: 150ms"></span>
            <span
              class="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-faint"
              style="animation-delay: 300ms"></span>
          </div>
          O assistente está pensando…
        </div>
      </div>
    </div>
  `,
})
export class AssistantThinkingIndicatorComponent {}
