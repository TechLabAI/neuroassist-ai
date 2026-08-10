// poc-http-adapters.ts — HTTP adapters que substituem os MockAdapters no perfil PoC.
// Conecta o prototype Angular às APIs reais de gest-service (8082) e evnt-service (8083).
// Padrão: PORT → MockAdapter (Estágio 0) → HttpAdapter (PoC) — a Facade nunca muda.
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';
import { TiposGestoPort, EventosPort } from './data-access';
import { TipoGesto, Evento } from '../shared/data-model';

// Usa o mesmo host do browser — funciona tanto em localhost quanto via rede local (celular)
const HOST = window.location.hostname;
const GEST = `http://${HOST}:8082`;
const EVNT = `http://${HOST}:8083`;

// ---------------------------------------------------------------------------
// TipoGesto — lê do gest-service (GET /gest-service/tipo-gesto)
// ---------------------------------------------------------------------------

interface GestTipoGestoDto {
  id: string;
  nome: string;
  detectabilityState: string;
  engineFeatureRef: string | null;
}

@Injectable()
export class GestTiposGestoHttpAdapter extends TiposGestoPort {
  private readonly http = inject(HttpClient);

  async list(): Promise<TipoGesto[]> {
    try {
      const data = await firstValueFrom(
        this.http.get<GestTipoGestoDto[]>(`${GEST}/gest-service/tipo-gesto`),
      );
      return data.map(t => ({ id: t.id, nome: t.nome }));
    } catch {
      // gest-service indisponível — retorna lista vazia para não travar a UI
      return [];
    }
  }
}

// ---------------------------------------------------------------------------
// Eventos — lê do evnt-service (GET /api/v1/events/active)
// Retorna lista com 0 ou 1 evento; somente eventos NAO_AVALIADO são considerados ativos.
// ---------------------------------------------------------------------------

interface EvntActiveEvent {
  id: string;
  urgencia: string;
  tier: string;
  feedback: string;
  recognizedAt: string;
  comandoRef: string | null;
}

@Injectable()
export class EvntEventosHttpAdapter extends EventosPort {
  private readonly http = inject(HttpClient);

  async list(): Promise<Evento[]> {
    try {
      const res = await firstValueFrom(
        this.http.get<{ activeEvent: EvntActiveEvent | null }>(`${EVNT}/api/v1/events/active`),
      );
      if (!res.activeEvent) return [];
      const e = res.activeEvent;
      return [{
        id: e.id,
        pacienteId: 'pac-marcio',
        comando: e.comandoRef ?? 'cmd-ajuda',
        timestamp: e.recognizedAt,
        confirmadoEm: null,
        confirmadoPor: '',
        feedback: 'nao_avaliado',
      }];
    } catch {
      return [];
    }
  }
}

// ---------------------------------------------------------------------------
// PocPublicarPerfilService — chama POST /gest-service/detection-profile/publish
// ---------------------------------------------------------------------------

export interface PublicarPerfilResult {
  version: number;
  contractVersion: string;
}

@Injectable({ providedIn: 'root' })
export class PocPublicarPerfilService {
  private readonly http = inject(HttpClient);

  publicar(): Promise<PublicarPerfilResult> {
    return firstValueFrom(
      this.http.post<PublicarPerfilResult>(`${GEST}/gest-service/detection-profile/publish`, {}),
    );
  }
}

// ---------------------------------------------------------------------------
// PocConfirmarEventoService — chama POST /api/v1/events/{id}/confirmation
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class PocConfirmarEventoService {
  private readonly http = inject(HttpClient);

  confirmar(id: string): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${EVNT}/api/v1/events/${id}/confirmation`, { verdict: 'real' }),
    );
  }
}

// ---------------------------------------------------------------------------
// Provider overrides — adicionar DEPOIS de DATA_PROVIDERS no app.config.ts
// (último provider vence no Angular DI)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// PocTestarGestoService — dispara um report de teste do calibrador para o evnt-service
// ---------------------------------------------------------------------------

export interface TesteGestoResult {
  eventoId: string;
  tier: string;
}

@Injectable({ providedIn: 'root' })
export class PocTestarGestoService {
  private readonly http = inject(HttpClient);

  disparar(tipoGestoId: string, profileVersion: number, comandoId: string, pacienteId: string): Promise<TesteGestoResult> {
    return firstValueFrom(
      this.http.post<{ eventoId: string; notification: { tier: string; caregiverId: string } }>(
        `${EVNT}/api/v1/reports`,
        {
          reportId: `test-ui-${Date.now()}`,
          pacienteId,
          commandRef: comandoId,
          primitives: [{ tipoGestoId, confidence: 0.9 }],
          confidence: 0.9,
          profileVersion,
          timingWindow: { startMs: 0, endMs: 800, durationMs: 800 },
          recognizedAt: new Date().toISOString(),
          deviceId: 'calibracao-ui-test',
        },
      ).pipe(
        map(r => ({ eventoId: r.eventoId, tier: r.notification?.tier ?? 'FULL_SCREEN_ALARM' }))
      )
    );
  }
}

export const POC_HTTP_PROVIDERS = [
  { provide: TiposGestoPort, useClass: GestTiposGestoHttpAdapter },
  { provide: EventosPort, useClass: EvntEventosHttpAdapter },
];
