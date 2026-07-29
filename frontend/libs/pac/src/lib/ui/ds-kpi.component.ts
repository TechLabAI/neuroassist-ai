import { Component, ChangeDetectionStrategy, input } from '@angular/core';

// ds-kpi — local copy for libs/pac (source: prototype ui/ds-kpi.component.ts)
// TODO: migrate to @neuroassist/shared/ui-components when that lib is fleshed out.

type Kind = 'tint' | 'vivid' | 'glass';
type SupportTone = 'up' | 'warn' | 'flat';

const WRAP: Record<Kind, string> = {
  tint:  'glass',
  vivid: 'bg-brand',
  glass: 'glass glass--hero',
};
const LABEL_CLS: Record<Kind, string> = {
  tint:  'text-ink-faint',
  vivid: 'text-brand-ink/65',
  glass: 'text-ink-soft',
};
const VALUE_CLS: Record<Kind, string> = {
  tint:  'text-ink',
  vivid: 'text-brand-ink',
  glass: 'text-ink',
};
const SUPPORT_CLS: Record<SupportTone, string> = {
  up:   'text-success-ink',
  warn: 'text-warning-ink',
  flat: 'text-ink-faint',
};

@Component({
  selector: 'pac-ds-kpi',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-[150px] flex-col rounded-card p-5" [class]="WRAP[kind()]">
      <span class="font-display text-2xs font-medium uppercase tracking-wide" [class]="LABEL_CLS[kind()]">{{ label() }}</span>
      <span class="mt-1 font-display text-3xl font-bold leading-tight" [class]="VALUE_CLS[kind()]">{{ value() }}</span>
      @if (delta()) {
        <div class="mt-auto border-t border-white/[0.06] pt-3.5">
          <span class="text-xs font-medium" [class]="SUPPORT_CLS[supportTone()]">{{ delta() }}</span>
        </div>
      }
    </div>
  `,
})
export class PacDsKpiComponent {
  readonly label = input('');
  readonly value = input<string | number>('');
  readonly delta = input<string | null>(null);
  readonly kind = input<Kind>('tint');
  readonly supportTone = input<SupportTone>('flat');
  protected readonly WRAP = WRAP;
  protected readonly LABEL_CLS = LABEL_CLS;
  protected readonly VALUE_CLS = VALUE_CLS;
  protected readonly SUPPORT_CLS = SUPPORT_CLS;
}
