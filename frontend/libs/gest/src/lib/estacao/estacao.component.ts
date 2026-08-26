import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  signal,
  computed,
  inject,
  isDevMode,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { GestureDetector, DEFAULT_ASSET_PATHS } from '../detection/gesture-detector';
import type { EngineFeatureRef } from '@neuroassist/shared/contracts';
import { DeviceSessionStore } from './device-session.store';
import { DetectionProfileStore } from './detection-profile.store';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import type { ReportRequest } from './report-ingress.port';
import { HeartbeatService } from './heartbeat.service';
import { WakeLockService } from './wake-lock.service';
import { ReportQueueService, isNonRetryable } from './report-queue.service';

// =============================================================================
// EstacaoComponent — rota /estacao, laço always-on (GEST-FE-030 — ADR-008/SPEC-09)
//
// Esta é a tela que faz o produto existir. É uma rota de topo-level (fora do
// AppShellComponent, como /login), acessível somente com sessão de dispositivo
// (estacaoDeviceGuard). Nenhum menu, sidebar ou breadcrumb é exibido.
//
// Ciclo de vida:
//   ngOnInit  → loadProfile() → init detector → acquireCamera → start()
//   ngOnDestroy → detector.destroy() + parar stream
//
// Máquina de estados (EstacaoEstado):
//   'carregando'          — aguardando profile; indicador visual
//   'vigiando'            — detector ativo; estado normal de operação
//   'sem-vinculo'         — 403 edge.device-not-paired (tela cheia — I2)
//   'degradada'           — 502 edge.profile-unavailable ou falha de câmera (tela cheia — I2)
//   'credencial-invalida' — estado terminal de DeviceSessionStore (tela cheia — I2)
//   'parada'              — aba oculta (visibilitychange); heartbeat continua em P5
//
// Regras I6 (SPEC-09): nunca exibir dado clínico além do rótulo do comando.
// Regras I2 (SPEC-09): 'parada' e 'degradada' ocupam a tela inteira.
//
// reportId idempotente (I4 — SPEC-09):
//   Derivado de deviceId + engineFeatureRef + instante do reconhecimento.
//   Reenvio manda o mesmo id (backend responde 200); pedido novo manda id novo.
//
// P5 (GEST-FE-031/032): HeartbeatService (heartbeat sempre ativo), WakeLockService
//   (tela acesa durante vigília) e ReportQueueService (fila offline de relatos)
//   foram integrados nesta prioridade.
// =============================================================================

export type EstacaoEstado =
  | 'carregando'
  | 'vigiando'
  | 'sem-vinculo'
  | 'degradada'
  | 'credencial-invalida'
  | 'parada';


@Component({
  selector: 'gest-estacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  styles: [`
    :host {
      display: block;
      position: fixed;
      inset: 0;
      width: 100vw;
      height: 100vh;
      font-family: sans-serif;
      overflow: hidden;
    }

    .tela {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      text-align: center;
      padding: 2rem;
      box-sizing: border-box;
    }

    .tela--carregando {
      background: #1a1a2e;
      color: #aaa;
    }

    .tela--vigiando {
      background: #0d1b12;
      color: #4ade80;
    }

    .tela--sem-vinculo {
      background: #1c1400;
      color: #facc15;
    }

    .tela--degradada {
      background: #1a0000;
      color: #f87171;
    }

    .tela--credencial-invalida {
      background: #0d0000;
      color: #fca5a5;
    }

    .tela--parada {
      background: #111;
      color: #555;
    }

    .status-bar {
      position: fixed;
      bottom: 1.5rem;
      right: 1.5rem;
      font-size: 0.9rem;
      opacity: 0.7;
      color: inherit;
    }

    .status-bar .pendentes {
      color: #fbbf24;
      font-weight: 600;
    }

    .status-bar .gap {
      color: #f87171;
      margin-top: 0.25rem;
    }

    h1 {
      font-size: clamp(2.5rem, 8vw, 6rem);
      font-weight: 700;
      margin: 0 0 1rem;
      letter-spacing: 0.02em;
    }

    p {
      font-size: clamp(1rem, 3vw, 2rem);
      margin: 0.5rem 0;
      max-width: 40rem;
    }

    .ultimo-comando {
      font-size: clamp(1.5rem, 5vw, 3.5rem);
      font-weight: 600;
      color: #86efac;
      margin-top: 2rem;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(0.5rem); }
      to   { opacity: 1; transform: translateY(0); }
    }
  `],
  template: `
    @switch (estado()) {
      @case ('carregando') {
        <div class="tela tela--carregando">
          <h1>⏳</h1>
          <p>Inicializando estação…</p>
        </div>
      }

      @case ('vigiando') {
        <div class="tela tela--vigiando">
          <h1>● VIGILANDO</h1>
          <p>Estação ativa. Aguardando gestos.</p>
          @if (ultimoComandoLabel()) {
            <p class="ultimo-comando">{{ ultimoComandoLabel() }}</p>
          }
          @if (pendingReportsCount() > 0) {
            <div class="status-bar">
              <div class="pendentes">⚠ {{ pendingReportsCount() }} relato(s) aguardando envio</div>
            </div>
          }
        </div>
      }

      @case ('sem-vinculo') {
        <div class="tela tela--sem-vinculo">
          <h1>⚠ SEM VÍNCULO</h1>
          <p>Este dispositivo não está vinculado a nenhum paciente.</p>
          <p>Solicite ao coordenador clínico que realize o pareamento.</p>
        </div>
      }

      @case ('degradada') {
        <div class="tela tela--degradada">
          <h1>✕ SERVIÇO INDISPONÍVEL</h1>
          <p>O serviço de detecção está temporariamente indisponível.</p>
          <p>A estação tentará recuperar automaticamente.</p>
        </div>
      }

      @case ('credencial-invalida') {
        <div class="tela tela--credencial-invalida">
          <h1>✕ CREDENCIAL INVÁLIDA</h1>
          <p>A credencial do dispositivo é inválida ou expirou.</p>
          <p>Chame o suporte técnico.</p>
        </div>
      }

      @case ('parada') {
        <div class="tela tela--parada">
          <h1>PARADA</h1>
          <p>A estação está pausada (aba oculta).</p>
          <p>Retorne a esta aba para retomar a vigilância.</p>
          @if (observacaoGapLabel()) {
            <div class="status-bar">
              <div class="gap">⚠ Janela sem observação: {{ observacaoGapLabel() }}</div>
            </div>
          }
        </div>
      }
    }
  `,
})
export class EstacaoComponent implements OnInit, OnDestroy {
  private readonly deviceStore = inject(DeviceSessionStore);
  private readonly profileStore = inject(DetectionProfileStore);
  private readonly reportAdapter = inject(ReportIngressHttpAdapter);

  // P5 services
  private readonly heartbeatService = inject(HeartbeatService);
  private readonly wakeLock = inject(WakeLockService);
  private readonly reportQueue = inject(ReportQueueService);

  // ---------------------------------------------------------------------------
  // Estado visível — signals
  // ---------------------------------------------------------------------------

  private readonly _estado = signal<EstacaoEstado>('carregando');
  private readonly _ultimoComandoLabel = signal<string | null>(null);

  readonly estado = this._estado.asReadonly();
  readonly ultimoComandoLabel = this._ultimoComandoLabel.asReadonly();

  /** Relatos aguardando reenvio — exibir na tela de estação (não em banner de canto). */
  readonly pendingReportsCount = this.reportQueue.pendingCount;

  /**
   * Duração da janela sem observação (estado 'parada').
   * null quando não há registro de parada ativa.
   *
   * ATENÇÃO: aba fechada NÃO é detecção enfileirada — é detecção que não aconteceu.
   * Nenhuma fila recupera um pedido de ajuda que ninguém observou.
   * Esta label deixa isso explícito na UI.
   */
  readonly observacaoGapLabel = computed((): string | null => {
    const since = this.reportQueue.lastParadaSince();
    if (!since) return null;
    const elapsedMs = Date.now() - new Date(since).getTime();
    const seconds = Math.floor(elapsedMs / 1000);
    if (seconds < 60) return `${seconds} s`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes} min ${secs} s`;
  });

  // ---------------------------------------------------------------------------
  // Interno — detector e câmera
  // ---------------------------------------------------------------------------

  private detector: GestureDetector | null = null;
  private videoEl: HTMLVideoElement | null = null;
  private stream: MediaStream | null = null;

  /** Janela de cooldown por engineFeatureRef — evita relatos duplicados. */

  /**
   * Listener de visibilidade — atualiza estado 'parada' e gerencia Wake Lock.
   * O heartbeat CONTINUA em 'parada' — é como o servidor distingue parada de morta.
   * Não chamar heartbeatService.stop() aqui: esse seria o pior modo de falha (ADR-007).
   */
  private readonly onVisibilityChange = () => {
    if (document.hidden) {
      this.detector?.stop();
      this.wakeLock.release(); // browser já libera, mas explicitamos
      this.reportQueue.recordParadaStart();
      this._estado.set('parada');
    } else if (this._estado() === 'parada') {
      this.reportQueue.clearParadaRecord();
      this.detector?.start(this.videoEl!);
      this._estado.set('vigiando');
      // Wake Lock foi liberado pelo browser ao ocultar; readquirir ao retornar
      void this.wakeLock.reacquire();
      // Tentar reenviar relatos enfileirados quando a aba voltou ao foco
      void this.reportQueue.processQueue();
    }
  };

  // ---------------------------------------------------------------------------
  // Ciclo de vida
  // ---------------------------------------------------------------------------

  async ngOnInit(): Promise<void> {
    // Verificar se o DeviceSessionStore está em estado terminal
    if (this.deviceStore.estado() === 'credencial-invalida') {
      this._estado.set('credencial-invalida');
      return;
    }

    document.addEventListener('visibilitychange', this.onVisibilityChange);

    // Iniciar heartbeat — continua em QUALQUER estado (parada, degradada, vigiando).
    // Isso é o que torna a estação distinguível de "morta" pelo servidor.
    this.heartbeatService.start();

    await this.inicializarLoop();
  }

  ngOnDestroy(): void {
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.heartbeatService.stop();
    this.wakeLock.release();
    this.encerrarSessao();
    this.removerGanchoE2E();
  }

  // ---------------------------------------------------------------------------
  // Laço always-on
  // ---------------------------------------------------------------------------

  async inicializarLoop(): Promise<void> {
    this._estado.set('carregando');

    // 1. Carregar perfil do dispositivo (EDGE-022)
    try {
      await this.profileStore.loadProfile();
    } catch {
      // Erros de rede genéricos — profileStore já os captura internamente
    }

    if (this.profileStore.semVinculo()) {
      this._estado.set('sem-vinculo');
      return;
    }

    if (this.profileStore.perfilIndisponivel()) {
      this._estado.set('degradada');
      return;
    }

    // 2. Obter limiares do perfil (faixa global 'media')
    const thresholds = this.profileStore.thresholdsGlobaisMedia();

    // ── Gancho E2E (dev mode only): pular detector + câmera ────────────────────
    // `window.__gest_e2e_skip_detector = true` (setado via addInitScript antes
    // do goto) faz o laço ir direto para 'vigiando'. O gancho de trigger de
    // gesto é registrado a seguir para que os specs possam simular detecções.
    // Em produção (`isDevMode()` falso) este bloco não existe no bundle.
    if (isDevMode() && (window as unknown as Record<string, unknown>)['__gest_e2e_skip_detector']) {
      this._estado.set('vigiando');
      this.registrarGanchoE2E();
      return;
    }

    // 3. Criar e inicializar detector com os limiares do perfil
    this.detector = new GestureDetector(
      {
        onFrame: () => {
          // Frames processados silenciosamente — sem telemetria de calibração.
        },
        onGesture: (engineFeatureRef, confidence) => {
          void this.handleGesture(engineFeatureRef, confidence);
        },
      },
      DEFAULT_ASSET_PATHS,
      thresholds,
    );

    try {
      await this.detector.init();
    } catch {
      this._estado.set('degradada');
      this.detector.destroy();
      this.detector = null;
      return;
    }

    // 4. Adquirir câmera
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    } catch {
      this._estado.set('degradada');
      this.detector.destroy();
      this.detector = null;
      return;
    }

    const video = document.createElement('video');
    video.srcObject = this.stream;
    video.muted = true;
    video.playsInline = true;
    this.videoEl = video;

    try {
      await video.play();
    } catch {
      this._estado.set('degradada');
      this.encerrarSessao();
      return;
    }

    // 5. Iniciar detecção contínua
    this.detector.start(video);
    this._estado.set('vigiando');

    // Solicitar Wake Lock para manter a tela acesa durante a vigília.
    // Ausência da API é degradação declarada — não altera o estado.
    await this.wakeLock.acquire();

    this.registrarGanchoE2E();
  }

  // ---------------------------------------------------------------------------
  // Manipulação de gesto detectado
  // ---------------------------------------------------------------------------

  /**
   * Chamado pelo detector para cada gesto reconhecido.
   *
   * Fluxo:
   *   1. Encontrar o primitivo do perfil com este engineFeatureRef
   *   2. Derivar reportId do instante do reconhecimento (idempotente no reenvio)
   *   3. Montar ReportRequest sem rawFramePayload nem deviceTenant
   *   4. Submeter ao backend
   *   5. Atualizar último comando exibido
   */
  async handleGesture(engineFeatureRef: EngineFeatureRef, confidence: number): Promise<void> {
    const primitivos = this.profileStore.primitivos();
    const primitivo = primitivos.find(p => p.engineFeatureRef === engineFeatureRef);
    if (!primitivo) {
      // Gesto reconhecido pelo motor mas não está no perfil ativo — descartar.
      return;
    }

    const deviceId = this.deviceStore.deviceId();
    const pacienteId = this.profileStore.pacienteId();
    const profileVersion = this.profileStore.profileVersion();

    if (!deviceId || !pacienteId || profileVersion === null) {
      // Sessão ou perfil não disponível — não reportar.
      return;
    }

    // ── Idempotência é POR RECONHECIMENTO, não por janela de relógio ─────────
    //
    // A primeira versão derivava o reportId de `Math.floor(Date.now() / 30_000)`
    // e descartava o gesto quando a janela se repetia. Dois defeitos:
    //
    // 1. ENGOLIA PEDIDO LEGÍTIMO. O detector já tem cooldown próprio de 4 s
    //    (COOLDOWN_MS), então cada chamada aqui É um reconhecimento distinto. Com
    //    a janela de 30 s, o Márcio pedir ajuda, ninguém vir, e ele pedir de novo
    //    aos 10 s resultava em silêncio — quando repetir o gesto é justamente o
    //    sinal mais forte de que o primeiro pedido não foi atendido.
    //
    // 2. A JANELA ERA ALINHADA AO RELÓGIO, não ao gesto. Dois gestos a 1 s de
    //    distância caíam em janelas DIFERENTES se cruzassem a fronteira dos 30 s
    //    (t=29,9 s e t=30,1 s → dois relatos), e dois a 29 s de distância caíam na
    //    MESMA (um relato). A garantia não era "um por gesto" nem "um por 30 s":
    //    era "um por balde de relógio", que depende de quando o tick calha.
    //
    // O que a SPEC-09 §4 (I4) pede é outra coisa: que UM reconhecimento vire UM
    // relato mesmo que o envio seja retentado — a idempotência existe para a fila
    // da P5, não para suprimir repetição do paciente. Por isso o id é derivado do
    // INSTANTE do reconhecimento: reenvio manda o mesmo id (o backend responde
    // 200), e um pedido novo manda um id novo.
    const recognizedAt = new Date().toISOString();
    const reportId = this.computeReportId(deviceId, engineFeatureRef, recognizedAt);

    const request: ReportRequest = {
      reportId,
      pacienteId,
      commandRef: primitivo.tipoGestoId, // tipoGestoId é o UUID do catálogo PAC
      primitives: [{ tipoGestoId: primitivo.tipoGestoId, confidence }],
      confidence,
      profileVersion,
      recognizedAt,
      deviceId,
    };

    try {
      await this.reportAdapter.submit(request);
      this._ultimoComandoLabel.set(primitivo.nome);
    } catch (err) {
      if (isNonRetryable(err)) {
        // 4xx semântico (ex.: 422 campo inválido, 400 body malformado) → não retentar.
        // O relato nunca vai passar; manter na fila entuperia sem resolver o problema.
        return;
      }
      // Falha de rede / 5xx / 408 / 429 → enfileirar para reenvio quando a rede voltar.
      // Não alterar o estado da estação — continuar vigiando.
      this.reportQueue.enqueue(request);
    }
  }

  // ---------------------------------------------------------------------------
  // reportId idempotente
  // ---------------------------------------------------------------------------

  /**
   * Deriva um reportId estável para UM reconhecimento.
   *
   * <p>Estável para o mesmo reconhecimento (reenvio pela fila manda o mesmo id, e o
   * backend responde 200 em vez de criar um segundo alarme) e distinto para
   * reconhecimentos diferentes — inclusive dois do mesmo gesto em sequência, que é
   * o caso de alguém pedindo ajuda de novo porque ninguém veio.
   */
  computeReportId(deviceId: string, engineFeatureRef: string, recognizedAt: string): string {
    return `${deviceId}|${engineFeatureRef}|${recognizedAt}`;
  }

  // ---------------------------------------------------------------------------
  // Ganchos E2E (dev mode only — GEST-FE-033)
  //
  // Em build de desenvolvimento, dois ganchos globais são expostos para os specs
  // Playwright conseguirem exercitar a máquina de estados sem câmera real e sem
  // MediaPipe rodando:
  //
  // 1. `window.__gest_e2e_skip_detector` (setado via addInitScript antes do goto):
  //    Quando `true`, `inicializarLoop()` vai direto para 'vigiando' sem tentar
  //    carregar modelos do MediaPipe nem abrir câmera. Só é verificado dentro de
  //    `inicializarLoop()`, portanto não interfere no fluxo de produção.
  //
  // 2. `window.__gest_e2e_trigger_gesture(ref, confidence)` (registrado aqui):
  //    Permite que um spec chame `handleGesture()` de fora, simulando um gesto
  //    reconhecido pelo motor. Limpo em ngOnDestroy.
  //
  // Ambos são removidos pelo tree-shaker em build de produção (`isDevMode()` é
  // falso; o código dentro do `if` nunca é incluído no bundle). O gancho não pode
  // existir onde há paciente — essa é a garantia.
  // ---------------------------------------------------------------------------

  private registrarGanchoE2E(): void {
    if (!isDevMode()) return;
    (window as unknown as Record<string, unknown>)['__gest_e2e_trigger_gesture'] =
      (ref: string, conf: number) => void this.handleGesture(ref as EngineFeatureRef, conf);
  }

  private removerGanchoE2E(): void {
    if (!isDevMode()) return;
    delete (window as unknown as Record<string, unknown>)['__gest_e2e_trigger_gesture'];
  }

  // ---------------------------------------------------------------------------
  // Limpeza
  // ---------------------------------------------------------------------------

  private encerrarSessao(): void {
    this.detector?.destroy();
    this.detector = null;

    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }

    if (this.videoEl) {
      this.videoEl.srcObject = null;
      this.videoEl = null;
    }
  }
}
