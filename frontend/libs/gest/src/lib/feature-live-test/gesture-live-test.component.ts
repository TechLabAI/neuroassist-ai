import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  inject,
  effect,
  viewChild,
  ElementRef,
  OnDestroy,
  computed,
} from '@angular/core';
import { ComandoDraft, GestureTestOutcome, guidanceDe } from '@neuroassist/shared/contracts';
import { EDGE_DETECTION_PORT } from '../api/edge-detection.port';
import { GestureTestFacade } from '../gest.facade';
import { CameraPreferenceService } from '../detection/camera-preference.service';

// @neuroassist/shared/ui-feedback is a Wave-0 stub — no spinner/error components yet.
// Inline CSS fallbacks are used below (gest-loading, gest-error, gest-empty) until the
// lib is fleshed out. See docs/architecture/frontend/libs/shared/ui-feedback.

// ---------------------------------------------------------------------------
// GestureLiveTestComponent — GEST-FE-011 (smart container)
// Composed into PAC's [slot=live-test] and the onboarding gesto step via
// GestShellIntegrationService.resolveLiveTestSlot() — never imported directly
// by scope:pac/scope:acs (GEST-INT-002 nx-boundary).
//
// Consolidates the Wave-3 presentational pieces (preview frame, state badge,
// composite step indicator, degraded banner, controls) inline for now —
// pragmatic-first; splitting into separate components (GEST-FE-012..016) is a
// non-breaking follow-up since this component's @Input/@Output surface won't
// change.
// ---------------------------------------------------------------------------

@Component({
  selector: 'gest-gesture-live-test',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="gest-live-test rounded-lg border border-line p-4">
      <!-- GEST-FE-018: empty state — shown when no test has been started yet (facade idle, no commandDraftRef).
           Guides the caregiver to select a command in the PAC editor before launching the test. -->
      @if (!facade.state().commandDraftRef && facade.testState() === 'idle') {
        <p class="gest-empty mb-3 text-sm text-ink-soft">
          Selecione um comando para iniciar o teste de gesto.
        </p>
      }

      <!-- Seletor de câmera (ADR-007 follow-up 2). Só aparece com mais de uma
           câmera: com uma só, escolher não é decisão, é ruído. -->
      @if (cameraPref.cameras().length > 1 && facade.testState() !== 'testing') {
        <label class="mb-3 flex items-center gap-2 text-xs text-ink-soft">
          <span class="material-symbols-outlined text-[16px]">videocam</span>
          <span>Câmera</span>
          <select
            class="flex-1 rounded-lg border border-field-line bg-field px-2 py-1 text-xs text-ink"
            [value]="cameraPref.selected() ?? ''"
            (change)="onCameraChange($event)"
            data-testid="gest-camera-select">
            <option value="">Padrão do navegador</option>
            @for (cam of cameraPref.cameras(); track cam.deviceId) {
              <option [value]="cam.deviceId">{{ cam.label }}</option>
            }
          </select>
        </label>
      }

      <!-- Watchdog (ADR-007 follow-up 3): sem supervisor de processo, detecção
           parada em silêncio é o pior modo de falha. Enquanto o teste está ativo
           a UI diz, sem ambiguidade, se o motor está batendo. -->
      @if (facade.testState() === 'testing' && facade.previewStream()) {
        @if (!facade.detectionStarted()) {
          <p class="mb-3 flex items-center gap-2 text-xs text-ink-soft"
             aria-live="polite" data-testid="gest-detection-starting">
            <span
              class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
              aria-hidden="true"></span>
            Iniciando detecção…
          </p>
        } @else if (facade.detectionAlive()) {
          <p class="mb-3 flex items-center gap-2 text-xs font-medium text-success-ink"
             aria-live="polite" data-testid="gest-detection-alive">
            <span class="inline-block h-2 w-2 rounded-full bg-success" aria-hidden="true"></span>
            Detecção ativa
          </p>
        } @else {
          <p class="mb-3 flex items-center gap-2 rounded-md bg-warning-bg px-3 py-2 text-xs font-medium text-warning-ink"
             role="alert" aria-live="assertive" data-testid="gest-detection-stalled">
            <span class="material-symbols-outlined text-[16px]">warning</span>
            Detecção parada — verifique se esta aba está visível e se a câmera não
            está em uso por outro programa.
          </p>
        }
      }

      <!-- Motor legado (agente Python) — banner interino da ADR-006. Fora do
           caminho do produto desde a ADR-007; só aparece com provideGest({ detection: 'bridge' }). -->
      @if (facade.detectorType() === 'ear_bri') {
        <div class="gest-interim-banner mb-3 rounded-md bg-info-bg px-3 py-2 text-xs text-info-ink">
          Motor legado (EAR/BRI do agente local) — não é o motor do produto.
        </div>
      }

      <!-- Degraded banner — always rendered (honest-degraded invariant, ADR-005) -->
      @if (facade.degraded()) {
        <div class="mb-3 rounded-md bg-warning-bg px-3 py-2 text-xs text-warning-ink">
          {{ degradedCopy() }}
        </div>
      }

      <!-- GEST-FE-019: loading spinner — shown while camera is being acquired (state:testing, stream not yet ready).
           Uses inline CSS (Wave-0 fallback) until @neuroassist/shared/ui-feedback provides <na-spinner>. -->
      @if (facade.testState() === 'testing' && !facade.previewStream()) {
        <div class="gest-loading mb-3 flex items-center gap-2 text-sm text-ink-soft" aria-live="polite">
          <span
            class="gest-spinner inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
            aria-hidden="true"
          ></span>
          <span>Inicializando câmera…</span>
        </div>
      }

      <!-- GEST-FE-020: camera-error state — prominent error block with inline retry action.
           Replaces the generic state badge for the camera-error case; uses inline CSS (Wave-0 fallback)
           until @neuroassist/shared/ui-feedback provides <na-error-display>. -->
      @if (facade.testState() === 'camera-error') {
        <div class="gest-error mb-3 rounded-md bg-red-50 px-3 py-2" role="alert">
          <p class="mb-2 text-sm text-red-800">{{ facade.message() }}</p>
          <button
            type="button"
            class="rounded-md bg-red-600 px-2.5 py-1 text-xs font-semibold text-white"
            (click)="retry()"
          >
            Tentar novamente
          </button>
        </div>
      }

      <!-- Composite step indicator -->
      @if (draft().composite && facade.testState() === 'testing') {
        <div class="mb-3 flex items-center gap-2 text-xs text-ink-soft">
          <span [class.font-bold]="facade.step() === 'principal'">1. Gesto principal</span>
          <span>→</span>
          <span [class.font-bold]="facade.step() === 'secundario'">2. Gesto secundário</span>
        </div>
      }

      <!--
        A INSTRUÇÃO — o que fazer diante da câmera.

        Vinha do protótipo e não foi portada junto com o detector: a tela abria a
        webcam e não dizia nada. Sem ela "não detectou" é indistinguível de "não
        sei o que devo fazer", e a pessoa conclui que o produto não funciona.

        Fica ACIMA do vídeo, e não abaixo, porque é lida ANTES de agir.
      -->
      @if (comoFazer(); as instrucao) {
        <p class="mb-2 text-xs leading-relaxed text-ink-soft" data-testid="gest-como-fazer">
          {{ instrucao }}
        </p>
      } @else if (facade.testState() === 'testing') {
        <p class="mb-2 text-xs leading-relaxed text-warning-ink" data-testid="gest-sem-motor">
          Este movimento ainda não é reconhecido pela câmera — o teste não pode
          confirmar o gesto. Escolha outro movimento para este comando.
        </p>
      }

      <!--
        Preview + malha facial.

        O vídeo já não é "cosmetic only": desde a ADR-007 é DELE que saem os
        frames que o motor analisa. O canvas por cima desenha a malha, que é o
        único jeito de a cuidadora distinguir "o motor não viu meu rosto" de "o
        motor viu e não reconheceu o gesto" — dois problemas com soluções opostas
        (ajustar luz/enquadramento × repetir o movimento).

        O aria-hidden no canvas: é informação visual redundante para quem usa
        leitor de tela, e o veredito já é anunciado pelo badge de estado.
      -->
      @if (facade.testState() === 'testing' || facade.testState() === 'recognized') {
        <div class="relative mb-3 w-full max-w-sm">
          <video
            #previewVideo
            class="w-full rounded-md bg-black"
            autoplay
            muted
            playsinline
          ></video>
          <canvas
            #meshOverlay
            class="pointer-events-none absolute inset-0 h-full w-full"
            aria-hidden="true"
            data-testid="gest-mesh-overlay"
          ></canvas>
        </div>
      }

      <!-- State badge (hidden for camera-error — the gest-error block above handles that state) -->
      @if (facade.testState() !== 'camera-error') {
        <div
          class="mb-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
          [class]="badgeClass()"
        >
          {{ facade.message() }}
        </div>
      }

      <!-- Controls -->
      <div class="flex items-center gap-2">
        @if (facade.testState() === 'idle' || facade.testState() === 'not-recognized') {
          <button
            type="button"
            class="rounded-md bg-brand px-3 py-1.5 text-xs font-semibold text-white"
            (click)="start()"
          >
            {{ facade.testState() === 'idle' ? 'Testar gesto' : 'Tentar novamente' }}
          </button>
        }
        @if (facade.testState() === 'testing') {
          <button
            type="button"
            class="rounded-md border border-line px-3 py-1.5 text-xs font-medium text-ink"
            (click)="stop()"
          >
            Parar
          </button>
        }
      </div>
    </div>
  `,
})
export class GestureLiveTestComponent implements OnDestroy {
  protected readonly facade = inject(GestureTestFacade);
  protected readonly cameraPref = inject(CameraPreferenceService);

  readonly draft = input.required<ComandoDraft>();
  readonly outcome = output<GestureTestOutcome>();

  private readonly previewVideo = viewChild<ElementRef<HTMLVideoElement>>('previewVideo');
  private readonly meshOverlay = viewChild<ElementRef<HTMLCanvasElement>>('meshOverlay');
  private readonly deteccao = inject(EDGE_DETECTION_PORT);

  /**
   * Instrução do gesto em teste — a do passo ATUAL num comando composto.
   *
   * <p>Mostrar a instrução do gesto principal enquanto a pessoa executa o
   * secundário mandaria ela fazer a coisa errada, e o teste reprovaria por culpa
   * do texto.
   */
  protected readonly comoFazer = computed<string | null>(() => {
    const d = this.draft();
    const ref =
      this.facade.step() === 'secundario'
        ? d.engineFeatureRefSecundario
        : d.engineFeatureRefPrincipal;
    return guidanceDe(ref)?.comoFazer ?? null;
  });

  constructor() {
    // Popula a lista de câmeras já na montagem. Antes da primeira permissão os
    // labels vêm vazios (o serviço numera como fallback) e são reenumerados
    // depois do primeiro getUserMedia bem-sucedido.
    void this.cameraPref.refresh();

    // Bind the cosmetic preview stream to the <video> element whenever both
    // become available (view child only exists while state is testing/recognized).
    effect(() => {
      const video = this.previewVideo()?.nativeElement;
      const stream = this.facade.previewStream();
      if (video && stream && video.srcObject !== stream) {
        video.srcObject = stream;
      }
    });

    // Liga/desliga a malha conforme o canvas entra e sai da view — ele só existe
    // enquanto o estado é testing/recognized. Passar `null` na saída importa: sem
    // isso o adaptador guardaria a referência de um canvas já destacado do DOM e
    // desenharia em memória a cada frame, para ninguém.
    effect(() => {
      this.deteccao.attachOverlay(this.meshOverlay()?.nativeElement ?? null);
    });

    // Forward completed outcomes to the host page (PAC command editor).
    effect(() => {
      const outcome = this.facade.lastOutcome();
      if (outcome && outcome.commandDraftRef === this.draft().commandDraftRef) {
        this.outcome.emit(outcome);
      }
    });
  }

  protected start(): void {
    this.facade.startTest(this.draft());
  }

  protected stop(): void {
    this.facade.stopTest();
  }

  /** Retry the last test run via the facade (GEST-FE-020).
   * Called from the camera-error block's "Tentar novamente" button.
   * Delegates to GestureTestFacade.retry() which restarts with the stored currentDraft. */
  protected retry(): void {
    this.facade.retry();
  }

  protected badgeClass(): string {
    switch (this.facade.testState()) {
      case 'recognized':
        return 'bg-green-100 text-green-800';
      case 'not-recognized':
      case 'camera-error':
        return 'bg-red-100 text-red-800';
      case 'testing':
        return 'bg-blue-100 text-blue-800';
      default:
        return 'bg-surface-2 text-ink-soft';
    }
  }

  protected degradedCopy(): string {
    switch (this.facade.state().degradedReason) {
      case 'device-offline':
        return 'Dispositivo offline — teste em modo simulado, resultado não é real.';
      case 'bridge-unreachable':
        return 'Agente de detecção local não respondeu — teste em modo simulado.';
      case 'version-mismatch':
        return 'Versão do agente de detecção incompatível — teste em modo simulado.';
      default:
        return 'Modo degradado — resultado do teste é simulado, não real.';
    }
  }

  protected onCameraChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.cameraPref.select(value || null);
  }

  ngOnDestroy(): void {
    this.facade.stopTest();
  }
}
