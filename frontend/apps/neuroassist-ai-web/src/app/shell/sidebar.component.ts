import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PAC_MODULE_DESCRIPTOR } from '@neuroassist/pac';
import { EVNT_NAV_CONFIG } from '@neuroassist/evnt';
import { ACS_MODULE_DESCRIPTOR } from '@neuroassist/acs';

interface NavItem {
  path: string;
  label: string;
  order: number;
}

// Sourced from each module's descriptor/nav-config (showInNav:true only).
// GEST and EDGE are showInNav:false — composed elsewhere (PAC editor slot,
// topbar chip) rather than routed via the sidebar.
const NAV_ITEMS: NavItem[] = [
  { path: '/pac', label: PAC_MODULE_DESCRIPTOR.menu.label, order: 10 },
  { path: EVNT_NAV_CONFIG.route, label: EVNT_NAV_CONFIG.label, order: EVNT_NAV_CONFIG.order },
  { path: '/acs', label: ACS_MODULE_DESCRIPTOR.menu.label, order: ACS_MODULE_DESCRIPTOR.menu.order },
].sort((a, b) => a.order - b.order);

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="sidebar">
      <div class="sidebar-brand">NeuroAssist AI</div>
      <ul class="sidebar-nav">
        @for (item of navItems; track item.path) {
          <li>
            <a
              [routerLink]="item.path"
              routerLinkActive="active"
              class="sidebar-link"
            >{{ item.label }}</a>
          </li>
        }
      </ul>
    </nav>
  `,
  styles: [`
    .sidebar { display: flex; flex-direction: column; width: 240px; height: 100%; }
    .sidebar-brand { padding: 1rem; font-weight: 600; }
    .sidebar-nav { list-style: none; margin: 0; padding: 0; }
    .sidebar-link { display: block; padding: 0.5rem 1rem; text-decoration: none; }
    .sidebar-link.active { font-weight: 600; }
  `],
})
export class SidebarComponent {
  readonly navItems = NAV_ITEMS;
}
