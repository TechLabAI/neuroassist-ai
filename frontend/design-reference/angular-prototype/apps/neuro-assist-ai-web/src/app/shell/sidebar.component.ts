import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface NavItem { id: string; label: string; icon: string; route: string; }

/** Navegação diária da cuidadora — ver wireframe §3.2.A. Alarme NÃO é destino de
 * navegação (§4.1): ele substitui a tela ativa automaticamente quando um comando
 * `emergencia` é reconhecido, por isso não aparece aqui. */
const NAV_ITEMS: NavItem[] = [
  { id: 'home', label: 'Status', icon: 'visibility', route: '/home' },
  { id: 'historico', label: 'Histórico', icon: 'history', route: '/historico' },
  { id: 'calibracao', label: 'Calibração', icon: 'tune', route: '/calibracao' },
];

/** Configuração inicial — uso único pelo condutor técnico, não pela cuidadora no dia a dia. */
const SETUP_ITEMS: NavItem[] = [
  { id: 'onboarding', label: 'Configuração inicial', icon: 'settings', route: '/onboarding' },
];

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="flex h-screen w-[248px] flex-col border-r border-sidebar-line bg-sidebar">
      <div class="flex h-24 flex-shrink-0 items-center border-b border-sidebar-line px-6">
        <img class="block w-full max-h-[64px] object-contain object-left" [src]="logoSrc" alt="NeuroAssist AI" />
      </div>
      <nav class="min-h-0 flex-1 overflow-y-auto px-3 py-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        @for (item of navItems; track item.id) {
          <a [routerLink]="item.route"
             routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
             class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-sidebar-muted transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }
        <div class="my-3 border-t border-sidebar-line"></div>
        @for (item of setupItems; track item.id) {
          <a [routerLink]="item.route"
             routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
             class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-xs font-medium text-sidebar-muted/70 transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[18px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }
      </nav>
      <div class="flex flex-shrink-0 items-center gap-3 border-t border-sidebar-line p-4">
        <div class="grid size-8 place-items-center rounded-full bg-sidebar-accent/15 text-xs font-bold text-sidebar-accent">JA</div>
        <div class="min-w-0 leading-tight">
          <div class="truncate text-xs font-semibold text-sidebar-ink">Jamilly Amaral</div>
          <div class="truncate text-[11px] text-sidebar-muted">Cuidadora</div>
        </div>
      </div>
    </aside>
  `,
})
export class SidebarComponent {
  readonly navItems = NAV_ITEMS;
  readonly setupItems = SETUP_ITEMS;
  /** Logo para superfície escura (sidebar é sempre escura). */
  readonly logoSrc = 'assets/logo-white.png';
}
