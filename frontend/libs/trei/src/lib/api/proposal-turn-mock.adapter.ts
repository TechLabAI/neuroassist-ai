import { Injectable } from '@angular/core';
import { Observable, timer } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { ProposalTurnPort } from './proposal-turn.port';
import { ProposeRequest, ProposalTurnResponse, LlmProposal } from '../trei.types';

// =============================================================================
// ProposalTurnMockAdapter — dev/test (default adapter)
// Simulates the LLM proposal turn with a realistic 1200ms delay
// (models the assistant-thinking state — SPEC-03 first-token ≤ 2s invariant).
// Seed proposals are in Portuguese and English — locale from ProposeRequest.
// =============================================================================

let proposalCounter = 0;

const SEED_PROPOSALS: Record<string, string[]> = {
  pt: [
    'Preciso de água',
    'Estou com dor',
    'Por favor, me ajude a mudar de posição',
    'Estou com fome',
    'Quero descansar',
  ],
  en: [
    'I need water',
    'I am in pain',
    'Please help me change position',
    'I am hungry',
    'I want to rest',
  ],
};

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class ProposalTurnMockAdapter implements ProposalTurnPort {
  propose(sessaoId: string, request: ProposeRequest): Observable<ProposalTurnResponse> {
    // Simulate assistant-thinking latency (≤2s success signal from vision §7)
    const delay = e2eFlag('__TREI_E2E_FAST__') ? 200 : 1200;

    return timer(delay).pipe(
      map(() => {
        if (e2eFlag('__TREI_E2E_LLM_FAIL__')) {
          throw Object.assign(new Error('E2E: llm-unavailable'), { status: 503 });
        }

        const locale = request.tipo === 'frase' ? 'pt' : 'en';
        const seedPool = SEED_PROPOSALS[locale] ?? SEED_PROPOSALS['pt'];
        const proposals: LlmProposal[] = seedPool.slice(0, 2).map((texto) => {
          proposalCounter++;
          return {
            propostaId: `pr-mock-${proposalCounter}`,
            texto,
            tipo: request.tipo,
            locale: locale as 'pt' | 'en',
          };
        });

        return { propostas: proposals, aiDisclosed: true };
      }),
    );
  }
}
