import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { BannerService } from './banner.service';

// =============================================================================
// Onde a faixa aparece: no topo do conteúdo, empurrando a página — e não
// flutuando por cima dela. Uma condição que continua valendo deve ocupar espaço;
// sobrepor esconderia justamente o que a pessoa precisa consertar.
//
// `role="status"` com `aria-live="polite"`: a faixa é visível e permanente, então
// interromper a leitura não acrescenta nada. Quem interrompe, neste produto, é o
// alarme.
// =============================================================================

@Component({
  selector: 'ui-banner-outlet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="status" aria-live="polite">
      @if (banners.faixa(); as faixa) {
        <div
          class="flex items-start gap-3 border-b px-6 py-3 text-sm"
          [class]="
            faixa.consultiva
              ? 'border-line bg-surface-2 text-ink-soft'
              : 'border-danger bg-danger-bg text-danger-ink'
          "
          data-testid="banner">
          <span aria-hidden="true" class="material-symbols-outlined text-[20px] leading-none">
            {{ faixa.consultiva ? 'info' : 'error' }}
          </span>
          <p class="min-w-0 flex-1">{{ faixa.mensagem }}</p>

          @if (faixa.dispensavel) {
            <button
              type="button"
              class="shrink-0 rounded-lg p-1 opacity-70 hover:opacity-100"
              [attr.aria-label]="'Dispensar aviso: ' + faixa.mensagem"
              data-testid="banner-dispensar"
              (click)="banners.dispensar()">
              <span aria-hidden="true" class="material-symbols-outlined text-[18px] leading-none">close</span>
            </button>
          }
        </div>
      }
    </div>
  `,
})
export class BannerOutletComponent {
  protected readonly banners = inject(BannerService);
}
