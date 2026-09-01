import { Component, ChangeDetectionStrategy, inject, signal, computed, viewChild, ElementRef, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ComandosFacade, TiposGestoFacade, PacientesFacade } from '../../core/data-access';
import { Comando, ComandoSensibilidade, ComandoUrgencia } from '../../shared/data-model';
import { DsBadgeComponent } from '../../ui/ds-badge.component';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { DsInputComponent } from '../../ui/ds-input.component';
import { DsKpiComponent } from '../../ui/ds-kpi.component';

// Contrato: .build-plans/calibracao.plan.md  (o implementer lê SÓ o build-plan)
// Wireframe ref: §5.1–5.5 — Calibracao (Emenda 1 — catálogo de comandos; Emenda 2 —
// catálogo de tipos de gesto extensível, não mais fechado em 6 valores)
// Objetivo: gerenciar o catálogo de comandos de Márcio (adicionar/editar/remover) e
// calibrar cada um independentemente — gesto (simples ou composto), sensibilidade,
// urgência (Princípio 2.4). A cuidadora/condutor pode cadastrar um novo TIPO de gesto
// (ex.: "Leitura labial") direto aqui — ver TODO[arquiteto] no Framing §6.1/§9.3:
// cadastrar o nome não implica que o motor de detecção já reconheça o movimento.

interface SensibilidadeOpcao { value: ComandoSensibilidade; label: string; hint: string; }
const SENSIBILIDADES: SensibilidadeOpcao[] = [
  { value: 'baixa', label: 'Baixa', hint: 'precisa de um gesto bem claro' },
  { value: 'media', label: 'Média', hint: 'equilíbrio recomendado' },
  { value: 'alta', label: 'Alta', hint: 'detecta o menor sinal' },
];

type TesteEstado = 'idle' | 'testando' | 'sucesso' | 'erro';
type NovoTipoAlvo = 'principal' | 'secundario' | null;

/** Estado do formulário do editor — espelha os campos editáveis de Comando.
 * primitivoPrincipal/Secundario guardam o id de um TipoGesto (catálogo extensível). */
interface ComandoForm {
  rotulo: string;
  urgencia: ComandoUrgencia;
  primitivoPrincipal: string;
  primitivoSecundario: string | null;
  sensibilidade: ComandoSensibilidade;
  tempoMinimoGestoMs: number;
  tempoMaximoGestoMs: number;
}

@Component({
  selector: 'app-calibracao',
  standalone: true,
  imports: [FormsModule, DsBadgeComponent, DsCardComponent, DsButtonComponent, DsInputComponent, DsKpiComponent],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Calibração — Márcio Amaral</h1>
          <p class="mt-1 text-sm text-ink-soft">Catálogo de comandos: cada gesto mapeado a uma necessidade, calibrado individualmente.</p>
        </div>
        @if (!editando()) {
          <app-ds-button (click)="novo()">
            <span class="material-symbols-outlined text-[18px]">add</span>
            Novo comando
          </app-ds-button>
        }
      </header>

      @if (loading()) {
        <p class="text-sm text-ink-faint">Carregando catálogo…</p>
      } @else if (editando(); as form) {
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <!-- CommandEditor -->
          <app-ds-card>
            <h2 class="mb-4 font-display text-sm font-semibold text-ink">
              {{ editandoId() ? 'Editar comando' : 'Novo comando' }}
            </h2>

            <label class="mb-1 block text-xs font-medium text-ink-soft">Rótulo (a palavra que a família usa)</label>
            <app-ds-input placeholder='Ex.: "água", "Sapo"...' [(value)]="form.rotulo" icon="edit" />
            <p class="mt-1.5 text-xs text-ink-faint">Texto livre — o sistema não interpreta o significado, só armazena e exibe.</p>

            <div class="mt-5">
              <label class="mb-2 block text-xs font-medium text-ink-soft">Urgência</label>
              <div class="flex items-center gap-2">
                <button
                  (click)="form.urgencia = 'emergencia'"
                  class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center text-sm font-semibold transition-colors"
                  [class]="form.urgencia === 'emergencia' ? 'border-alarm bg-alarm-bg text-alarm' : 'border-line text-ink-soft hover:bg-surface-2'">
                  Emergência
                </button>
                <button
                  (click)="form.urgencia = 'rotina'"
                  class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center text-sm font-semibold transition-colors"
                  [class]="form.urgencia === 'rotina' ? 'border-brand bg-brand-subtle text-brand' : 'border-line text-ink-soft hover:bg-surface-2'">
                  Rotina
                </button>
              </div>
              <p class="mt-1.5 text-xs text-ink-faint">
                @if (form.urgencia === 'emergencia') {
                  Dispara o alarme em tela cheia — exige confirmação imediata da cuidadora.
                } @else {
                  Aparece como um aviso discreto, sem interromper a tela — fica registrado no histórico.
                }
              </p>
            </div>

            <div class="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label class="mb-1 block text-xs font-medium text-ink-soft">Gesto principal</label>
                @if (novoTipoAberto() === 'principal') {
                  <div class="flex items-center gap-2">
                    <input [(ngModel)]="novoTipoNome" placeholder='Ex.: "Leitura labial"'
                      class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand" />
                    <button (click)="confirmarNovoTipo(form)" [disabled]="!novoTipoNome.trim()" class="flex-shrink-0 text-xs font-semibold text-brand hover:text-brand-strong disabled:opacity-40">salvar</button>
                    <button (click)="cancelarNovoTipo()" class="flex-shrink-0 text-xs font-semibold text-ink-soft hover:text-ink">×</button>
                  </div>
                } @else {
                  <div class="flex items-center gap-2">
                    <select [ngModel]="form.primitivoPrincipal" (ngModelChange)="form.primitivoPrincipal = $any($event)"
                      class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand">
                      @for (t of tiposGesto(); track t.id) {
                        <option [value]="t.id">{{ t.nome }}</option>
                      }
                    </select>
                    <button (click)="abrirNovoTipo('principal')" title="Cadastrar novo tipo de gesto"
                      class="grid size-9 flex-shrink-0 place-items-center rounded-md border-[1.5px] border-dashed border-line text-ink-soft hover:bg-brand-subtle hover:text-brand">
                      <span class="material-symbols-outlined text-[18px]">add</span>
                    </button>
                  </div>
                }
              </div>

              <div>
                @if (novoTipoAberto() === 'secundario') {
                  <label class="mb-1 block text-xs font-medium text-ink-soft">Novo tipo (segundo gesto)</label>
                  <div class="flex items-center gap-2">
                    <input [(ngModel)]="novoTipoNome" placeholder='Ex.: "Leitura labial"'
                      class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand" />
                    <button (click)="confirmarNovoTipo(form)" [disabled]="!novoTipoNome.trim()" class="flex-shrink-0 text-xs font-semibold text-brand hover:text-brand-strong disabled:opacity-40">salvar</button>
                    <button (click)="cancelarNovoTipo()" class="flex-shrink-0 text-xs font-semibold text-ink-soft hover:text-ink">×</button>
                  </div>
                } @else if (form.primitivoSecundario === null) {
                  <label class="mb-1 block text-xs font-medium text-ink-soft">&nbsp;</label>
                  <button (click)="form.primitivoSecundario = proximoTipo(form.primitivoPrincipal)"
                    class="flex h-9 w-full items-center justify-center rounded-md border-[1.5px] border-dashed border-line text-xs font-semibold text-brand hover:bg-brand-subtle">
                    + Adicionar segundo gesto (combinação)
                  </button>
                } @else {
                  <label class="mb-1 block text-xs font-medium text-ink-soft">Segundo gesto (combinação)</label>
                  <div class="flex items-center gap-2">
                    <select [ngModel]="form.primitivoSecundario" (ngModelChange)="form.primitivoSecundario = $any($event)"
                      class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand">
                      @for (t of tiposGesto(); track t.id) {
                        <option [value]="t.id">{{ t.nome }}</option>
                      }
                    </select>
                    <button (click)="abrirNovoTipo('secundario')" title="Cadastrar novo tipo de gesto"
                      class="grid size-9 flex-shrink-0 place-items-center rounded-md border-[1.5px] border-dashed border-line text-ink-soft hover:bg-brand-subtle hover:text-brand">
                      <span class="material-symbols-outlined text-[18px]">add</span>
                    </button>
                    <button (click)="form.primitivoSecundario = null" class="flex-shrink-0 text-xs font-semibold text-danger hover:text-danger">remover</button>
                  </div>
                }
              </div>
            </div>
            <p class="mt-2 text-xs text-ink-faint">
              Não achou o gesto na lista? Use o <span class="material-symbols-outlined align-middle text-[13px]">add</span> para cadastrar um tipo novo — o catálogo não é fechado.
            </p>

            <div class="mt-5">
              <label class="mb-2 block text-xs font-medium text-ink-soft">Sensibilidade</label>
              <div class="flex items-center gap-2">
                @for (s of sensibilidades; track s.value) {
                  <button
                    (click)="form.sensibilidade = s.value"
                    class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center transition-colors"
                    [class]="form.sensibilidade === s.value ? 'border-brand bg-brand-subtle' : 'border-line hover:bg-surface-2'">
                    <div class="text-sm font-semibold" [class]="form.sensibilidade === s.value ? 'text-brand' : 'text-ink'">{{ s.label }}</div>
                  </button>
                }
              </div>
              <p class="mt-2 text-center text-xs text-ink-faint">{{ sensibilidadeHint(form.sensibilidade) }}</p>
            </div>

            <div class="mt-5 flex items-center gap-4 rounded-lg bg-surface-2 p-4">
              <div class="relative grid h-20 w-28 flex-shrink-0 place-items-center overflow-hidden rounded-xl bg-surface">
                <video #videoPreview class="h-full w-full object-cover" [class.hidden]="teste() !== 'testando'" autoplay muted playsinline></video>
                @if (teste() !== 'testando') {
                  <span class="material-symbols-outlined text-[28px]"
                    [class]="teste() === 'sucesso' ? 'text-success' : teste() === 'erro' ? 'text-danger' : 'text-ink-faint'">
                    {{ teste() === 'sucesso' ? 'check_circle' : teste() === 'erro' ? 'videocam_off' : 'videocam' }}
                  </span>
                }
              </div>
              <div class="flex-1 text-xs">
                @if (teste() === 'idle') {
                  <span class="text-ink-soft">
                    @if (form.primitivoSecundario) {
                      Faça os dois gestos em sequência — testar a combinação reduz o risco de falso negativo.
                    } @else {
                      Faça o gesto em frente à webcam para confirmar que está sendo reconhecido.
                    }
                  </span>
                } @else if (teste() === 'testando') {
                  <span class="font-medium text-brand">Câmera ativa — faça o gesto agora…</span>
                } @else if (teste() === 'erro') {
                  <span class="font-medium text-danger">Não foi possível acessar a webcam. Verifique a permissão do navegador e tente novamente.</span>
                } @else {
                  <span class="font-medium text-success-ink">Gesto reconhecido com sucesso!</span>
                }
              </div>
              @if (teste() === 'testando') {
                <app-ds-button size="sm" variant="ghost" (click)="pararTeste()">Parar</app-ds-button>
              } @else {
                <app-ds-button size="sm" variant="secondary" (click)="testar()">Testar agora</app-ds-button>
              }
            </div>
          </app-ds-card>

          <!-- Coluna lateral: pré-visualização + ações -->
          <div class="flex flex-col gap-6">
            <app-ds-card>
              <h3 class="mb-3 font-display text-sm font-semibold text-ink">Pré-visualização</h3>
              <div class="flex items-center gap-2">
                <span class="text-sm font-semibold text-ink">"{{ form.rotulo || '—' }}"</span>
                <app-ds-badge [tone]="form.urgencia === 'emergencia' ? 'er' : 'nf'">{{ form.urgencia }}</app-ds-badge>
              </div>
              <p class="mt-2 text-xs text-ink-faint">{{ gestoDescricaoForm(form) }}</p>
              <div class="mt-2.5 flex items-center justify-between text-xs">
                <span class="text-ink-soft">Sensibilidade</span>
                <span class="font-medium text-ink">{{ form.sensibilidade }}</span>
              </div>
              <div class="mt-1.5 flex items-center justify-between text-xs">
                <span class="text-ink-soft">Teste ao vivo</span>
                <span class="font-medium text-ink">{{ teste() === 'sucesso' ? 'Reconhecido ✓' : 'Não testado' }}</span>
              </div>
            </app-ds-card>

            <app-ds-card>
              <p class="text-xs text-ink-soft">Salve para adicionar ao catálogo — as mudanças só têm efeito depois de salvas.</p>
              <div class="mt-4 flex flex-col gap-2">
                <app-ds-button [disabled]="!form.rotulo.trim()" (click)="salvar(form)">Salvar comando</app-ds-button>
                <app-ds-button variant="ghost" (click)="cancelar()">Cancelar</app-ds-button>
              </div>
            </app-ds-card>
          </div>
        </div>
      } @else {
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <!-- CommandList -->
          <div>
            @if (comandos().length === 0) {
              <app-ds-card>
                <div class="flex flex-col items-center py-10 text-center">
                  <span class="material-symbols-outlined text-[40px] text-ink-faint">sentiment_neutral</span>
                  <p class="mt-3 text-sm text-ink-soft">Nenhum comando configurado ainda.</p>
                  <app-ds-button class="mt-4" (click)="novo()">Adicionar primeiro comando</app-ds-button>
                </div>
              </app-ds-card>
            } @else {
              <div class="flex flex-col gap-3">
                @for (c of comandos(); track c.id) {
                  <app-ds-card>
                    <div class="flex items-center justify-between gap-4">
                      <div class="flex items-center gap-3">
                        <span class="material-symbols-outlined text-[22px]" [class]="c.urgencia === 'emergencia' ? 'text-alarm' : 'text-info'">
                          {{ c.urgencia === 'emergencia' ? 'emergency_home' : 'notifications' }}
                        </span>
                        <div>
                          <div class="flex items-center gap-2">
                            <span class="text-sm font-semibold text-ink">"{{ c.rotulo }}"</span>
                            <app-ds-badge [tone]="c.urgencia === 'emergencia' ? 'er' : 'nf'">{{ c.urgencia }}</app-ds-badge>
                          </div>
                          <p class="mt-0.5 text-xs text-ink-faint">
                            {{ gestoDescricao(c) }} · sensibilidade {{ c.sensibilidade }}
                          </p>
                        </div>
                      </div>
                      <div class="flex items-center gap-1">
                        <button (click)="editar(c)" class="grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink" title="Editar">
                          <span class="material-symbols-outlined text-[18px]">edit</span>
                        </button>
                        <button (click)="remover(c.id)" class="grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-danger-bg hover:text-danger" title="Remover">
                          <span class="material-symbols-outlined text-[18px]">delete</span>
                        </button>
                      </div>
                    </div>
                  </app-ds-card>
                }
              </div>
            }
          </div>

          <!-- Coluna lateral: resumo do catálogo -->
          <div class="flex flex-col gap-6">
            <div class="grid grid-cols-2 gap-4 lg:grid-cols-1">
              <app-ds-kpi label="Comandos" [value]="comandos().length" kind="tint" />
              <app-ds-kpi label="Emergência" [value]="totalEmergencia()" kind="tint" />
            </div>
            <app-ds-card>
              <h3 class="mb-3 font-display text-sm font-semibold text-ink">Tipos de gesto cadastrados</h3>
              <div class="flex flex-wrap gap-1.5">
                @for (t of tiposGesto(); track t.id) {
                  <app-ds-badge tone="mu">{{ t.nome }}</app-ds-badge>
                }
              </div>
              <p class="mt-3 text-xs text-ink-soft">
                Cada comando é um gesto (simples ou combinado) mapeado a uma necessidade específica de Márcio.
                O rótulo é a palavra que a família já usa. O catálogo de tipos de gesto é extensível — novos tipos
                podem ser cadastrados direto no editor de comando.
              </p>
            </app-ds-card>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalibracaoComponent implements OnDestroy {
  private readonly facade = inject(ComandosFacade);
  private readonly tiposGestoFacade = inject(TiposGestoFacade);
  private readonly pacientesFacade = inject(PacientesFacade);
  protected readonly sensibilidades = SENSIBILIDADES;

  private readonly videoPreview = viewChild<ElementRef<HTMLVideoElement>>('videoPreview');
  private stream: MediaStream | null = null;
  private testeTimeoutId: ReturnType<typeof setTimeout> | null = null;

  readonly loading = computed(() => this.facade.loading() || this.tiposGestoFacade.loading() || this.pacientesFacade.loading());
  readonly comandos = computed(() => this.facade.items());
  readonly tiposGesto = computed(() => this.tiposGestoFacade.items());
  readonly totalEmergencia = computed(() => this.comandos().filter(c => c.urgencia === 'emergencia').length);

  readonly editando = signal<ComandoForm | null>(null);
  readonly editandoId = signal<string | null>(null);
  readonly teste = signal<TesteEstado>('idle');

  readonly novoTipoAberto = signal<NovoTipoAlvo>(null);
  novoTipoNome = '';

  ngOnDestroy(): void {
    this.pararStream();
  }

  private pararStream(): void {
    if (this.testeTimeoutId !== null) {
      clearTimeout(this.testeTimeoutId);
      this.testeTimeoutId = null;
    }
    this.stream?.getTracks().forEach(track => track.stop());
    this.stream = null;
    const video = this.videoPreview()?.nativeElement;
    if (video) video.srcObject = null;
  }

  private nomeDoTipo(id: string): string {
    return this.tiposGesto().find(t => t.id === id)?.nome ?? id;
  }

  protected gestoDescricao(c: Comando): string {
    const principal = this.nomeDoTipo(c.primitivoPrincipal);
    return c.primitivoSecundario ? `${principal} + ${this.nomeDoTipo(c.primitivoSecundario)}` : principal;
  }

  protected gestoDescricaoForm(form: ComandoForm): string {
    const principal = this.nomeDoTipo(form.primitivoPrincipal);
    return form.primitivoSecundario ? `${principal} + ${this.nomeDoTipo(form.primitivoSecundario)}` : principal;
  }

  protected sensibilidadeHint(v: ComandoSensibilidade): string {
    return this.sensibilidades.find(s => s.value === v)?.hint ?? '';
  }

  protected proximoTipo(atual: string): string {
    const tipos = this.tiposGesto();
    return tipos.find(t => t.id !== atual)?.id ?? tipos[0]?.id ?? atual;
  }

  protected abrirNovoTipo(alvo: NovoTipoAlvo): void {
    this.novoTipoNome = '';
    this.novoTipoAberto.set(alvo);
  }

  protected cancelarNovoTipo(): void {
    this.novoTipoNome = '';
    this.novoTipoAberto.set(null);
  }

  protected confirmarNovoTipo(form: ComandoForm): void {
    const nome = this.novoTipoNome.trim();
    if (!nome) return;
    const id = this.tiposGestoFacade.adicionar(nome);
    if (this.novoTipoAberto() === 'principal') {
      form.primitivoPrincipal = id;
    } else if (this.novoTipoAberto() === 'secundario') {
      form.primitivoSecundario = id;
    }
    this.cancelarNovoTipo();
  }

  protected novo(): void {
    this.pararStream();
    this.editandoId.set(null);
    this.editando.set({
      rotulo: '',
      urgencia: 'rotina',
      primitivoPrincipal: this.tiposGesto()[0]?.id ?? '',
      primitivoSecundario: null,
      sensibilidade: 'media',
      tempoMinimoGestoMs: 400,
      tempoMaximoGestoMs: 1500,
    });
    this.teste.set('idle');
  }

  protected editar(c: Comando): void {
    this.pararStream();
    this.editandoId.set(c.id);
    this.editando.set({
      rotulo: c.rotulo,
      urgencia: c.urgencia,
      primitivoPrincipal: c.primitivoPrincipal,
      primitivoSecundario: c.primitivoSecundario,
      sensibilidade: c.sensibilidade,
      tempoMinimoGestoMs: c.tempoMinimoGestoMs,
      tempoMaximoGestoMs: c.tempoMaximoGestoMs,
    });
    this.teste.set('idle');
  }

  protected cancelar(): void {
    this.pararStream();
    this.editando.set(null);
    this.editandoId.set(null);
    this.cancelarNovoTipo();
  }

  /** Liga a webcam de verdade (getUserMedia) para o teste ao vivo. Não há reconhecimento
   * real de gesto no protótipo — só a câmera ligada, para dar feedback tátil concreto de
   * que "a webcam está vendo você" antes de simular o resultado (ver LBC §12 Emenda 1:
   * risco de falso negativo em gesto composto é decisão de arquitetura, não de UI). */
  protected async testar(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      this.teste.set('erro');
      return;
    }
    this.teste.set('testando');
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      const video = this.videoPreview()?.nativeElement;
      if (video) {
        video.srcObject = this.stream;
        await video.play().catch(() => {});
      }
      this.testeTimeoutId = setTimeout(() => {
        this.pararStream();
        this.teste.set('sucesso');
      }, 2500);
    } catch {
      this.pararStream();
      this.teste.set('erro');
    }
  }

  protected pararTeste(): void {
    this.pararStream();
    this.teste.set('idle');
  }

  protected salvar(form: ComandoForm): void {
    this.pararStream();
    const id = this.editandoId();
    if (id) {
      this.facade.items.update(list => list.map(c => (c.id === id ? { ...c, ...form } : c)));
    } else {
      const pacienteId = this.pacientesFacade.items()[0]?.id ?? '';
      const novoId = `cmd-${form.rotulo.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') || Date.now()}`;
      this.facade.items.update(list => [...list, { id: novoId, pacienteId, ...form }]);
    }
    this.editando.set(null);
    this.editandoId.set(null);
    // TODO[fe-autopilot]: persistir catálogo e sincronizar com o dispositivo edge no próximo heartbeat
  }

  protected remover(id: string): void {
    this.facade.items.update(list => list.filter(c => c.id !== id));
  }
}
