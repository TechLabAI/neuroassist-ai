import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { BannerService } from './banner.service';

// =============================================================================
// Onde a faixa aparece: no topo do conteúdo, empurrando a página — e não
// flutuando por cima dela. Uma condição que continua valendo deve ocupar espaço;
// sobrepor esconderia justamente o que a pessoa precisa consertar.
//
// O PAPEL DEPENDE DA SEVERIDADE, e a distinção é principiada:
//
//   consultiva → `role="status"` (`aria-live="polite"`). Ela informa; a leitura
//     em curso não precisa ser cortada por causa dela.
//   não consultiva → `role="alert"` (assertivo). Ela descreve uma condição
//     QUEBRADA — sessão sem empresa, catálogo indisponível — e quem está usando
//     o sistema precisa saber disso antes de continuar digitando.
//
// Isto não contradiz a regra de que só o alarme de emergência interrompe: aquela
// regra vale para o AVISO PASSAGEIRO, que é frequente e trata do que acabou de
// acontecer. Uma faixa de erro é rara e persistente. Gastar a interrupção aqui é
// justamente o caso para o qual `alert` existe.
// =============================================================================

@Component({
  selector: 'ui-banner-outlet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [attr.role]="papel()" [attr.aria-live]="vivacidade()">
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

  /**
   * `alert` para erro, `status` para consultiva.
   *
   * <p>Sem faixa alguma o contêiner fica como `status`: um `role="alert"` vazio
   * e permanente no DOM é ruído para o leitor de tela, e alguns anunciam a
   * região ao entrar nela.
   */
  protected readonly papel = computed(() => {
    const faixa = this.banners.faixa();
    return faixa !== null && !faixa.consultiva ? 'alert' : 'status';
  });

  protected readonly vivacidade = computed(() =>
    this.papel() === 'alert' ? 'assertive' : 'polite',
  );
}
