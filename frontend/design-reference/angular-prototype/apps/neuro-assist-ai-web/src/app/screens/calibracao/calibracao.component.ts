import { Component, ChangeDetectionStrategy, inject, signal, computed, viewChild, ElementRef, OnDestroy, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ComandosFacade, TiposGestoFacade, PacientesFacade } from '../../core/data-access';
import { PocPublicarPerfilService, PocTestarGestoService, PublicarPerfilResult, TesteGestoResult } from '../../core/poc-http-adapters';
import { PocGestureDetector, GestureFrame } from '../../core/poc-gesture-detector';
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

type TesteEstado = 'idle' | 'inicializando' | 'testando' | 'disparando' | 'sucesso' | 'erro';
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

            <!-- Painel de teste ao vivo -->
            <div class="mt-5 rounded-xl border border-line bg-surface-2 p-4">

              <!-- Preview com grade de face landmarks -->
              <div class="relative w-full overflow-hidden rounded-xl bg-black" style="aspect-ratio:4/3">
                <video #videoPreview autoplay muted playsinline
                  class="absolute inset-0 h-full w-full object-cover"
                  [class.opacity-0]="teste() === 'idle' || teste() === 'inicializando' || teste() === 'erro' || teste() === 'sucesso'">
                </video>
                <canvas #canvasOverlay
                  class="absolute inset-0 h-full w-full pointer-events-none"
                  [class.hidden]="teste() === 'idle' || teste() === 'inicializando' || teste() === 'erro' || teste() === 'sucesso'">
                </canvas>

                <!-- Ícone / estado quando câmera está inativa -->
                @if (teste() === 'idle' || teste() === 'inicializando' || teste() === 'erro' || teste() === 'sucesso') {
                  <div class="absolute inset-0 flex flex-col items-center justify-center gap-2">
                    @if (teste() === 'inicializando') {
                      <span class="material-symbols-outlined animate-spin text-[40px] text-white/50">autorenew</span>
                      <span class="text-xs text-white/50">Carregando detector…</span>
                    } @else if (teste() === 'sucesso') {
                      <span class="material-symbols-outlined text-[40px] text-green-400">check_circle</span>
                    } @else if (teste() === 'erro') {
                      <span class="material-symbols-outlined text-[40px] text-red-400">videocam_off</span>
                    } @else {
                      <span class="material-symbols-outlined text-[40px] text-white/20">face</span>
                    }
                  </div>
                }

                <!-- Overlay de disparo (enquanto envia) -->
                @if (teste() === 'disparando') {
                  <div class="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/50 rounded-xl">
                    <span class="material-symbols-outlined animate-spin text-[36px] text-white">autorenew</span>
                    <span class="text-xs text-white/70">Enviando alarme…</span>
                  </div>
                }

                <!-- Contador de piscadas (badge no canto — só para piscar duplo) -->
                @if ((teste() === 'testando' || teste() === 'disparando') && editando()?.primitivoPrincipal === 'tg-piscar-duplo-001') {
                  <div class="absolute top-2 right-2 flex items-center gap-1.5 rounded-full bg-black/60 px-2.5 py-1">
                    <span class="h-2.5 w-2.5 rounded-full transition-colors" [class]="(frame()?.blinkCount ?? 0) >= 1 ? 'bg-brand' : 'bg-white/30'"></span>
                    <span class="h-2.5 w-2.5 rounded-full transition-colors" [class]="(frame()?.blinkCount ?? 0) >= 2 ? 'bg-brand' : 'bg-white/30'"></span>
                    <span class="text-[10px] text-white/60 ml-0.5">piscadas</span>
                  </div>
                }
              </div>

              <!-- Indicadores de face + EAR -->
              @if (teste() === 'testando' || teste() === 'disparando') {
                <div class="mt-3 grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <span class="text-ink-soft">Rosto</span>
                    <p class="mt-0.5 font-medium" [class]="frame()?.faceDetected ? 'text-success-ink' : 'text-ink-faint'">
                      @if (!frame()?.faceDetected) {
                        Posicione o rosto…
                      } @else {
                        {{ statusGesto(editando()?.primitivoPrincipal ?? '', frame()?.blinkCount ?? 0) }}
                      }
                    </p>
                  </div>
                  <div>
                    <span class="text-ink-soft">Abertura dos olhos (EAR)</span>
                    <div class="mt-1.5 flex items-center gap-2">
                      <div class="h-2 flex-1 overflow-hidden rounded-full bg-surface">
                        <div class="h-2 rounded-full transition-all duration-100"
                          [style.width.%]="Math.min(100, (frame()?.earAvg ?? 0) * 300)"
                          [class]="(frame()?.earAvg ?? 0) < 0.22 ? 'bg-alarm' : 'bg-success'">
                        </div>
                      </div>
                      <span class="w-10 text-right font-mono text-ink-faint">
                        {{ (frame()?.earAvg ?? 0).toFixed(2) }}
                      </span>
                    </div>
                  </div>
                </div>
                <p class="mt-2 text-xs text-ink-faint" [innerHTML]="instrucaoGesto(editando()?.primitivoPrincipal ?? '')"></p>
              }

              <!-- Mensagem de resultado / erro -->
              @if (teste() === 'sucesso' && testeResultado(); as r) {
                <div class="mt-3 rounded-lg bg-surface p-3 text-xs">
                  <p class="font-medium text-success-ink">✓ Alarme criado — tier: {{ r.tier }}</p>
                  <p class="mt-0.5 font-mono text-ink-faint">{{ r.eventoId }}</p>
                  <p class="mt-1 text-ink-soft">Acesse <strong>/#/alarme</strong> para confirmar o atendimento.</p>
                </div>
              }
              @if (teste() === 'erro') {
                <p class="mt-3 text-xs font-medium text-danger">{{ testeErroMsg() }}</p>
              }

              <!-- Botões de ação -->
              <div class="mt-3 flex flex-wrap gap-2">
                @if (teste() === 'testando') {
                  <app-ds-button size="sm" (click)="dispararAlarme(form.primitivoPrincipal)">
                    <span class="material-symbols-outlined text-[15px]">send</span>
                    Disparar manualmente
                  </app-ds-button>
                  <app-ds-button size="sm" variant="ghost" (click)="pararTeste()">Parar</app-ds-button>
                } @else if (teste() === 'sucesso' || teste() === 'erro') {
                  <app-ds-button size="sm" variant="secondary" (click)="testar()">
                    <span class="material-symbols-outlined text-[15px]">refresh</span>
                    Testar novamente
                  </app-ds-button>
                } @else if (teste() !== 'inicializando' && teste() !== 'disparando') {
                  <app-ds-button size="sm" variant="secondary" (click)="testar()">
                    <span class="material-symbols-outlined text-[15px]">videocam</span>
                    Testar agora
                  </app-ds-button>
                }
              </div>
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

            <app-ds-card>
              <h3 class="mb-1 font-display text-sm font-semibold text-ink">Perfil de detecção</h3>
              <p class="mb-3 text-xs text-ink-soft">Publicar sincroniza os tipos de gesto com o agente edge.</p>
              @if (publicandoPerfil()) {
                <app-ds-button [disabled]="true" variant="secondary">
                  <span class="material-symbols-outlined animate-spin text-[16px]">autorenew</span>
                  Publicando…
                </app-ds-button>
              } @else if (publicacaoOk(); as resultado) {
                <div class="flex items-center gap-2 text-xs font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[16px] text-success">check_circle</span>
                  Perfil v{{ resultado.version }} publicado
                </div>
                <app-ds-button class="mt-3" variant="secondary" (click)="publicarPerfil()">Republicar</app-ds-button>
              } @else {
                <app-ds-button variant="secondary" (click)="publicarPerfil()">
                  <span class="material-symbols-outlined text-[16px]">sync</span>
                  Publicar perfil de detecção
                </app-ds-button>
              }
              @if (publicacaoErro()) {
                <p class="mt-2 text-xs text-danger">{{ publicacaoErro() }}</p>
              }
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
  private readonly perfilService = inject(PocPublicarPerfilService);
  private readonly testarService = inject(PocTestarGestoService);
  private readonly zone = inject(NgZone);
  protected readonly sensibilidades = SENSIBILIDADES;
  protected readonly Math = Math;

  readonly publicandoPerfil = signal(false);
  readonly publicacaoOk     = signal<PublicarPerfilResult | null>(null);
  readonly publicacaoErro   = signal<string | null>(null);

  readonly testeResultado = signal<TesteGestoResult | null>(null);
  readonly testeErroMsg   = signal<string>('');
  readonly frame          = signal<GestureFrame | null>(null);

  private readonly videoPreview   = viewChild<ElementRef<HTMLVideoElement>>('videoPreview');
  private readonly canvasOverlay  = viewChild<ElementRef<HTMLCanvasElement>>('canvasOverlay');
  private stream:   MediaStream | null = null;
  private detector: PocGestureDetector | null = null;

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
    this.detector?.destroy();
    this.detector = null;
  }

  private pararStream(): void {
    this.detector?.stop();
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    const video = this.videoPreview()?.nativeElement;
    if (video) video.srcObject = null;
    const canvas = this.canvasOverlay()?.nativeElement;
    if (canvas) canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
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

  protected instrucaoGesto(tipoGestoId: string): string {
    switch (tipoGestoId) {
      case 'tg-piscar-duplo-001':    return 'Pisque <strong>duas vezes de forma deliberada</strong> (olho completamente fechado e reaberto em cada piscada, janela de 1.8 s) — o alarme dispara automaticamente.';
      case 'tg-piscar-esquerdo':     return 'Feche e reabra <strong>apenas o olho esquerdo</strong> (olho direito deve permanecer aberto) por ~0.1 s — o alarme dispara automaticamente.';
      case 'tg-piscar-direito':      return 'Feche e reabra <strong>apenas o olho direito</strong> (olho esquerdo deve permanecer aberto) por ~0.1 s — o alarme dispara automaticamente.';
      case 'tg-sobrancelha-dupla':   return 'Levante <strong>as duas sobrancelhas</strong> simultaneamente e mantenha por ~0.2 s — o alarme dispara automaticamente.';
      case 'tg-sobrancelha-direita': return 'Levante <strong>apenas a sobrancelha direita</strong> (esquerda relaxada) e mantenha por ~0.2 s — o alarme dispara automaticamente.';
      case 'tg-sobrancelha-esquerda':return 'Levante <strong>apenas a sobrancelha esquerda</strong> (direita relaxada) e mantenha por ~0.2 s — o alarme dispara automaticamente.';
      default:                        return 'Execute o gesto configurado — o alarme dispara automaticamente quando reconhecido.';
    }
  }

  protected statusGesto(tipoGestoId: string, blinkCount: number): string {
    switch (tipoGestoId) {
      case 'tg-piscar-duplo-001':
        return blinkCount >= 1 ? '1ª piscada ✓ — aguardando 2ª' : 'Detectado ✓ — pisque deliberadamente';
      case 'tg-piscar-esquerdo':
        return 'Detectado ✓ — pisque o olho esquerdo';
      case 'tg-piscar-direito':
        return 'Detectado ✓ — pisque o olho direito';
      case 'tg-sobrancelha-dupla':
        return 'Detectado ✓ — levante as duas sobrancelhas';
      case 'tg-sobrancelha-direita':
        return 'Detectado ✓ — levante a sobrancelha direita';
      case 'tg-sobrancelha-esquerda':
        return 'Detectado ✓ — levante a sobrancelha esquerda';
      default:
        return 'Detectado ✓ — execute o gesto configurado';
    }
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

  private resetTeste(): void {
    this.pararStream();
    this.teste.set('idle');
    this.testeResultado.set(null);
    this.testeErroMsg.set('');
    this.frame.set(null);
  }

  protected novo(): void {
    this.resetTeste();
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
  }

  protected editar(c: Comando): void {
    this.resetTeste();
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
  }

  protected cancelar(): void {
    this.resetTeste();
    this.editando.set(null);
    this.editandoId.set(null);
    this.cancelarNovoTipo();
  }

  protected async testar(): Promise<void> {
    this.testeResultado.set(null);
    this.testeErroMsg.set('');
    this.frame.set(null);

    if (!navigator.mediaDevices?.getUserMedia) {
      this.testeErroMsg.set('Webcam não disponível neste navegador.');
      this.teste.set('erro');
      return;
    }

    this.teste.set('inicializando');
    try {
      // Inicia câmera
      this.stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      const video  = this.videoPreview()?.nativeElement;
      const canvas = this.canvasOverlay()?.nativeElement;
      if (!video || !canvas) throw new Error('elementos de vídeo não encontrados');

      video.srcObject = this.stream;
      await video.play().catch(() => {});

      // Aguarda o primeiro frame real para garantir videoWidth/Height corretos
      await new Promise<void>(resolve => {
        if (video.readyState >= 2) { resolve(); return; }
        video.addEventListener('loadeddata', () => resolve(), { once: true });
      });
      canvas.width  = video.videoWidth  || 640;
      canvas.height = video.videoHeight || 480;

      // Inicializa o detector (reutiliza instância se já existir)
      if (!this.detector) {
        this.detector = new PocGestureDetector(
          (f)           => this.zone.run(() => this.frame.set(f)),
          // Só dispara se o gesto detectado for o configurado no comando em edição
          (tipoGestoId) => {
            if (tipoGestoId === this.editando()?.primitivoPrincipal) {
              this.zone.run(() => void this.dispararAlarme(tipoGestoId));
            }
          },
        );
        await this.detector.init();
      }

      this.teste.set('testando');
      this.detector.start(video, canvas);
    } catch (err) {
      this.pararStream();
      this.testeErroMsg.set('Não foi possível iniciar o detector. Verifique a webcam e a conexão (primeiro uso baixa ~30 MB).');
      this.teste.set('erro');
    }
  }

  protected async dispararAlarme(tipoGestoId: string): Promise<void> {
    if (this.teste() === 'disparando' || this.teste() === 'sucesso') return;
    this.teste.set('disparando');
    this.detector?.stop();
    const profileVersion = this.publicacaoOk()?.version ?? 1;
    const comandoId  = this.editandoId() ?? this.facade.items().find(c => c.primitivoPrincipal === tipoGestoId)?.id ?? 'cmd-desconhecido';
    const pacienteId = this.pacientesFacade.items()[0]?.id ?? 'pac-marcio';
    try {
      const resultado = await this.testarService.disparar(tipoGestoId, profileVersion, comandoId, pacienteId);
      this.pararStream();
      this.testeResultado.set(resultado);
      this.teste.set('sucesso');
    } catch {
      this.pararStream();
      this.testeErroMsg.set('Erro ao enviar ao evnt-service — verifique se está rodando na porta 8083.');
      this.teste.set('erro');
    }
  }

  protected pararTeste(): void {
    this.pararStream();
    this.teste.set('idle');
    this.frame.set(null);
  }

  protected salvar(form: ComandoForm): void {
    this.resetTeste();
    const id = this.editandoId();
    if (id) {
      this.facade.items.update(list => list.map(c => (c.id === id ? { ...c, ...form } : c)));
    } else {
      const pacienteId = this.pacientesFacade.items()[0]?.id ?? '';
      const novoId = `cmd-${form.rotulo.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') || Date.now()}`;
      this.facade.items.update(list => [...list, { id: novoId, pacienteId, ...form }]);
    }
    this.facade.persistir();
    this.editando.set(null);
    this.editandoId.set(null);
  }

  protected remover(id: string): void {
    this.facade.items.update(list => list.filter(c => c.id !== id));
    this.facade.persistir();
  }

  protected async publicarPerfil(): Promise<void> {
    this.publicandoPerfil.set(true);
    this.publicacaoOk.set(null);
    this.publicacaoErro.set(null);
    try {
      const res = await this.perfilService.publicar();
      this.publicacaoOk.set(res);
    } catch {
      this.publicacaoErro.set('Erro ao publicar — verifique se o gest-service está rodando (porta 8082).');
    } finally {
      this.publicandoPerfil.set(false);
    }
  }
}
