import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PAC_MODULE_DESCRIPTOR } from '@neuroassist/pac';
import { EVNT_NAV_CONFIG } from '@neuroassist/evnt';
import { ACS_NAV_CONFIG } from '@neuroassist/acs';
import { DASH_NAV_CONFIG, DashHomeFacade } from '@neuroassist/dash';
import { HIST_NAV_CONFIG } from '@neuroassist/hist';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  order: number;
}

// ---------------------------------------------------------------------------
// SidebarComponent — navegação diária da cuidadora (wireframe §3.2.A).
//
// Os itens vêm dos nav-configs de cada módulo (showInNav:true), não de uma lista
// local: rótulo, ícone e ordem são propriedade do módulo dono. GEST e EDGE são
// showInNav:false — compõem-se em outros lugares (slot do editor PAC, chip da
// topbar), não no menu.
//
// PAC é a exceção: PAC_MODULE_DESCRIPTOR.menu não declara `route` nem `order`
// (só showInNav/label/icon), então os dois ficam aqui. Quando o pipeline do PAC
// completar o descriptor, remover o override.
//
// O alarme NÃO é destino de navegação (§4.1): substitui a tela ativa quando um
// comando `emergencia` é reconhecido — por isso EVNT aparece aqui como o feed de
// rotina ("Eventos"), e o overlay vive no app-shell.
// ---------------------------------------------------------------------------

const NAV_ITEMS: NavItem[] = [
  {
    path: DASH_NAV_CONFIG.route,
    label: DASH_NAV_CONFIG.label,
    icon: DASH_NAV_CONFIG.icon,
    order: DASH_NAV_CONFIG.order,
  },
  {
    path: ACS_NAV_CONFIG.route,
    label: ACS_NAV_CONFIG.label,
    icon: ACS_NAV_CONFIG.icon,
    order: ACS_NAV_CONFIG.order,
  },
  {
    path: HIST_NAV_CONFIG.route,
    label: HIST_NAV_CONFIG.label,
    icon: HIST_NAV_CONFIG.icon,
    order: HIST_NAV_CONFIG.order,
  },
  {
    path: '/pac',
    label: PAC_MODULE_DESCRIPTOR.menu.label,
    icon: PAC_MODULE_DESCRIPTOR.menu.icon,
    order: 10,
  },
  {
    path: EVNT_NAV_CONFIG.route,
    label: EVNT_NAV_CONFIG.label,
    icon: EVNT_NAV_CONFIG.icon,
    order: EVNT_NAV_CONFIG.order,
  },
].sort((a, b) => a.order - b.order);

/** Configuração inicial — uso único pelo condutor técnico, não pela cuidadora no dia a dia. */
const SETUP_ITEMS: NavItem[] = [
  { path: '/onboarding', label: 'Configuração inicial', icon: 'settings', order: 100 },
];

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="flex h-full w-[248px] flex-col border-r border-sidebar-line bg-sidebar">
      <div class="flex h-24 flex-shrink-0 items-center border-b border-sidebar-line px-6">
        <img
          class="block max-h-[64px] w-full object-contain object-left"
          src="assets/logo-white.png"
          alt="NeuroAssist AI" />
      </div>

      <nav class="min-h-0 flex-1 overflow-y-auto px-3 py-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        @for (item of navItems; track item.path) {
          <a
            [routerLink]="item.path"
            [routerLinkActiveOptions]="{ exact: item.path === '/' }"
            routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-sidebar-muted transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }

        <div class="my-3 border-t border-sidebar-line"></div>

        @for (item of setupItems; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-xs font-medium text-sidebar-muted/70 transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[18px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }
      </nav>

      <div class="flex flex-shrink-0 items-center gap-3 border-t border-sidebar-line p-4">
        <div class="grid size-8 place-items-center rounded-full bg-sidebar-accent/15 text-xs font-bold text-sidebar-active-ink">
          {{ iniciais() }}
        </div>
        <div class="min-w-0 leading-tight">
          <div class="truncate text-xs font-semibold text-sidebar-ink">{{ cuidadora() }}</div>
          <div class="truncate text-[11px] text-sidebar-muted">Cuidadora</div>
        </div>
      </div>
    </aside>
  `,
})
export class SidebarComponent {
  readonly navItems = NAV_ITEMS;
  readonly setupItems = SETUP_ITEMS;

  /** Nome da cuidadora logada — mesmo read-model que a home DASH usa. */
  private readonly dash = inject(DashHomeFacade);
  protected readonly cuidadora = computed(() => this.dash.caregiverName() || '—');
  protected readonly iniciais = computed(() => iniciaisDe(this.dash.caregiverName()));
}

/** "Jamilly Amaral" → "JA". Vazio → "—". */
function iniciaisDe(nome: string): string {
  const partes = nome.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '—';
  const primeira = partes[0][0] ?? '';
  const ultima = partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : '';
  return (primeira + ultima).toUpperCase();
}
