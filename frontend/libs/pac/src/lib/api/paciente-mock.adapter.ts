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

// ---------------------------------------------------------------------------
// PacienteMockAdapter — dev/test only. No backend required.
// Delay 400ms simulates realistic latency.
// ---------------------------------------------------------------------------

@Injectable()
export class PacienteMockAdapter implements PacPacientePort {
  list(page = 0, size = 20): Observable<Page<Paciente>> {
    const start = page * size;
    const content = _pacientes.slice(start, start + size);
    return of<Page<Paciente>>({
      content,
      totalElements: _pacientes.length,
      totalPages: Math.ceil(_pacientes.length / size),
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
