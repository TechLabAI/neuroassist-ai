import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { DeviceSessionStore } from './device-session.store';
import { DetectionProfileStore } from './detection-profile.store';

// =============================================================================
// HeartbeatService — sinal periódico de presença ao edge-service (GEST-FE-031)
//
// Invariante I3 (SPEC-09): o heartbeat CONTINUA em 'parada' e em 'degradada'.
// É a única forma do servidor distinguir "estação parada" de "estação morta".
//
// Corpo do heartbeat: exatamente { agentVersion, runningProfileVersion, deviceId }.
// Qualquer campo adicional faz o backend lançar (@JsonAnySetter — invariante de
// privacidade nº 6). Nunca adicionar telemetria aqui.
//
// Cadência: definida pelo servidor em recommendedHeartbeatSeconds.
// Default 30 s até a primeira resposta. Após isso, o valor do servidor prevalece
// — a cadência muda por configuração (edge.heartbeat.interval-seconds), sem
// reimplantar o cliente.
//
// profileDrift na resposta → forceReload() do DetectionProfileStore.
// =============================================================================

/** Versão do agente — corresponde ao package.json da app. */
export const AGENT_VERSION = '0.1.0';

/** Intervalo padrão até o servidor recomendar um. */
const DEFAULT_INTERVAL_MS = 30_000;

/**
 * Corpo enviado ao endpoint POST /api/v1/edge/heartbeat.
 * NUNCA acrescente campos — @JsonAnySetter no backend lança em campo não declarado.
 */
interface HeartbeatRequest {
  readonly agentVersion: string;
  readonly runningProfileVersion: number | null;
  readonly deviceId: string | null;
}

/**
 * Resposta do endpoint.
 * recommendedHeartbeatSeconds: valor de edge.heartbeat.interval-seconds no servidor.
 */
export interface HeartbeatResponse {
  readonly status: string;
  readonly serverTime: string;
  readonly currentProfileVersion: number;
  readonly profileDrift: boolean;
  /** Cadência recomendada pelo servidor (edge.heartbeat.interval-seconds). Opcional:
   *  caso ausente na resposta, o cliente mantém o intervalo atual. */
  readonly recommendedHeartbeatSeconds?: number;
}

@Injectable({ providedIn: 'root' })
export class HeartbeatService {
  private readonly http = inject(HttpClient);
  private readonly deviceStore = inject(DeviceSessionStore);
  private readonly profileStore = inject(DetectionProfileStore);

  private timer: ReturnType<typeof setTimeout> | null = null;

  /** Intervalo atual — atualizado pelo servidor após cada resposta bem-sucedida. */
  private currentIntervalMs = DEFAULT_INTERVAL_MS;

  /** true entre start() e stop(). Controla a recursão de setTimeout. */
  private running = false;

  // ---------------------------------------------------------------------------
  // Ciclo público
  // ---------------------------------------------------------------------------

  /**
   * Inicia o ciclo de heartbeat.
   * Idempotente: chamadas adicionais enquanto em execução são ignoradas.
   * Emite imediatamente e depois em cada intervalo.
   */
  start(): void {
    if (this.running) return;
    this.running = true;

    // Emissão imediata — confirma ao servidor que a estação está viva agora.
    void this.emit();
    this.scheduleNext();
  }

  /**
   * Para o ciclo. Chamar em ngOnDestroy.
   */
  stop(): void {
    this.running = false;
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  // ---------------------------------------------------------------------------
  // Emissão
  // ---------------------------------------------------------------------------

  /**
   * Emite um heartbeat imediatamente (exposto para testes).
   * Nunca lança — falhas de rede são absorvidas para não interromper o ciclo.
   */
  async emit(): Promise<void> {
    const deviceId = this.deviceStore.deviceId();
    const runningProfileVersion = this.profileStore.profileVersion();

    // Corpo com exatamente 3 campos — imutável para sempre.
    const body: HeartbeatRequest = {
      agentVersion: AGENT_VERSION,
      runningProfileVersion,
      deviceId,
    };

    try {
      const response = await firstValueFrom(
        this.http.post<HeartbeatResponse>('/api/v1/edge/heartbeat', body),
      );

      // Atualizar cadência com o valor recomendado pelo servidor.
      if (
        typeof response.recommendedHeartbeatSeconds === 'number' &&
        response.recommendedHeartbeatSeconds > 0
      ) {
        this.currentIntervalMs = response.recommendedHeartbeatSeconds * 1000;
      }

      // Perfil desatualizado → recarregar para sincronizar com o servidor.
      if (response.profileDrift) {
        await this.profileStore.forceReload();
      }
    } catch {
      // Falha de rede ou 5xx → continuar batendo. O heartbeat NUNCA para por erro.
      // Esse silêncio é correto: a ausência de heartbeats no banco JÁ É o sinal
      // de alerta; parar de bater ao ver um erro esconde o problema.
    }
  }

  // ---------------------------------------------------------------------------
  // Agendamento interno
  // ---------------------------------------------------------------------------

  private scheduleNext(): void {
    if (!this.running) return;

    this.timer = setTimeout(async () => {
      if (!this.running) return; // stop() chamado enquanto o timer estava pendente
      await this.emit();
      this.scheduleNext();         // re-agendar após a emissão completar
    }, this.currentIntervalMs);
  }
}
