import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsTenantPort, TenantDto, TenantCreateDto, ProvisioningStatus } from './acs-tenant.port';
import { ACS_E2E_FLAGS } from './acs-e2e-flags';

// =============================================================================
// AcsTenantMockAdapter — dev/test (default adapter)
// Seed: Família Amaral (matches prototype mock-data.ts).
// Delay 400ms on reads; 800ms on writes.
//
// Bandeiras de e2e (localStorage, só no browser — nunca em produção):
//   __ACS_E2E_TENANT_PROVISIONING__  → empresa nasce/está EM PROVISIONAMENTO
//   __ACS_E2E_TENANT_RETRY_STUCK__   → a retentativa não resolve
//
// POR QUE ELAS EXISTEM: até aqui o mock devolvia `active` incondicionalmente nos
// três métodos. O backend provisiona schema por SAGA e responde 202; o estado
// intermediário é real, `AcsTenantFacade.provisioning()` já o representa, e não
// havia como um teste chegar nele — a superfície de tela do provisionamento não
// podia sequer ser exercitada.
//
// Os literais vêm de `ACS_E2E_FLAGS` e nunca são redigitados aqui: quem escreve
// a bandeira é a suíte, quem a lê é este arquivo, e um literal por lado é o modo
// clássico de a bandeira ficar inerte sem ninguém notar.
// =============================================================================

/** Lê uma bandeira de e2e sem estourar onde não há `localStorage`. */
function bandeiraLigada(bandeira: string): boolean {
  return typeof localStorage !== 'undefined' && localStorage.getItem(bandeira) === 'true';
}

const _tenants = new Map<string, TenantDto>([
  ['tenant-familia-amaral', {
    id: 'tenant-familia-amaral',
    nome: 'Família Amaral',
    tipo: 'familia',
    provisioningStatus: 'active' as ProvisioningStatus,
  }],
]);

let _idSeq = 1;

/**
 * Estado com que uma empresa nasce nesta execução.
 *
 * <p>Lido a CADA chamada, não uma vez no carregamento do módulo: o
 * `addInitScript` do Playwright roda antes do bundle, mas o mock é `providedIn:
 * 'root'` e sobrevive entre navegações — congelar o valor faria a segunda
 * navegação de um mesmo teste responder com a bandeira da primeira.
 */
function estadoInicial(): ProvisioningStatus {
  return bandeiraLigada(ACS_E2E_FLAGS.empresaProvisionando) ? 'provisioning' : 'active';
}

@Injectable({ providedIn: 'root' })
export class AcsTenantMockAdapter implements AcsTenantPort {
  get(id: string): Observable<TenantDto> {
    return timer(400).pipe(
      switchMap(() => {
        const t = _tenants.get(id);
        if (!t) throw new Error(`mock: tenant not found: ${id}`);
        // A bandeira também vale para o registro SEMEADO: uma empresa recarregada
        // no meio da saga é o mesmo estado que uma recém-criada por 202.
        const estado = estadoInicial();
        return of(estado === 'active' ? t : { ...t, provisioningStatus: estado });
      }),
    );
  }

  create(dto: TenantCreateDto): Observable<TenantDto> {
    return timer(800).pipe(
      switchMap(() => {
        const id = `tenant-${Date.now()}-${++_idSeq}`;
        const tenant: TenantDto = {
          id,
          nome: dto.nome,
          tipo: dto.tipo,
          provisioningStatus: estadoInicial(),
        };
        _tenants.set(id, tenant);
        return of(tenant);
      }),
    );
  }

  retryProvisioning(id: string): Observable<TenantDto> {
    return timer(600).pipe(
      switchMap(() => {
        const t = _tenants.get(id);
        if (!t) throw new Error(`mock: tenant not found: ${id}`);
        // `retryNaoResolve` mantém `provisioning`, e não `failed`, DE PROPÓSITO:
        // o backend só emite PROVISIONING|ACTIVE|SUSPENDED — o `failed` do tipo
        // do frontend não tem produtor nenhum (divergência relatada, não
        // corrigida aqui). Um mock que devolvesse `failed` estaria ensinando a
        // tela a esperar algo que nunca chega.
        const estado: ProvisioningStatus = bandeiraLigada(ACS_E2E_FLAGS.retryNaoResolve)
          ? 'provisioning'
          : 'active';
        const updated: TenantDto = { ...t, provisioningStatus: estado };
        _tenants.set(id, updated);
        return of(updated);
      }),
    );
  }
}
