import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { NoArvoreFalaPort } from './no-arvore-fala.port';
import { NoArvoreFala, Locale, ConteudoLocale } from '../conv.types';

// =============================================================================
// NoArvoreFalaMockAdapter — Estágio 0 (prototype data seed).
// Seed data promoted from design-reference/.../shared/mock-data.ts (Emenda 4).
// Delay 400ms matches the project-wide mock latency convention.
// =============================================================================

// Helper: build a fully active (Dual-Authorization complete) content record.
function conteudoAtivo(pt: string, en: string): Record<Locale, ConteudoLocale> {
  return {
    pt: {
      texto: pt,
      audioRef: `audio/pt/${pt.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`,
      confirmadoCuidadora: true,
      confirmadoPaciente: true,
    },
    en: {
      texto: en,
      audioRef: `audio/en/${en.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`,
      confirmadoCuidadora: true,
      confirmadoPaciente: true,
    },
  };
}

/** Seed dataset — Márcio Amaral's speech tree (3 categories, 2-3 phrases each).
 *  Includes TREINAMENTO proposals in all 3 Dual-Authorization states for UI testing. */
export const MOCK_NOS_ARVORE_FALA: NoArvoreFala[] = [
  // Root categories
  { id: 'no-cat-conforto', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Conforto', 'Comfort'), ordem: 1, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-cat-conversa', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Conversar', 'Talk'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-cat-ajuda', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Preciso de ajuda', 'I need help'), ordem: 3, engineFeatureRef: null, propostoPorTreinamento: false },

  // Phrases — Conforto
  { id: 'no-frase-agua', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', conteudo: conteudoAtivo('Quero água', 'I want water'), ordem: 1, engineFeatureRef: 'tg-sobrancelha', propostoPorTreinamento: false },
  { id: 'no-frase-reposicionar', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', conteudo: conteudoAtivo('Quero trocar de posição', 'I want to reposition'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },

  // Phrases — Conversar
  { id: 'no-frase-oi', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Oi, tudo bem?', 'Hi, how are you?'), ordem: 1, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-obrigado', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Obrigado', 'Thank you'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-cansado', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Estou cansado', "I'm tired"), ordem: 3, engineFeatureRef: null, propostoPorTreinamento: false },

  // Phrases — Ajuda
  { id: 'no-frase-ajuda-agora', pacienteId: 'pac-marcio', parentId: 'no-cat-ajuda', tipo: 'frase', conteudo: conteudoAtivo('Preciso de ajuda agora', 'I need help now'), ordem: 1, engineFeatureRef: 'tg-sobrancelha', propostoPorTreinamento: false },
  { id: 'no-frase-nao-urgente', pacienteId: 'pac-marcio', parentId: 'no-cat-ajuda', tipo: 'frase', conteudo: conteudoAtivo('Pode ser daqui a pouco', 'It can wait a bit'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },

  // TREINAMENTO proposals (Emenda 4, ADR-016):
  // State 1: cuidadora-approved, awaiting patient (→ shows on Tela 09)
  {
    id: 'no-frase-agua-gelo', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', ordem: 3, engineFeatureRef: null, propostoPorTreinamento: true,
    conteudo: {
      pt: { texto: 'Quero água com gelo', audioRef: null, confirmadoCuidadora: true, confirmadoPaciente: false },
      en: { texto: 'I want water with ice', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
    },
  },
  // State 2: pending both (not visible in Tela 06 or Tela 09 yet)
  {
    id: 'no-frase-chamar-danilo', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', ordem: 4, engineFeatureRef: null, propostoPorTreinamento: true,
    conteudo: {
      pt: { texto: 'Chamar o Danilo', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
      en: { texto: 'Call Danilo', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
    },
  },
];

@Injectable()
export class NoArvoreFalaMockAdapter implements NoArvoreFalaPort {
  /** In-memory mutable dataset so patient confirmations are reflected
   *  within a session without a real backend. */
  private nodes: NoArvoreFala[] = MOCK_NOS_ARVORE_FALA.map(n => ({ ...n }));

  list(_locale?: Locale): Observable<NoArvoreFala[]> {
    // Return a snapshot so the facade's signal is not mutated by reference.
    return of(this.nodes.map(n => ({ ...n }))).pipe(delay(400));
  }

  selectNode(_nodeId: string, _locale: Locale): Observable<void> {
    // Audit-only — no state change in mock.
    return of(undefined).pipe(delay(50));
  }

  search(query: string, locale: Locale): Observable<NoArvoreFala[]> {
    // Simple substring match — approximates ES semantic search for dev/test.
    const q = query.toLowerCase();
    const results = this.nodes.filter(
      n => n.tipo === 'frase' && n.conteudo[locale].texto.toLowerCase().includes(q),
    );
    return of(results.map(n => ({ ...n }))).pipe(delay(400));
  }
}
