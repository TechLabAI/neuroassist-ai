import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto, CuidadorStatus } from './acs-cuidador.port';

// =============================================================================
// AcsCuidadorMockAdapter — dev/test (default adapter)
// Seed: Jamilly Amaral (matches prototype mock-data.ts).
//
// E2E test-support flags (localStorage, browser-only — never set in prod):
//   __ACS_E2E_LIST_FAIL__   = 'true'  → list()   throws (enables error-state e2e)
//   __ACS_E2E_CREATE_FAIL__ = 'true'  → create() throws (enables draft-preservation e2e)
//   __ACS_E2E_SLOW_LIST__   = 'true'  → list() demora 3s em vez de 400ms
//
// Sobre o __ACS_E2E_SLOW_LIST__: o cenário que observa o ESTADO DE CARREGAMENTO
// disputava uma janela de 400ms com o arranque do Angular. Contra o build de
// desenvolvimento dava tempo; contra o build de produção, que arranca mais
// rápido, o dado já tinha chegado quando o teste foi olhar — e o cenário
// reprovava sem que nada estivesse errado. Alongar a espera A PEDIDO do teste
// torna a janela observável sem mexer no tempo que a pessoa real espera.
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
  // Maria Santos — second active caregiver for multi-caregiver scenarios:
  //   link-002: cuid-maria → pac-marcio (enables non-last-covering deactivate test)
  //   link-003: cuid-maria → pac-outro  (enables last-covering revoke ADR-005 test)
  ['cuid-maria', {
    id: 'cuid-maria',
    tenantId: 'tenant-familia-amaral',
    nome: 'Maria Santos',
    email: 'maria.santos@example.com',
    papel: 'cuidadora',
    status: 'active' as CuidadorStatus,
  }],
  // cuid-inativo — inactive caregiver seeded for e2e scenario 10:
  //   "An inactive linked caregiver is excluded from the paciente coverage count."
  //   link-inativo: cuid-inativo → pac-inativo-linked (in acs-link-mock.adapter.ts)
  //   activeCaregivers() excludes this entry → pac-inativo-linked shows sem-cobertura.
  ['cuid-inativo', {
    id: 'cuid-inativo',
    tenantId: 'tenant-familia-amaral',
    nome: 'Inativo Cuidador',
    email: 'inativo@example.com',
    papel: 'cuidadora',
    status: 'inactive' as CuidadorStatus,
  }],
]);

let _idSeq = 1;

@Injectable({ providedIn: 'root' })
export class AcsCuidadorMockAdapter implements AcsCuidadorPort {
  list(): Observable<CuidadorDto[]> {
    const espera =
      typeof localStorage !== 'undefined' &&
      localStorage.getItem('__ACS_E2E_SLOW_LIST__') === 'true'
        ? 3000
        : 400;
    return timer(espera).pipe(
      switchMap(() => {
        // E2E test-support: localStorage flag forces an error to test the roster error state.
        if (typeof localStorage !== 'undefined' && localStorage.getItem('__ACS_E2E_LIST_FAIL__') === 'true') {
          throw new Error('Simulated network error (E2E test — __ACS_E2E_LIST_FAIL__)');
        }
        return of(Array.from(_cuidadores.values()));
      }),
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
        // E2E test-support: localStorage flag forces a failure to test draft preservation.
        if (typeof localStorage !== 'undefined' && localStorage.getItem('__ACS_E2E_CREATE_FAIL__') === 'true') {
          throw new Error('Simulated invite error (E2E test — __ACS_E2E_CREATE_FAIL__)');
        }
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
