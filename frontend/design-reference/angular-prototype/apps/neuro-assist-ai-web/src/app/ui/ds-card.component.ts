import { Component, ChangeDetectionStrategy, input } from '@angular/core';

// ds-card — superfície glass. Três níveis de profundidade.
// default = card principal · elevated = flutuante · surface-2 = painel aninhado.
type Variant = 'default' | 'elevated' | 'surface-2';
const VARIANT: Record<Variant, string> = {
  default:     'glass',
  elevated:    'glass',
  'surface-2': 'glass',
};

@Component({
  selector: 'app-ds-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="rounded-card p-5" [class]="VARIANT[variant()]"><ng-content /></div>`,
})
export class DsCardComponent {
  readonly variant = input<Variant>('default');
  protected readonly VARIANT = VARIANT;
}
