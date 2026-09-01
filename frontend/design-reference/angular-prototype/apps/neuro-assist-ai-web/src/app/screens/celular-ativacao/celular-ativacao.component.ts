import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { PacientesFacade, DispositivosFacade, SessaoPacienteFacade, CuidadoresFacade } from '../../core/data-access';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';

// Contrato: wireframe.md Tela 07 — Ativação do celular do paciente (Emenda 3, 2026-08-30;
// ADR-012 accepted Opção C, ADR-014 accepted). Extensão do fluxo de pareamento existente
// (Onboarding/Tela 05) para o caso "dispositivo é o celular do próprio paciente" — reaproveita
// o mesmo modelo de identidade de dispositivo (edge.device, ADR-014) para GEST, e provisiona
// separadamente a SessaoPaciente (ADR-012) para CONV. Sessão não expira; revogação é
// obrigatória e sempre visível (constraint SEC-001).

type Pareamento = 'idle' | 'pareando' | 'pareado';

function dataHora(iso: string): string {
  const d = new Date(iso);
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm} às ${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-celular-ativacao',
  standalone: true,
  imports: [DsCardComponent, DsButtonComponent, DsBadgeComponent],
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-7">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Ativar celular do paciente</h1>
        <p class="mt-1 text-sm text-ink-soft">Conduzido pela cuidadora — o paciente não digita credencial nenhuma.</p>
      </header>

      <div class="flex flex-col gap-5">
        <app-ds-card>
          <div class="mb-3 flex items-center gap-2">
            <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">1</span>
            <h2 class="font-display text-sm font-semibold text-ink">Paciente</h2>
          </div>
          <p class="text-sm text-ink">{{ paciente()?.nome ?? '—' }}</p>
        </app-ds-card>

        <app-ds-card>
          <div class="mb-3 flex items-center gap-2">
            <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">2</span>
            <h2 class="font-display text-sm font-semibold text-ink">Celular do paciente</h2>
          </div>
          <div class="flex items-center justify-between gap-3 rounded-lg bg-surface-2 px-4 py-3">
            @if (pareamento() === 'idle') {
              <span class="text-sm text-ink-soft">Nenhum celular pareado ainda.</span>
              <app-ds-button size="sm" variant="secondary" (click)="parear()">Parear celular</app-ds-button>
            } @else if (pareamento() === 'pareando') {
              <span class="flex items-center gap-2 text-sm font-medium text-brand">
                <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Pareando…
              </span>
              <app-ds-badge tone="nf">Aguardando</app-ds-badge>
            } @else {
              <span class="flex items-center gap-2 text-sm font-medium text-success-ink">
                <span class="material-symbols-outlined text-[18px]">check_circle</span>
                Celular pareado como dispositivo edge
              </span>
              <app-ds-badge tone="ok">Online</app-ds-badge>
            }
          </div>
          <p class="mt-2 text-xs text-ink-faint">Reaproveita o mesmo modelo de identidade de dispositivo já usado pela estação fixa (ADR-014) — restrito a Android/Chromium (constraint PLAT-001).</p>
        </app-ds-card>

        <app-ds-card>
          <div class="mb-3 flex items-center gap-2">
            <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">3</span>
            <h2 class="font-display text-sm font-semibold text-ink">Sessão persistente</h2>
          </div>
          <div class="flex items-start gap-2 rounded-lg bg-warning-bg px-4 py-3 text-sm text-warning-ink">
            <span class="material-symbols-outlined text-[18px]">warning</span>
            <span>Esta sessão não expira — o paciente não vai "logar de novo" sozinho. Pode ser revogada a qualquer momento nesta tela.</span>
          </div>
        </app-ds-card>

        <div>
          <app-ds-button [disabled]="pareamento() !== 'pareado'" (click)="ativarSessao()">
            Ativar celular do paciente
          </app-ds-button>
        </div>
      </div>

      <div class="mt-8">
        <h3 class="mb-3 font-display text-sm font-semibold text-ink">Sessões ativas neste paciente</h3>
        @if (sessoesDoPaciente().length === 0) {
          <app-ds-card><p class="text-center text-sm text-ink-soft">Nenhuma sessão de paciente ativa ainda.</p></app-ds-card>
        } @else {
          <div class="flex flex-col gap-3">
            @for (sess of sessoesDoPaciente(); track sess.id) {
              <app-ds-card>
                <div class="flex items-center justify-between gap-4">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[20px] text-ink-soft">smartphone</span>
                    <div>
                      <p class="text-sm font-semibold text-ink">Celular de {{ paciente()?.nome ?? 'paciente' }}</p>
                      @if (sess.status === 'ativa') {
                        <p class="text-xs text-ink-soft">Ativa desde {{ dataHora(sess.provisionadaEm) }}</p>
                      } @else {
                        <p class="text-xs text-ink-faint">Revogada em {{ sess.revogadaEm ? dataHora(sess.revogadaEm) : '—' }}</p>
                      }
                    </div>
                  </div>
                  @if (sess.status === 'ativa') {
                    <app-ds-button size="sm" variant="danger" (click)="revogar(sess.id)">Revogar</app-ds-button>
                  } @else {
                    <app-ds-badge tone="mu">Revogada</app-ds-badge>
                  }
                </div>
              </app-ds-card>
            }
          </div>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CelularAtivacaoComponent {
  private readonly pacientesFacade = inject(PacientesFacade);
  private readonly dispositivosFacade = inject(DispositivosFacade);
  private readonly cuidadoresFacade = inject(CuidadoresFacade);
  protected readonly sessoesFacade = inject(SessaoPacienteFacade);
  protected readonly dataHora = dataHora;

  readonly pareamento = signal<Pareamento>('idle');

  readonly paciente = computed(() => this.pacientesFacade.items()[0] ?? null);
  readonly sessoesDoPaciente = computed(() => {
    const p = this.paciente();
    if (!p) return [];
    return this.sessoesFacade.items()
      .filter(s => s.pacienteId === p.id)
      .sort((a, b) => b.provisionadaEm.localeCompare(a.provisionadaEm));
  });

  protected parear(): void {
    this.pareamento.set('pareando');
    setTimeout(() => this.pareamento.set('pareado'), 1600);
  }

  protected ativarSessao(): void {
    const p = this.paciente();
    const dev = this.dispositivosFacade.items().find(d => d.pacienteId === p?.id);
    const cuidadora = this.cuidadoresFacade.items()[0];
    if (!p || !dev || !cuidadora) return;
    this.sessoesFacade.provisionar(p.id, dev.id, cuidadora.id);
  }

  protected revogar(sessaoId: string): void {
    const cuidadora = this.cuidadoresFacade.items()[0];
    this.sessoesFacade.revogar(sessaoId, cuidadora?.id ?? '');
  }
}
