import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';

// Wireframe §3.1: "sem painel de IA lateral" — a IA deste produto (visão computacional
// determinística) não tem representação própria na UI, só o resultado dela nas telas.
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell-layout">
      <app-sidebar class="shell-sidebar" />
      <div class="shell-main">
        <app-topbar class="shell-topbar" />
        <main class="shell-content"><router-outlet /></main>
      </div>
    </div>
  `,
  styles: [`:host { display: contents; }
    .shell-layout { display: grid; grid-template-columns: auto 1fr;
                    height: 100vh; overflow: hidden; }
    .shell-main { display: flex; flex-direction: column;
                  overflow: hidden; min-width: 0; }
    .shell-content { flex: 1; overflow: auto;
                     background-color: var(--content-bg);
                     background-image: var(--content-mesh); }`],
})
export class ShellComponent {}
