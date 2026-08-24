import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SairComponent } from '@neuroassist/shared/auth-login';

// =============================================================================
// Raiz da aplicação.
//
// `auth-sair` entra aqui, e não na topbar, porque a topbar (`app/shell/`) é de
// outro agente nesta rodada. O botão se auto-oculta quando não há sessão — sem
// sessão, a única tela é o próprio login. Quando a topbar ganhar o menu de
// perfil, mover para lá.
// =============================================================================

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SairComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <auth-sair />
  `,
})
export class AppShellRootComponent {}
