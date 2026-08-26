import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { ROTA_LOGIN } from './auth.config';

// =============================================================================
// SairComponent — o botão de sair.
//
// Fica flutuando no canto porque o lugar certo dele é a topbar, e a topbar é de
// outro agente nesta rodada (`app/shell/`). Sair precisa EXISTIR desde o
// primeiro dia em que existe entrar: uma sessão que só termina fechando o
// navegador é uma sessão que a próxima pessoa do mesmo computador herda — e num
// posto de enfermagem o computador é compartilhado por definição.
//
// Quando a topbar ganhar o menu de perfil, mover este botão para lá e apagar o
// posicionamento fixo.
// =============================================================================

@Component({
  selector: 'auth-sair',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sessao.autenticado()) {
      <button
        type="button"
        class="fixed bottom-3 left-3 z-50 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-semibold text-ink-soft shadow-sm hover:text-ink"
        (click)="sair()"
        data-testid="sair"
      >
        Sair{{ rotulo() }}
      </button>
    }
  `,
})
export class SairComponent {
  readonly sessao = inject(AuthSessionStore);
  private readonly router = inject(Router);

  /** Quem está logado, quando o backend informa o nome. */
  rotulo(): string {
    const nome = this.sessao.user()?.nome ?? '';
    return nome ? ` (${nome})` : '';
  }

  sair(): void {
    this.sessao.sair();
    // `navigateByUrl` e não `location.href`: recarregar a página inteira
    // descartaria o estado dos outros módulos sem necessidade — a sessão já foi
    // limpa em memória e no `sessionStorage`.
    void this.router.navigateByUrl(ROTA_LOGIN);
  }
}
