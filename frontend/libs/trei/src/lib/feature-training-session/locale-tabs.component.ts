import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { TreiLocale } from '../trei.types';

// =============================================================================
// LocaleTabsComponent — SPEC-02 §2 (FE-TREI-017)
//
// Presentational: locale selector tab strip (PT | EN).
// ADR-018: locale is independent — switching locale does NOT affect existing messages.
// =============================================================================

@Component({
  selector: 'trei-locale-tabs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-4 flex gap-2" role="tablist" aria-label="Idioma de autoria">
      @for (locale of locales; track locale) {
        <button
          role="tab"
          [attr.aria-selected]="activeLocale === locale"
          [attr.data-testid]="'locale-tab-' + locale"
          (click)="localeChange.emit(locale)"
          class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
          [class]="activeLocale === locale
            ? 'bg-primary text-white'
            : 'bg-surface-2 text-ink-soft hover:text-ink'">
          {{ locale.toUpperCase() }}
        </button>
      }
    </div>
  `,
})
export class LocaleTabsComponent {
  @Input({ required: true }) locales: TreiLocale[] = [];
  @Input({ required: true }) activeLocale: TreiLocale = 'pt';
  @Output() readonly localeChange = new EventEmitter<TreiLocale>();
}
