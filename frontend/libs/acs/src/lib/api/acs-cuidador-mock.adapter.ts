import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto, CuidadorStatus } from './acs-cuidador.port';

// =============================================================================
// AcsCuidadorMockAdapter — dev/test (default adapter)
// Seed: Jamilly Amaral (matches prototype mock-data.ts).
// =============================================================================

const _cuidadores = new Map<string, CuidadorDto>([
  ['cuid-jamilly', {
    id: 'cuid-jamilly',
    tenantId: 'tenant-familia-amaral',
    nome: 'Jamilly Amaral',
    email: 'jamilly.amaral@example.com',
    papel: 'cuidadora',
    status: 'active' as CuidadorStatus,
  }],
]);

let _idSeq = 1;

@Injectable({ providedIn: 'root' })
export class AcsCuidadorMockAdapter implements AcsCuidadorPort {
  list(): Observable<CuidadorDto[]> {
    return timer(400).pipe(
      switchMap(() => of(Array.from(_cuidadores.values()))),
    );
  }

  get(id: string): Observable<CuidadorDto> {
    return timer(400).pipe(
      switchMap(() => {
        const c = _cuidadores.get(id);
        if (!c) throw new Error(`mock: cuidador not found: ${id}`);
        return of(c);
      }),
    );
  }

  create(dto: CuidadorCreateDto): Observable<CuidadorDto> {
    return timer(800).pipe(
      switchMap(() => {
        const id = `cuid-${Date.now()}-${++_idSeq}`;
        const cuidador: CuidadorDto = {
          id,
          tenantId: 'tenant-familia-amaral',
          nome: dto.nome,
          email: dto.email,
          papel: dto.papel,
          status: 'active',
        };
        _cuidadores.set(id, cuidador);
        return of(cuidador);
      }),
    );
  }

  deactivate(id: string): Observable<CuidadorDto> {
    return timer(600).pipe(
      switchMap(() => {
        const c = _cuidadores.get(id);
        if (!c) throw new Error(`mock: cuidador not found: ${id}`);
        const updated: CuidadorDto = { ...c, status: 'inactive' };
        _cuidadores.set(id, updated);
        return of(updated);
      }),
    );
  }
}
