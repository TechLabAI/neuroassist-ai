import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
  output,
} from '@angular/core';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { PacDsCardComponent } from '../ui/ds-card.component';
import { PacDsButtonComponent } from '../ui/ds-button.component';
import { PacDsInputComponent } from '../ui/ds-input.component';

// ---------------------------------------------------------------------------
// PatientRegisterComponent — SPEC-02 §3, promoted from onboarding.component.ts step 2
//
// PAC owns Paciente registration (authoring source — ADR-004).
// This component is NOT a routed screen — it is composed by ACS onboarding
// wizard (step 2 of the wizard) via ng-content or direct import at scope:web.
//
// Routes: none (embedded surface — generator-config.yml feature-patient-register.routes: [])
// ---------------------------------------------------------------------------

@Component({
  selector: 'pac-patient-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PacDsCardComponent, PacDsButtonComponent, PacDsInputComponent],
  template: `
    <pac-ds-card>
      <div class="mb-3 flex items-center gap-2">
        <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">
          <ng-content select="[slot=step-number]">2</ng-content>
        </span>
        <h2 class="font-display text-sm font-semibold text-ink">Paciente</h2>
      </div>

      @if (facade.patient(); as p) {
        <!-- Patient exists — show read state with edit option -->
        <div class="flex items-center justify-between gap-3 rounded-lg bg-surface-2 px-4 py-3">
          <div>
            <p class="text-sm font-semibold text-ink">{{ p.nome }}</p>
            <p class="text-xs text-ink-faint">Paciente registrado</p>
          </div>
          <button
            (click)="enterEditMode(p.nome)"
            class="text-xs font-semibold text-brand hover:text-brand-strong">
            Editar
          </button>
        </div>
      } @else if (editing()) {
        <!-- Edit / create form -->
        <pac-ds-input
          placeholder="Nome do paciente"
          [value]="nomeInput()"
          (valueChange)="nomeInput.set($event)"
          icon="person" />
        @if (facade.saveState() === 'error') {
          <p class="mt-1.5 text-xs text-danger">Erro ao salvar. Verifique sua conexão e tente novamente.</p>
        }
        <div class="mt-4 flex gap-3">
          <pac-ds-button
            [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
            (click)="onSave()">
            @if (facade.saveState() === 'saving') {
              <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
              Salvando…
            } @else {
              Salvar paciente
            }
          </pac-ds-button>
          @if (facade.patient()) {
            <pac-ds-button variant="ghost" (click)="cancelEdit()">Cancelar</pac-ds-button>
          }
        </div>
      } @else {
        <!-- No patient yet — prompt to create -->
        <pac-ds-input
          placeholder="Nome do paciente"
          [value]="nomeInput()"
          (valueChange)="nomeInput.set($event)"
          icon="person" />
        <div class="mt-4">
          <pac-ds-button
            [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
            (click)="onSave()">
            @if (facade.saveState() === 'saving') {
              <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
              Salvando…
            } @else {
              Registrar paciente
            }
          </pac-ds-button>
        </div>
      }

      @if (facade.saveState() === 'saved') {
        <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink">
          <span class="material-symbols-outlined text-[18px]">check_circle</span>
          Paciente registrado com sucesso
        </p>
        <!-- ADR-005: device-ack awareness — patient is registered but catalog sync is pending -->
        <p class="mt-1 text-xs text-ink-faint">
          O catálogo de comandos estará pendente de sincronização até o próximo heartbeat do dispositivo.
        </p>
      }
    </pac-ds-card>
  `,
})
export class PatientRegisterComponent implements OnInit {
  protected readonly facade = inject(PacPacienteFacade);

  /** Emitted once the patient is successfully registered */
  readonly registered = output<{ patientId: string; nome: string }>();

  protected readonly nomeInput = signal('');
  protected readonly editing = signal(false);

  readonly isDone = computed(() => this.facade.patient() !== null);

  ngOnInit(): void {
    this.facade.loadCurrent();
  }

  protected enterEditMode(currentNome: string): void {
    this.nomeInput.set(currentNome);
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.nomeInput.set(this.facade.patient()?.nome ?? '');
  }

  protected onSave(): void {
    const nome = this.nomeInput().trim();
    if (!nome) return;
    const existing = this.facade.patient();
    if (existing) {
      // Update existing patient (for edit mode)
      // TODO[PAC-FE-019]: use facade.update() with ETag when exposing update in layer 2
      this.facade.save({ nome });
    } else {
      this.facade.save({ nome });
    }
    this.editing.set(false);
  }
}
