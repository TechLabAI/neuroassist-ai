import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto, CuidadorStatus } from './acs-cuidador.port';
import { ACS_E2E_FLAGS, ACS_E2E_CUIDADORA_OUTRA_EMPRESA } from './acs-e2e-flags';
import {
  vinculosAtivosDoCuidador,
  contarVinculosAtivosDoPaciente,
} from './acs-link-mock.adapter';

/** Lê uma bandeira de e2e sem estourar onde não há `localStorage`. */
function bandeiraLigada(bandeira: string): boolean {
  return typeof localStorage !== 'undefined' && localStorage.getItem(bandeira) === 'true';
}

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
    const espera = bandeiraLigada(ACS_E2E_FLAGS.listaLenta) ? 3000 : 400;
    return timer(espera).pipe(
      switchMap(() => {
        // E2E test-support: localStorage flag forces an error to test the roster error state.
        if (bandeiraLigada(ACS_E2E_FLAGS.falhaListar)) {
          throw new Error(`Simulated network error (E2E test — ${ACS_E2E_FLAGS.falhaListar})`);
        }
        const elenco = Array.from(_cuidadores.values());
        // Cuidadora de OUTRA empresa — só quando a suíte pede. Ver o comentário
        // da bandeira em acs-e2e-flags.ts para o porquê de não estar na semente.
        if (bandeiraLigada(ACS_E2E_FLAGS.crossEmpresa)) {
          elenco.push({
            id: ACS_E2E_CUIDADORA_OUTRA_EMPRESA.id,
            tenantId: ACS_E2E_CUIDADORA_OUTRA_EMPRESA.tenantId,
            nome: ACS_E2E_CUIDADORA_OUTRA_EMPRESA.nome,
            email: 'fora@outra-empresa.example.com',
            papel: 'cuidadora',
            status: 'active' as CuidadorStatus,
          });
        }
        return of(elenco);
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
        if (bandeiraLigada(ACS_E2E_FLAGS.falhaCriar)) {
          throw new Error(`Simulated invite error (E2E test — ${ACS_E2E_FLAGS.falhaCriar})`);
        }

        // Login único por empresa. NÃO é uma conveniência de teste: o
        // `CaregiverService.create` do acs-service normaliza o login para
        // minúsculas e estoura `LoginDuplicateException`, que o
        // GlobalExceptionHandler converte em 409. O mock que aceitava e-mail
        // repetido concordava com o consumidor e discordava do produtor — a tela
        // ficava verde convidando duas pessoas com o mesmo login, que em produção
        // toma 409.
        const login = dto.email.toLowerCase();
        if (Array.from(_cuidadores.values()).some((c) => c.email.toLowerCase() === login)) {
          throw Object.assign(
            new Error('Já existe um cuidador com este e-mail nesta empresa.'),
            { status: 409, type: '/errors/login-duplicate' },
          );
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

        // D-204 (SAFETY-CRITICAL) — o mock MENTIA sobre o backend aqui.
        //
        // `CaregiverService.updateStatus` do acs-service, ao inativar, percorre os
        // vínculos ativos do cuidador e estoura `LastCaregiverLinkException` (409)
        // se algum paciente ficaria com zero cobertura. O mock aceitava tudo, então
        // a única barreira nos testes era a pré-checagem da facade — e um mock que
        // aceita o que o backend recusa é precisamente o que faz o gate ficar verde
        // enquanto a integração quebra.
        //
        // Conta LINHAS DE VÍNCULO, não status de cuidador, porque é o que
        // `countActiveByPacienteId` conta.
        for (const vinculo of vinculosAtivosDoCuidador(id)) {
          if (contarVinculosAtivosDoPaciente(vinculo.pacienteId) <= 1) {
            throw Object.assign(
              new Error('Não é possível desativar: este cuidador é o último a cobrir um paciente.'),
              { status: 409, type: '/errors/last-covering-caregiver' },
            );
          }
        }

        const updated: CuidadorDto = { ...c, status: 'inactive' };
        _cuidadores.set(id, updated);
        return of(updated);
      }),
    );
  }
}
