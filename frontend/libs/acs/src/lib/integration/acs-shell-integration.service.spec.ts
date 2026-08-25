import { TestBed } from '@angular/core/testing';
import { AcsShellIntegrationService } from './acs-shell-integration.service';
import { PatientLinkageComponent } from '../feature-patient-linkage/patient-linkage.component';
import { TenantSetupComponent } from '../feature-tenant-setup/tenant-setup.component';
import { ACS_TENANT_PORT } from '../api/acs-tenant.port';
import { ACS_CUIDADOR_PORT } from '../api/acs-cuidador.port';
import { ACS_LINK_PORT } from '../api/acs-link.port';
import { of } from 'rxjs';

// =============================================================================
// AcsShellIntegrationService — o passo de VÍNCULO deixou de ser placeholder.
//
// `onboardingWizardComposition()` devolvia `TenantSetupComponent` DUAS vezes: a
// segunda sob `TODO[FE-ACS-021]: replace step 5 placeholder with real
// CaregiverLinkStepComponent`, um componente que nunca existiu. O método também
// não tinha chamador nenhum, então o placeholder nunca apareceu em tela — a
// lacuna entre o Passo 3 do tutorial ("a coordenadora vincula cuidadoras ao
// paciente") e o produto era total.
// =============================================================================

const portaVazia = {
  get: () => of({ id: 't', nome: 'T', tipo: 'familia' as const, provisioningStatus: 'active' as const }),
  create: () => of({ id: 't', nome: 'T', tipo: 'familia' as const, provisioningStatus: 'active' as const }),
  retryProvisioning: () => of({ id: 't', nome: 'T', tipo: 'familia' as const, provisioningStatus: 'active' as const }),
};

describe('AcsShellIntegrationService — composição do assistente', () => {
  let service: AcsShellIntegrationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: ACS_TENANT_PORT, useValue: portaVazia },
        { provide: ACS_CUIDADOR_PORT, useValue: { list: () => of([]), create: () => of(null), deactivate: () => of(null) } },
        { provide: ACS_LINK_PORT, useValue: { list: () => of([]), link: () => of(null), revoke: () => of(null) } },
      ],
    });
    service = TestBed.inject(AcsShellIntegrationService);
  });

  it('resolveCaregiverLinkSlot() entrega a superfície de vínculo real', async () => {
    await expect(service.resolveCaregiverLinkSlot()).resolves.toBe(PatientLinkageComponent);
  });

  // CONTROLE NEGATIVO: é exatamente o valor que o placeholder devolvia. Sem esta
  // afirmação, remontar o TenantSetupComponent no lugar do vínculo volta a
  // passar despercebido — o passo continuaria "existindo".
  it('o passo de vínculo NÃO é o TenantSetupComponent remontado', async () => {
    const passos = service.onboardingWizardComposition();
    const vinculo = passos.find((p) => p.key === 'acs-caregiver-link');
    expect(vinculo).toBeDefined();
    const comp = await vinculo!.component;
    expect(comp).not.toBe(TenantSetupComponent);
    expect(comp).toBe(PatientLinkageComponent);
  });

  it('o passo de empresa continua sendo o TenantSetupComponent, na ordem 1', async () => {
    const passo = service.onboardingWizardComposition().find((p) => p.key === 'acs-tenant-setup');
    expect(passo?.order).toBe(1);
    await expect(passo!.component).resolves.toBe(TenantSetupComponent);
  });

  it('o vínculo vem DEPOIS do convite da cuidadora — não há a quem vincular antes', () => {
    const vinculo = service.onboardingWizardComposition().find((p) => p.key === 'acs-caregiver-link');
    expect(vinculo?.order).toBeGreaterThan(5);
  });
});
