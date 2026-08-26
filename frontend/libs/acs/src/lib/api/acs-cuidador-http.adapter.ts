import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import {
  AcsCuidadorPort,
  CuidadorDto,
  CuidadorCreateDto,
  CuidadorEditDto,
} from './acs-cuidador.port';

// =============================================================================
// AcsCuidadorHttpAdapter — production adapter (stub compilable)
// Base path: ${ACCESS_HTTP_BASE}/v1/acs/caregivers
// TODO[INT-FE-ACS-001]: X-Empresa-Id header via shared interceptor quando promovido de Wave-0 stub.
// TODO[INT-FE-ACS-002]: wire once acs-service is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsCuidadorHttpAdapter implements AcsCuidadorPort {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(ACCESS_HTTP_BASE)}/v1/acs/caregivers`;

  list(): Observable<CuidadorDto[]> {
    return this.http.get<CuidadorDto[]>(this.base);
  }

  get(id: string): Observable<CuidadorDto> {
    return this.http.get<CuidadorDto>(`${this.base}/${id}`);
  }

  create(dto: CuidadorCreateDto): Observable<CuidadorDto> {
    return this.http.post<CuidadorDto>(this.base, dto);
  }

  edit(id: string, dto: CuidadorEditDto): Observable<CuidadorDto> {
    // Caminho e verbo lidos do produtor (`CaregiverController.edit`), não
    // deduzidos do nome do método — é a regra desta casa, e o `deactivate` logo
    // abaixo é o contraexemplo vivo: ele faz `POST /{id}/deactivation` enquanto o
    // backend expõe `PATCH /{id}/status`. Divergência já relatada, não corrigida
    // aqui para não misturar assuntos.
    return this.http.put<CuidadorDto>(`${this.base}/${id}`, dto);
  }

  deactivate(id: string): Observable<CuidadorDto> {
    return this.http.post<CuidadorDto>(`${this.base}/${id}/deactivation`, {});
  }
}
