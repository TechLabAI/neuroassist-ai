import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { PacPacientePort, Paciente, PacienteCreate, PacienteUpdate, Page } from './paciente.port';

// ---------------------------------------------------------------------------
// Mock seed — sourced from prototype shared/mock-data.ts
// ---------------------------------------------------------------------------

const MOCK_PACIENTES: Paciente[] = [
  {
    id: 'pac-marcio',
    tenantId: 'tenant-familia-amaral',
    nome: 'Márcio Amaral',
    dispositivo: 'edge-marcio-01',
    catalogoVersao: 'v1',
  },
];

let _pacientes = [...MOCK_PACIENTES];

// E2E test hook: set window.__PAC_E2E_NO_PATIENT = true (via page.addInitScript) to simulate
// a fresh setup where no Paciente has been registered yet. The guard will then redirect
// to /onboarding as intended for the "no registered patient" scenario.
declare global {
  interface Window {
    __PAC_E2E_NO_PATIENT?: boolean;
  }
}

// ---------------------------------------------------------------------------
// PacienteMockAdapter — dev/test only. No backend required.
// Delay 400ms simulates realistic latency.
// ---------------------------------------------------------------------------

@Injectable()
export class PacienteMockAdapter implements PacPacientePort {
  list(page = 0, size = 20): Observable<Page<Paciente>> {
    // E2E test hook: simulate no registered patient when flag is active
    const isE2ENoPatient =
      typeof window !== 'undefined' && window.__PAC_E2E_NO_PATIENT === true;

    const start = page * size;
    const source = isE2ENoPatient ? [] : _pacientes;
    const content = source.slice(start, start + size);
    return of<Page<Paciente>>({
      content,
      totalElements: source.length,
      totalPages: Math.max(1, Math.ceil(source.length / size)),
      number: page,
      size,
    }).pipe(delay(400));
  }

  get(id: string): Observable<Paciente> {
    const found = _pacientes.find((p) => p.id === id);
    if (!found) throw new Error(`PacienteMockAdapter: not found ${id}`);
    return of(found).pipe(delay(400));
  }

  create(dto: PacienteCreate, _idempotencyKey?: string): Observable<HttpResponse<Paciente>> {
    const paciente: Paciente = {
      id: `pac-${Date.now()}`,
      tenantId: 'tenant-familia-amaral',
      nome: dto.nome,
      dispositivo: null,
      catalogoVersao: 'v1',
    };
    _pacientes = [..._pacientes, paciente];
    return of(
      new HttpResponse<Paciente>({ status: 201, body: paciente }),
    ).pipe(delay(400));
  }

  update(id: string, dto: PacienteUpdate, _ifMatch: string): Observable<Paciente> {
    _pacientes = _pacientes.map((p) => (p.id === id ? { ...p, ...dto } : p));
    const updated = _pacientes.find((p) => p.id === id)!;
    return of(updated).pipe(delay(400));
  }
}
