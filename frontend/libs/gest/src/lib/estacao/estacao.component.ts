import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { GestureDetector, DEFAULT_ASSET_PATHS } from '../detection/gesture-detector';
import type { EngineFeatureRef } from '@neuroassist/shared/contracts';
import { DeviceSessionStore } from './device-session.store';
import { DetectionProfileStore } from './detection-profile.store';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import type { ReportRequest } from './report-ingress.port';

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
// P5 (GEST-FE-031/032): HeartbeatService, WakeLockService e fila offline são
//   implementados em P5 e acoplados externamente — este componente não os importa.
// =============================================================================

export type EstacaoEstado =
  | 'carregando'
  | 'vigiando'
  | 'sem-vinculo'
  | 'degradada'
  | 'credencial-invalida'
  | 'parada';


@Component({
  selector: 'na-estacao',
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
        </div>
      }
    }
  `,
})
export class EstacaoComponent implements OnInit, OnDestroy {
  private readonly deviceStore = inject(DeviceSessionStore);
  private readonly profileStore = inject(DetectionProfileStore);
  private readonly reportAdapter = inject(ReportIngressHttpAdapter);

  // ---------------------------------------------------------------------------
  // Estado visível — signals
  // ---------------------------------------------------------------------------

  private readonly _estado = signal<EstacaoEstado>('carregando');
  private readonly _ultimoComandoLabel = signal<string | null>(null);

  readonly estado = this._estado.asReadonly();
  readonly ultimoComandoLabel = this._ultimoComandoLabel.asReadonly();

  // ---------------------------------------------------------------------------
  // Interno — detector e câmera
  // ---------------------------------------------------------------------------

  private detector: GestureDetector | null = null;
  private videoEl: HTMLVideoElement | null = null;
  private stream: MediaStream | null = null;

  /** Janela de cooldown por engineFeatureRef — evita relatos duplicados. */

  /** Listener de visibilidade — atualiza estado 'parada'. */
  private readonly onVisibilityChange = () => {
    if (document.hidden) {
      this.detector?.stop();
      this._estado.set('parada');
    } else if (this._estado() === 'parada') {
      this.detector?.start(this.videoEl!);
      this._estado.set('vigiando');
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

    await this.inicializarLoop();
  }

  ngOnDestroy(): void {
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.encerrarSessao();
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
    } catch {
      // Falha de rede → P5 enfileirará. Por ora apenas registra silenciosamente.
      // Não alterar o estado da estação — continuar vigiando.
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
