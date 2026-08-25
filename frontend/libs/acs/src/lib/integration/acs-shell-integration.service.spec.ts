import { TestBed } from '@angular/core/testing';
import { AcsShellIntegrationService } from './acs-shell-integration.service';
import { PatientLinkageComponent } from '../feature-patient-linkage/patient-linkage.component';
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

  // O controle negativo continua, com outra âncora.
  //
  // Ele afirmava que o passo de vínculo não era o `TenantSetupComponent`
  // remontado — que era o valor que o placeholder devolvia. O componente foi
  // REMOVIDO (era órfão: completo, testado, montado em lugar nenhum), então a
  // afirmação passou a ser sobre o que o passo É, e não sobre o que ele não é.
  // Afirmar a identidade é mais forte: pega qualquer substituição, não só a
  // antiga.
  it('o passo de vínculo é a superfície de vínculo real', async () => {
    const passos = service.onboardingWizardComposition();
    const vinculo = passos.find((p) => p.key === 'acs-caregiver-link');
    expect(vinculo).toBeDefined();
    await expect(vinculo!.component).resolves.toBe(PatientLinkageComponent);
  });

  it('o passo da empresa saiu da composição — quem o desenha é o assistente', () => {
    // Ele entregava o componente órfão. O assistente aprendeu os três estados de
    // provisionamento e é a única tela da empresa; duas telas para a mesma coisa,
    // uma delas invisível, garantem que a invisível apodreça.
    const passos = service.onboardingWizardComposition();
    expect(passos.find((p) => p.key === 'acs-tenant-setup')).toBeUndefined();
  });

  it('o vínculo vem DEPOIS do convite da cuidadora — não há a quem vincular antes', () => {
    const vinculo = service.onboardingWizardComposition().find((p) => p.key === 'acs-caregiver-link');
    expect(vinculo?.order).toBeGreaterThan(5);
  });
});
