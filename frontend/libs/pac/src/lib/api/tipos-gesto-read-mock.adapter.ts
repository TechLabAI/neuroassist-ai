import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TiposGestoReadPort, TipoGesto } from './tipos-gesto-read.port';

// ---------------------------------------------------------------------------
// Mock seed — sourced from prototype shared/mock-data.ts
// "Leitura labial" illustrates an extensible catalog (Emenda 2, 2026-07-27).
// ---------------------------------------------------------------------------

let _tipos: TipoGesto[] = [
  { id: 'tg-sobrancelha',   nome: 'Levantar sobrancelha' },
  { id: 'tg-piscar-duplo',  nome: 'Piscar duas vezes' },
  { id: 'tg-piscar-rapido', nome: 'Piscar rápido' },
  { id: 'tg-piscar-devagar',nome: 'Piscar devagar' },
  { id: 'tg-fechar-olhos',  nome: 'Fechar os olhos' },
  { id: 'tg-mover-nariz',   nome: 'Mover o nariz' },
  { id: 'tg-leitura-labial',nome: 'Leitura labial' },
];

// ---------------------------------------------------------------------------
// TiposGestoReadMockAdapter — dev/test only.
// requestNew() simulates GEST accepting a new primitive name (naming only,
// not detectability — TODO[arquiteto] in Framing §6.1/§9.3).
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
    const novo: TipoGesto = { id, nome: nome.trim() };
    _tipos = [..._tipos, novo];
    return of(novo).pipe(delay(400));
  }
}
