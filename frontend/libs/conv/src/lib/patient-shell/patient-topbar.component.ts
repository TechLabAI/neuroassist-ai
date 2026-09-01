import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { Locale } from '../conv.types';

// =============================================================================
// PatientTopbarComponent — presentational topbar for the restricted patient shell.
// CONV-FE-003. Accepts locale + patientName as inputs; emits localeSwitchRequested.
// No caregiver route is rendered here — no /historico, /calibracao, /onboarding link.
// =============================================================================

@Component({
  selector: 'conv-patient-topbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="patient-topbar">
      <span class="flex items-center gap-1.5 text-sm font-semibold text-ink">
        <span class="material-symbols-outlined text-[18px] text-brand">smartphone</span>
        {{ patientName() }}
      </span>

      <!-- Locale switcher — bilingual EN/PT (ADR-018) -->
      <div class="flex items-center gap-3">
        <div class="flex overflow-hidden rounded-full border border-line-strong text-xs font-semibold">
          <button
            (click)="localeSwitchRequested.emit('pt')"
            class="px-2.5 py-1"
            [class]="locale() === 'pt' ? 'bg-brand text-white' : 'text-ink-soft hover:bg-surface'">
            PT
          </button>
          <button
            (click)="localeSwitchRequested.emit('en')"
            class="px-2.5 py-1"
            [class]="locale() === 'en' ? 'bg-brand text-white' : 'text-ink-soft hover:bg-surface'">
            EN
          </button>
        </div>
        <span class="flex items-center gap-1.5 rounded-full bg-info-bg px-2.5 py-1 text-xs font-semibold text-info-ink">
          <span class="material-symbols-outlined text-[14px]">circle</span>
          Sessão ativa
        </span>
      </div>
    </header>
  `,
  styles: [`:host { display: contents; }
    .patient-topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 3.5rem;
      padding: 0 1.25rem;
      border-bottom: 1px solid var(--color-line, #2a2a2a);
      flex-shrink: 0;
    }`],
})
export class PatientTopbarComponent {
  readonly locale = input.required<Locale>();
  readonly patientName = input<string>('Paciente');
  readonly localeSwitchRequested = output<Locale>();
}
