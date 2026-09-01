import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { NoArvoreFalaPort } from './no-arvore-fala.port';
import { NoArvoreFala, Locale } from '../conv.types';

// =============================================================================
// NoArvoreFalaHttpAdapter — HTTP adapter for conv-service.
// Base URL: /api/v1/conv (via GWY, ADR-008).
// Security: Authorization: Bearer <patient-session-jwt> required on every call
//           (scope: conv.arvore.navegar / conv.no.selecionar — ADR-012/ADR-003).
// All paths: api-contract.md §2.
// TODO[CONV-INT-001]: wire the real backend once conv-service BE is available.
// =============================================================================

/** Minimal DTO shape returned by conv-service GET /api/v1/conv/tree.
 *  Full schema in api-contract.md §2.1. */
interface ConvNodeDto {
  id: string;
  parentId: string | null;
  tipo: 'categoria' | 'frase';
  ordem: number;
  engineFeatureRef: string | null;
  propostoPorTreinamento: boolean;
  conteudo: {
    locale: Locale;
    texto: string;
    contentStatus: 'ativo' | 'pendente' | 'rascunho';
    audioRef: string | null;
  };
}

interface ConvTreeResponse {
  nodes: ConvNodeDto[];
  pacienteId: string;
  localeServed: Locale;
  degraded: boolean;
}

function dtoToNode(dto: ConvNodeDto, locale: Locale): NoArvoreFala {
  const isAtivo = dto.conteudo.contentStatus === 'ativo';
  return {
    id: dto.id,
    pacienteId: '',             // populated from ConvTreeResponse.pacienteId by caller
    parentId: dto.parentId,
    tipo: dto.tipo,
    conteudo: {
      pt: locale === 'pt'
        ? { texto: dto.conteudo.texto, audioRef: dto.conteudo.audioRef, confirmadoCuidadora: isAtivo, confirmadoPaciente: isAtivo }
        : { texto: '', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
      en: locale === 'en'
        ? { texto: dto.conteudo.texto, audioRef: dto.conteudo.audioRef, confirmadoCuidadora: isAtivo, confirmadoPaciente: isAtivo }
        : { texto: '', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
    },
    ordem: dto.ordem,
    engineFeatureRef: dto.engineFeatureRef,
    propostoPorTreinamento: dto.propostoPorTreinamento,
  };
}

@Injectable()
export class NoArvoreFalaHttpAdapter implements NoArvoreFalaPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/conv';

  list(locale?: Locale): Observable<NoArvoreFala[]> {
    let params = new HttpParams();
    if (locale) params = params.set('locale', locale);
    return this.http
      .get<ConvTreeResponse>(`${this.base}/tree`, { params })
      .pipe(
        map(res => res.nodes.map(dto => {
          const node = dtoToNode(dto, res.localeServed);
          node.pacienteId = res.pacienteId;
          return node;
        })),
      );
  }

  selectNode(nodeId: string, locale: Locale): Observable<void> {
    return this.http.post<void>(`${this.base}/select`, { nodeId, locale });
  }

  search(query: string, locale: Locale): Observable<NoArvoreFala[]> {
    return this.http
      .post<ConvTreeResponse>(`${this.base}/search`, { query, locale })
      .pipe(
        map(res => res.nodes.map(dto => {
          const node = dtoToNode(dto, res.localeServed);
          node.pacienteId = res.pacienteId;
          return node;
        })),
      );
  }
}
