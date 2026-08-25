import { Injectable, signal, computed, inject } from '@angular/core';
import { isEngineFeatureRef, type EngineFeatureRef } from '@neuroassist/shared/contracts';
import { DetectionProfileHttpAdapter } from './detection-profile-http.adapter';
import { isNotModified, ProfileErro, type DetectionProfileDto, type PrimitivoDeteccao } from './detection-profile.port';
import type { DetectionThresholds } from '@neuroassist/shared/contracts';
import { LIMIARES_PADRAO } from '@neuroassist/shared/contracts';

// =============================================================================
// DetectionProfileStore — perfil de detecção em runtime (GEST-FE-028)
//
// Responsabilidades:
//   - Buscar GET /api/v1/edge/profile com ETag condicional (EDGE-022)
//   - Expor os primitivos válidos (origem ≠ INDISPONIVEL, engineFeatureRef conhecido)
//   - Expor os limiares da faixa correta para cada primitivo
//   - Expor o estado de degradação (parcial ou total)
//   - Distinguir sem-vinculo (403) de degradada (502) de erro genérico
//   - 304 → manter perfil em memória sem sobrescrever nada
//
// Primitivos com origem: INDISPONIVEL são descartados da lista de primitivos
// detectáveis e registrados como "degradação parcial" — nunca servidos ao motor.
//
// engineFeatureRef que não existe em ENGINE_FEATURE também é descartado com aviso.
// =============================================================================

/** Um primitivo resolvido: já tem a faixa de limiares aplicada. */
export interface PrimitivoResolvido {
  readonly tipoGestoId: string;
  readonly nome: string;
  readonly engineFeatureRef: EngineFeatureRef;
  /** Os limiares da faixa de sensibilidade deste primitivo. */
  readonly thresholds: DetectionThresholds;
}

/** Estado de degradação do perfil. */
export interface ProfileDegradacao {
  /** true se o perfil não pôde ser carregado (fallback para LIMIARES_PADRAO). */
  readonly semPerfil: boolean;
  /** Nomes dos primitivos com origem INDISPONIVEL, excluídos da detecção. */
  readonly primitivosIndisponiveis: readonly string[];
  /** true se qualquer primitivo foi descartado por engineFeatureRef desconhecido. */
  readonly primitivosDesconhecidos: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class DetectionProfileStore {
  // O adapter HTTP é o default; testes substituem via TestBed.overrideProvider.
  private readonly adapter = inject(DetectionProfileHttpAdapter);

  // ---------------------------------------------------------------------------
  // Estado interno
  // ---------------------------------------------------------------------------

  private readonly _perfil = signal<DetectionProfileDto | null>(null);
  private readonly _carregando = signal(false);
  private readonly _erro = signal<string | null>(null);
  /** 403 edge.device-not-paired — dispositivo sem paciente pareado (SPEC-09 §3). */
  private readonly _semVinculo = signal(false);
  /** 502 edge.profile-unavailable — GEST fora do ar (SPEC-09 §3). */
  private readonly _perfilIndisponivel = signal(false);
  private _etag: string | null = null;

  // ---------------------------------------------------------------------------
  // API pública — signals readonly
  // ---------------------------------------------------------------------------

  /** Versão do perfil carregado, ou null se não há perfil. */
  readonly profileVersion = computed(() => this._perfil()?.version ?? null);

  /** ETag atual (para uso pelo heartbeat ao detectar drift). */
  readonly currentEtag = computed(() => this._perfil()?.etag ?? null);

  /** true se uma carga está em andamento. */
  readonly carregando = this._carregando.asReadonly();

  /** Mensagem de erro da última tentativa, ou null. */
  readonly erro = this._erro.asReadonly();

  /**
   * true se o dispositivo não está pareado a nenhum paciente (403 edge.device-not-paired).
   * Estado de primeira classe — exige ação humana, não retry automático.
   */
  readonly semVinculo = this._semVinculo.asReadonly();

  /**
   * true se o GEST está fora do ar (502 edge.profile-unavailable).
   * Estado `degradada` — NUNCA tratar como "nenhum gesto configurado".
   */
  readonly perfilIndisponivel = this._perfilIndisponivel.asReadonly();

  /**
   * Primitivos detectáveis — apenas os com origem válida (PACIENTE | PERFIL_EMPRESA)
   * e com engineFeatureRef reconhecido pelo motor.
   *
   * INDISPONIVEL e engineFeatureRef desconhecidos são excluídos aqui.
   * Use `degradacao()` para saber o que foi excluído.
   */
  readonly primitivos = computed((): readonly PrimitivoResolvido[] => {
    const perfil = this._perfil();
    if (!perfil) return [];

    const resolvidos: PrimitivoResolvido[] = [];
    for (const p of perfil.primitives) {
      if (p.origem === 'INDISPONIVEL') continue;
      if (!isEngineFeatureRef(p.engineFeatureRef)) continue;
      resolvidos.push({
        tipoGestoId: p.tipoGestoId,
        nome: p.nome,
        engineFeatureRef: p.engineFeatureRef as EngineFeatureRef,
        thresholds: this._resolverLimiares(perfil, p),
      });
    }
    return resolvidos;
  });

  /**
   * Estado de degradação do perfil.
   * `semPerfil: true` → usando LIMIARES_PADRAO como fallback.
   */
  readonly degradacao = computed((): ProfileDegradacao => {
    const perfil = this._perfil();
    if (!perfil) {
      return { semPerfil: true, primitivosIndisponiveis: [], primitivosDesconhecidos: [] };
    }

    const indisponiveis: string[] = [];
    const desconhecidos: string[] = [];

    for (const p of perfil.primitives) {
      if (p.origem === 'INDISPONIVEL') {
        indisponiveis.push(p.nome);
      } else if (!isEngineFeatureRef(p.engineFeatureRef)) {
        console.warn(
          `[DetectionProfileStore] engineFeatureRef desconhecido descartado: "${p.engineFeatureRef}" (gesto: "${p.nome}"). ` +
          'O motor não detecta este gesto. Verifique ENGINE_FEATURE em shared/contracts.'
        );
        desconhecidos.push(p.nome);
      }
    }

    return {
      semPerfil: false,
      primitivosIndisponiveis: indisponiveis,
      primitivosDesconhecidos: desconhecidos,
    };
  });

  /** true se há qualquer degradação (sem perfil, primitivos indisponíveis ou desconhecidos). */
  readonly degradado = computed(() => {
    const d = this.degradacao();
    return d.semPerfil || d.primitivosIndisponiveis.length > 0 || d.primitivosDesconhecidos.length > 0;
  });

  /**
   * Limiares globais da faixa 'media' do perfil carregado.
   * Usados pelo always-on loop (EstacaoComponent) como threshold único para o detector.
   * Quando não há perfil, retorna LIMIARES_PADRAO.
   */
  readonly thresholdsGlobaisMedia = computed((): DetectionThresholds => {
    return this._perfil()?.thresholds.media ?? LIMIARES_PADRAO;
  });

  /**
   * pacienteId do perfil carregado — vem do pareamento no backend.
   * null quando não há perfil ou o backend não informou.
   */
  readonly pacienteId = computed(() => this._perfil()?.pacienteId ?? null);

  /**
   * Limiares padrão quando não há perfil carregado.
   * Use `primitivos()` → cada `PrimitivoResolvido.thresholds` para produção;
   * este getter existe para que componentes exibam qual faixa está em uso.
   */
  readonly limiaresFallback: DetectionThresholds = LIMIARES_PADRAO;

  // ---------------------------------------------------------------------------
  // Operações
  // ---------------------------------------------------------------------------

  /**
   * Carrega o perfil do dispositivo autenticado.
   * O deviceId é derivado do claim `sub` do JWT pelo backend (EDGE-022).
   * - 304: mantém o perfil em memória sem sobrescrever nada.
   * - 403 edge.device-not-paired: sinaliza `semVinculo`.
   * - 502 edge.profile-unavailable: sinaliza `perfilIndisponivel`.
   * - Outro erro: sinaliza `erro`; `degradacao().semPerfil` permanece true.
   */
  async loadProfile(): Promise<void> {
    this._carregando.set(true);
    this._erro.set(null);
    this._semVinculo.set(false);
    this._perfilIndisponivel.set(false);

    try {
      const resultado = await this.adapter.fetchProfile(this._etag ?? undefined);

      if (isNotModified(resultado)) {
        // 304 — perfil em memória está atual; não substituir nada.
        return;
      }

      this._perfil.set(resultado);
      this._etag = resultado.etag ?? null;
    } catch (err) {
      if (err instanceof ProfileErro) {
        if (err.tipo === 'sem-vinculo') {
          this._semVinculo.set(true);
        } else if (err.tipo === 'degradada') {
          this._perfilIndisponivel.set(true);
        }
        // _perfil permanece null (ou o último valor válido em memória).
      } else {
        const msg = err instanceof Error ? err.message : String(err);
        this._erro.set(msg);
      }
    } finally {
      this._carregando.set(false);
    }
  }

  /**
   * Força recarregamento ignorando o ETag atual.
   * Usado pelo HeartbeatService quando profileDrift=true (P5 — GEST-FE-031).
   */
  async forceReload(): Promise<void> {
    this._etag = null;
    await this.loadProfile();
  }

  // ---------------------------------------------------------------------------
  // Privado
  // ---------------------------------------------------------------------------

  private _resolverLimiares(
    perfil: DetectionProfileDto,
    primitivo: PrimitivoDeteccao,
  ): DetectionThresholds {
    const tabela = perfil.thresholds;
    switch (primitivo.sensibilidade) {
      case 'baixa': return tabela.baixa;
      case 'media': return tabela.media;
      case 'alta':  return tabela.alta;
      default:      return tabela.media;
    }
  }
}
