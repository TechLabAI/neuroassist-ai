import { Component, ChangeDetectionStrategy, inject, computed, signal, OnInit, OnDestroy, effect } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';
import { EventosFacade, ComandosFacade, CuidadoresFacade } from '../core/data-access';
import { PocConfirmarEventoService } from '../core/poc-http-adapters';
import { agoraIso } from '../shared/clock';

function hora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

// Wireframe §3.1: "sem painel de IA lateral" — a IA deste produto (visão computacional
// determinística) não tem representação própria na UI, só o resultado dela nas telas.
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SidebarComponent, TopbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell-layout">
      <!-- Sidebar: visível apenas em telas md+ -->
      <app-sidebar class="shell-sidebar hidden md:flex" />
      <div class="shell-main">
        <app-topbar class="shell-topbar" />
        <!-- Padding inferior no mobile para não ficar atrás da bottom nav -->
        <main class="shell-content pb-16 md:pb-0"><router-outlet /></main>
      </div>
    </div>

    <!-- Bottom nav: visível apenas em mobile (< md) -->
    <nav class="fixed bottom-0 left-0 right-0 z-40 flex border-t border-sidebar-line bg-sidebar md:hidden">
      <a routerLink="/home"      routerLinkActive="text-sidebar-active-ink" class="flex flex-1 flex-col items-center gap-0.5 py-2 text-sidebar-muted transition-colors" [routerLinkActiveOptions]="{exact:false}">
        <span class="material-symbols-outlined text-[24px]">visibility</span>
        <span class="text-[10px] font-semibold">Status</span>
      </a>
      <a routerLink="/historico" routerLinkActive="text-sidebar-active-ink" class="flex flex-1 flex-col items-center gap-0.5 py-2 text-sidebar-muted transition-colors">
        <span class="material-symbols-outlined text-[24px]">history</span>
        <span class="text-[10px] font-semibold">Histórico</span>
      </a>
      <a routerLink="/calibracao" routerLinkActive="text-sidebar-active-ink" class="flex flex-1 flex-col items-center gap-0.5 py-2 text-sidebar-muted transition-colors">
        <span class="material-symbols-outlined text-[24px]">tune</span>
        <span class="text-[10px] font-semibold">Calibração</span>
      </a>
      <a routerLink="/onboarding" routerLinkActive="text-sidebar-active-ink" class="flex flex-1 flex-col items-center gap-0.5 py-2 text-sidebar-muted transition-colors">
        <span class="material-symbols-outlined text-[24px]">settings</span>
        <span class="text-[10px] font-semibold">Config</span>
      </a>
    </nav>

    <!-- Alarm overlay — aparece em qualquer tela quando há evento pendente -->
    @if (eventoPendente(); as evt) {
      <div class="fixed inset-0 z-50 flex flex-col items-center justify-center px-8 text-center"
           [class]="urgenciaDoEvento(evt.comando) === 'emergencia' ? 'bg-red-600' : 'bg-blue-600'">
        <span class="material-symbols-outlined text-[96px] text-white">
          {{ urgenciaDoEvento(evt.comando) === 'emergencia' ? 'emergency_home' : 'notifications_active' }}
        </span>
        <h1 class="mt-6 font-display text-4xl font-extrabold tracking-tight text-white">
          {{ urgenciaDoEvento(evt.comando) === 'emergencia' ? pacienteNome + ' precisa de ajuda' : 'Pedido de ' + pacienteNome }}
        </h1>
        <p class="mt-3 text-xl font-bold text-white">"{{ rotulo(evt.comando) }}"</p>
        <p class="mt-2 text-base font-medium text-white">
          Gesto detectado às {{ hora(evt.timestamp) }}
        </p>
        <button
          (click)="confirmar(evt.id)"
          class="mt-12 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-4 text-base font-bold shadow-lg transition-transform hover:scale-[1.02] active:scale-[0.98]"
          [class]="urgenciaDoEvento(evt.comando) === 'emergencia' ? 'text-red-600' : 'text-blue-600'">
          <span class="material-symbols-outlined text-[22px]">check</span>
          Confirmar que atendi {{ pacienteNome.split(' ')[0] }}
        </button>
      </div>
    }

    <!-- Feedback pós-confirmação — desaparece após 3s -->
    @if (recemConfirmado()) {
      <div class="fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#0d5c3a] px-8 text-center">
        <span class="material-symbols-outlined text-[80px] text-white">check_circle</span>
        <h1 class="mt-5 font-display text-3xl font-bold text-white">Atendimento confirmado</h1>
        <p class="mt-2 text-base text-white/70">Obrigado, {{ primeiroNomeCuidador() }}.</p>
      </div>
    }
  `,
  styles: [`:host { display: contents; }
    .shell-layout { display: grid; grid-template-columns: 1fr;
                    height: 100vh; overflow: hidden; }
    @media (min-width: 768px) {
      .shell-layout { grid-template-columns: auto 1fr; }
    }
    .shell-main { display: flex; flex-direction: column;
                  overflow: hidden; min-width: 0; }
    .shell-content { flex: 1; overflow: auto;
                     background-color: var(--content-bg);
                     background-image: var(--content-mesh); }`],
})
export class ShellComponent implements OnInit, OnDestroy {
  private readonly facade             = inject(EventosFacade);
  private readonly comandosFacade     = inject(ComandosFacade);
  private readonly cuidadoresFacade   = inject(CuidadoresFacade);
  private readonly confirmacaoService = inject(PocConfirmarEventoService);

  protected readonly pacienteNome = 'Márcio Amaral';
  protected readonly hora = hora;

  private pollId: ReturnType<typeof setInterval> | null = null;
  private confirmadoNestaSessaoId = signal<string | null>(null);
  private lastNotifiedEventId: string | null = null;
  private lastLoggedAlarmEventId = signal<string | null>(null);

  constructor() {
    // Paper latency measurement: record alarm_shown_at when overlay first appears.
    // Export log: localStorage.getItem('_neuroassist_alarm_log')
    effect(() => {
      const evt = this.eventoPendente();
      if (evt && evt.id !== this.lastLoggedAlarmEventId()) {
        this.lastLoggedAlarmEventId.set(evt.id);
        const key     = '_neuroassist_alarm_log';
        const existing = JSON.parse(localStorage.getItem(key) ?? '[]') as Array<{ eventId: string; alarmShownAtMs: number }>;
        existing.push({ eventId: evt.id, alarmShownAtMs: Date.now() });
        localStorage.setItem(key, JSON.stringify(existing));
      }
    });
  }

  ngOnInit(): void {
    // Pedir permissão de notificação assim que o shell carrega.
    // O browser só exibe o prompt uma vez; chamadas subsequentes retornam o estado atual.
    if ('Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission();
    }

    void this.facade.load();
    this.pollId = setInterval(() => {
      void this.facade.load({ silent: true }).then(() => this.notificarSeNovo());
    }, 5000);
  }

  ngOnDestroy(): void {
    if (this.pollId !== null) clearInterval(this.pollId);
  }

  private readonly eventosPorUrgencia = computed(() =>
    this.facade.items()
      .filter(e => !e.confirmadoEm)
      .sort((a, b) => {
        // Emergências sempre antes de rotina; dentro de cada tier: mais recente primeiro
        const ua = this.comandosFacade.items().find(c => c.id === a.comando)?.urgencia ?? 'rotina';
        const ub = this.comandosFacade.items().find(c => c.id === b.comando)?.urgencia ?? 'rotina';
        if (ua === ub) return b.timestamp.localeCompare(a.timestamp);
        return ua === 'emergencia' ? -1 : 1;
      }),
  );

  readonly eventoPendente = computed(() =>
    this.eventosPorUrgencia()[0] ?? null,
  );

  protected urgenciaDoEvento(comandoId: string): 'emergencia' | 'rotina' {
    return this.comandosFacade.items().find(c => c.id === comandoId)?.urgencia ?? 'rotina';
  }

  readonly recemConfirmado = computed(() => {
    const id = this.confirmadoNestaSessaoId();
    if (!id) return false;
    return this.facade.items().find(e => e.id === id && e.confirmadoEm != null) != null;
  });

  protected rotulo(comandoId: string): string {
    return this.comandosFacade.items().find(c => c.id === comandoId)?.rotulo ?? 'Preciso de ajuda';
  }

  protected primeiroNomeCuidador(): string {
    const cuidador = this.cuidadoresFacade.items()[0];
    return cuidador?.nome?.split(' ')[0] ?? 'você';
  }

  private notificarSeNovo(): void {
    const evt = this.eventoPendente();
    if (!evt || evt.id === this.lastNotifiedEventId) return;
    this.lastNotifiedEventId = evt.id;

    if (!('Notification' in window) || Notification.permission !== 'granted') return;

    const n = new Notification(`🚨 ${this.pacienteNome} precisa de ajuda`, {
      body: `"${this.rotulo(evt.comando)}" — gesto detectado às ${hora(evt.timestamp)}`,
      icon: '/favicon.ico',
      tag:  `alarme-${evt.id}`,  // evita duplicar se o polling rodar antes da interação
      requireInteraction: true,  // mantém a notificação até o usuário interagir (PC)
    });

    // Clicar na notificação do sistema foca a aba
    n.onclick = () => { window.focus(); n.close(); };
  }

  protected confirmar(id: string): void {
    const now      = agoraIso();
    const cuidador = this.cuidadoresFacade.items()[0]?.id ?? '';
    this.facade.items.update(list =>
      list.map(e => e.id === id ? { ...e, confirmadoEm: now, confirmadoPor: cuidador, feedback: 'real' as const } : e),
    );
    this.confirmadoNestaSessaoId.set(id);
    setTimeout(() => this.confirmadoNestaSessaoId.set(null), 3000);
    this.confirmacaoService.confirmar(id).catch(() => { /* ignora erro no PoC */ });
  }
}
