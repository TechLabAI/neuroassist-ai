import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TiposGestoReadPort, TipoGesto } from './tipos-gesto-read.port';
import { ENGINE_FEATURE_REFS, GESTURE_GUIDANCE } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Semente do catálogo.
//
// A lista anterior vinha do `shared/mock-data.ts` do protótipo e oferecia sete
// movimentos como se todos funcionassem — sendo que NENHUM dos ids batia com o
// que o motor emite (`tg-piscar-duplo` contra `tg-piscar-duplo-001`), e quatro
// deles não têm detector nenhum em lugar algum.
//
// Agora a lista é derivada de `GESTURE_GUIDANCE`: os detectáveis são exatamente
// os que o motor emite, e a derivação impede que voltem a divergir — acrescentar
// um gesto ao motor o traz para cá; tirá-lo daqui é impossível sem tirá-lo de lá.
//
// Os movimentos sem motor CONTINUAM no catálogo, e é de propósito: eles ilustram
// a extensibilidade (Emenda 2, 2026-07-27) e são pedidos reais de clínicas. O que
// muda é que agora dizem a verdade sobre si — `registered` significa "pedido
// registrado, ainda não detectável", e a tela os mostra desabilitados com o
// motivo em vez de fingir que são escolhas válidas.
// ---------------------------------------------------------------------------

const DETECTAVEIS: TipoGesto[] = ENGINE_FEATURE_REFS.map((ref) => ({
  // No banco o id é uuid; no mock usamos a própria referência de motor como id,
  // o que mantém o mock legível SEM reintroduzir a confusão: quem compara com a
  // detecção é `engineFeatureRef`, nunca o id.
  id: ref,
  nome: GESTURE_GUIDANCE[ref].nomePadrao,
  detectabilityState: 'supported' as const,
  engineFeatureRef: ref,
}));

const PEDIDOS_SEM_MOTOR: TipoGesto[] = [
  { id: 'tg-piscar-rapido', nome: 'Piscar rápido', detectabilityState: 'registered', engineFeatureRef: null },
  { id: 'tg-piscar-devagar', nome: 'Piscar devagar', detectabilityState: 'registered', engineFeatureRef: null },
  { id: 'tg-fechar-olhos', nome: 'Fechar os olhos', detectabilityState: 'pending_training', engineFeatureRef: null },
  { id: 'tg-mover-nariz', nome: 'Mover o nariz', detectabilityState: 'registered', engineFeatureRef: null },
  { id: 'tg-leitura-labial', nome: 'Leitura labial', detectabilityState: 'registered', engineFeatureRef: null },
];

let _tipos: TipoGesto[] = [...DETECTAVEIS, ...PEDIDOS_SEM_MOTOR];

// ---------------------------------------------------------------------------
// TiposGestoReadMockAdapter — dev/test only.
// requestNew() simulates GEST accepting a new primitive name. Naming is NOT
// detectability — o tipo novo nasce `registered`, e a distincao agora esta no
// modelo em vez de num TODO (resolvido; era Framing 6.1/9.3).
// ---------------------------------------------------------------------------

@Injectable()
export class TiposGestoReadMockAdapter implements TiposGestoReadPort {
  list(): Observable<TipoGesto[]> {
    return of([..._tipos]).pipe(delay(400));
  }

  get(id: string): Observable<TipoGesto> {
    const found = _tipos.find((t) => t.id === id);
    if (!found) throw new Error(`TiposGestoReadMockAdapter: not found ${id}`);
    return of(found).pipe(delay(400));
  }

  requestNew(nome: string): Observable<TipoGesto> {
    const id = `tg-${nome
      .trim()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-') || Date.now()}`;
    const existing = _tipos.find((t) => t.id === id);
    if (existing) return of(existing).pipe(delay(400));
    // `registered`, nunca `supported`: pedir um nome não cria capacidade de
    // detecção. Era exatamente o que o TODO[arquiteto] acima descrevia como
    // pendência ("naming only, not detectability") — o modelo não tinha onde
    // guardar a diferença, então o tipo novo nascia indistinguível de um
    // detectável. Agora nasce dizendo o que é.
    const novo: TipoGesto = {
      id,
      nome: nome.trim(),
      detectabilityState: 'registered',
      engineFeatureRef: null,
    };
    _tipos = [..._tipos, novo];
    return of(novo).pipe(delay(400));
  }
}
