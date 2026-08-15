import { Injectable, inject, signal, computed } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { ACS_CUIDADOR_PORT } from './api/acs-cuidador.port';
import type { CuidadorCreateDto } from './api/acs-cuidador.port';
import { ACS_LINK_PORT } from './api/acs-link.port';
import { CuidadorVm, CoverageVm, toCuidadorVm } from './acs.types';

// =============================================================================
// AcsCaregiverFacade — SPEC-03 §3, SPEC-04 §2/§3
// Roster state + derived coverage map (ADR-005).
// Components never import Port or adapters directly.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsCaregiverFacade {
  private readonly cuidadorPort = inject(ACS_CUIDADOR_PORT);
  private readonly linkPort = inject(ACS_LINK_PORT);

  private readonly _caregivers = signal<readonly CuidadorVm[]>([]);
  private readonly _selected = signal<CuidadorVm | null>(null);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<string | null>(null);
  private readonly _coverageMap = signal<ReadonlyMap<string, readonly string[]>>(new Map());
  /** Maps `${cuidadorId}:${pacienteId}` → active linkId for revokeLink calls. */
  private readonly _linkMap = signal<ReadonlyMap<string, string>>(new Map());

  readonly caregivers = this._caregivers.asReadonly();
  readonly selected = this._selected.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();

  /** Derived coverage signal: per pacienteId, array of active cuidador ids. */
  readonly coverageByPatient = this._coverageMap.asReadonly();

  /** Convenience: coverage vm for a specific patient. */
  coverageFor(pacienteId: string): CoverageVm {
    const coveredBy = this._coverageMap().get(pacienteId) ?? [];
    return { pacienteId, coveredBy, isCovered: coveredBy.length > 0 };
  }

  /**
   * Returns the active linkId for a given cuidador-patient pair, or null if not linked.
   * Used by PatientLinkageComponent to obtain the linkId before calling revokeLink().
   * Populated after loadRoster() / linkToPatient() / revokeLink() via _refreshCoverage*.
   */
  linkIdFor(cuidadorId: string, pacienteId: string): string | null {
    return this._linkMap().get(`${cuidadorId}:${pacienteId}`) ?? null;
  }

  /** Active caregivers only (for roster display). */
  readonly activeCaregivers = computed(() =>
    this._caregivers().filter((c) => c.isActive),
  );

  async loadRoster(): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const dtos = await lastValueFrom(this.cuidadorPort.list());
      const vms = dtos.map(toCuidadorVm);
      this._caregivers.set(vms);
      // Derive coverage: gather links for each active caregiver
      await this._refreshCoverageMap(vms.filter((c) => c.isActive));
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao carregar cuidadores');
    } finally {
      this._loading.set(false);
    }
  }

  async loadOne(id: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const dto = await lastValueFrom(this.cuidadorPort.get(id));
      this._selected.set(toCuidadorVm(dto));
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Cuidador não encontrado');
      this._selected.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  async invite(dto: CuidadorCreateDto): Promise<CuidadorVm | null> {
    this._saving.set(true);
    this._error.set(null);
    try {
      const result = await lastValueFrom(this.cuidadorPort.create(dto));
      const vm = toCuidadorVm(result);
      this._caregivers.update((list) => [...list, vm]);
      return vm;
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao convidar cuidador');
      return null;
    } finally {
      this._saving.set(false);
    }
  }

  async deactivate(id: string): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      const dto = await lastValueFrom(this.cuidadorPort.deactivate(id));
      const vm = toCuidadorVm(dto);
      this._caregivers.update((list) =>
        list.map((c) => (c.id === id ? vm : c)),
      );
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao desativar cuidador');
    } finally {
      this._saving.set(false);
    }
  }

  async linkToPatient(cuidadorId: string, pacienteId: string): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await lastValueFrom(this.linkPort.link(cuidadorId, pacienteId));
      // Refresh coverage for the affected patient
      await this._refreshCoverageForPatient(pacienteId);
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao vincular cuidador ao paciente');
    } finally {
      this._saving.set(false);
    }
  }

  /** Revoke link. ADR-005: 409 last-covering-block → error signal with user-visible message. */
  async revokeLink(cuidadorId: string, linkId: string, pacienteId: string): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await lastValueFrom(this.linkPort.revoke(cuidadorId, linkId));
      await this._refreshCoverageForPatient(pacienteId);
    } catch (err: unknown) {
      const status = err instanceof Object && 'status' in err ? (err as { status: number }).status : 0;
      if (status === 409) {
        this._error.set('Não é possível remover o último vínculo ativo deste paciente (ADR-005). Adicione outro cuidador primeiro.');
      } else {
        this._error.set(err instanceof Error ? err.message : 'Erro ao revogar vínculo');
      }
    } finally {
      this._saving.set(false);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private async _refreshCoverageMap(activeCaregivers: readonly CuidadorVm[]): Promise<void> {
    const patientMap = new Map<string, string[]>();
    const linkMap = new Map<string, string>();
    for (const caregiver of activeCaregivers) {
      try {
        const links = await lastValueFrom(this.linkPort.listForCaregiver(caregiver.id));
        for (const link of links) {
          if (!link.active) continue;
          const current = patientMap.get(link.pacienteId) ?? [];
          current.push(caregiver.id);
          patientMap.set(link.pacienteId, current);
          linkMap.set(`${caregiver.id}:${link.pacienteId}`, link.id);
        }
      } catch { /* best-effort */ }
    }
    this._coverageMap.set(patientMap);
    this._linkMap.set(linkMap);
  }

  private async _refreshCoverageForPatient(pacienteId: string): Promise<void> {
    const active = this._caregivers().filter((c) => c.isActive);
    const ids: string[] = [];
    const linkUpdates = new Map<string, string>();
    const toRemove: string[] = [];

    for (const c of active) {
      try {
        const links = await lastValueFrom(this.linkPort.listForCaregiver(c.id));
        const activeLink = links.find((l) => l.pacienteId === pacienteId && l.active);
        const key = `${c.id}:${pacienteId}`;
        if (activeLink) {
          ids.push(c.id);
          linkUpdates.set(key, activeLink.id);
        } else {
          toRemove.push(key);
        }
      } catch { /* best-effort */ }
    }

    this._coverageMap.update((m) => {
      const updated = new Map(m);
      updated.set(pacienteId, ids);
      return updated;
    });

    this._linkMap.update((m) => {
      const updated = new Map(m);
      for (const [k, v] of linkUpdates) updated.set(k, v);
      for (const k of toRemove) updated.delete(k);
      return updated;
    });
  }
}
