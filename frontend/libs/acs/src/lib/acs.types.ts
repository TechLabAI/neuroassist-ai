// =============================================================================
// acs.types.ts — view-model types for ACS feature components
// Components consume these Vm types; never the raw DTOs directly.
// =============================================================================

export type TenantTipo = 'familia' | 'clinica';
export type ProvisioningStatus = 'active' | 'provisioning' | 'failed';
export type CuidadorPapel = 'cuidadora' | 'coordenador_clinica';
export type CuidadorStatus = 'active' | 'inactive';

/** Tenant view-model rendered in the onboarding wizard step 1 and roster header. */
export interface TenantVm {
  readonly id: string;
  readonly nome: string;
  readonly tipo: TenantTipo;
  readonly provisioningStatus: ProvisioningStatus;
  readonly isActive: boolean; // provisioningStatus === 'active'
}

/** Caregiver view-model rendered in the roster and editor. */
export interface CuidadorVm {
  readonly id: string;
  readonly nome: string;
  readonly email: string;
  readonly papel: CuidadorPapel;
  readonly status: CuidadorStatus;
  readonly isActive: boolean; // status === 'active'
  readonly papelLabel: string; // pt-BR label
}

/** Coverage state for a given pacienteId — derived client-side (ADR-005). */
export interface CoverageVm {
  readonly pacienteId: string;
  readonly coveredBy: readonly string[]; // active cuidador ids linked to this patient
  readonly isCovered: boolean;           // coveredBy.length > 0
}

/** Draft used by the caregiver editor form. */
export interface CaregiverDraft {
  nome: string;
  email: string;
  papel: CuidadorPapel;
}

export function toPapelLabel(papel: CuidadorPapel): string {
  return papel === 'cuidadora' ? 'Cuidadora' : 'Coordenador de Clínica';
}

export function toCuidadorVm(dto: { id: string; nome: string; email: string; papel: CuidadorPapel; status: CuidadorStatus; tenantId: string }): CuidadorVm {
  return {
    id: dto.id,
    nome: dto.nome,
    email: dto.email,
    papel: dto.papel,
    status: dto.status,
    isActive: dto.status === 'active',
    papelLabel: toPapelLabel(dto.papel),
  };
}

export function toTenantVm(dto: { id: string; nome: string; tipo: TenantTipo; provisioningStatus: ProvisioningStatus }): TenantVm {
  return {
    id: dto.id,
    nome: dto.nome,
    tipo: dto.tipo,
    provisioningStatus: dto.provisioningStatus,
    isActive: dto.provisioningStatus === 'active',
  };
}
