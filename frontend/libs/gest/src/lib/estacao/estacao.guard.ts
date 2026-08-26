import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';
import { DeviceSessionStore } from './device-session.store';

// =============================================================================
// estacaoDeviceGuard — guarda de rota /estacao (GEST-FE-030)
//
// Exige sessão de DISPOSITIVO (token com `edge.device`), não de usuário.
// DeviceSessionStore.autenticado() é true somente após autenticar() com
// credenciais de dispositivo via POST /oauth2/token/device — um token de
// usuário cuidadora/coordenadora nunca ativa esse estado.
//
// Sessão de usuário em /estacao → redirect para /
// Sem sessão alguma → redirect para /
// Sessão de dispositivo autenticada → acesso concedido
//
// Nota: o guard não chama autenticar() nem redireciona para tela de login
// de dispositivo. A tela de configuração do dispositivo é responsável por
// autenticar ANTES de navegar para /estacao.
// =============================================================================

export const estacaoDeviceGuard: CanActivateFn = () => {
  const deviceStore = inject(DeviceSessionStore);
  const router = inject(Router);

  if (deviceStore.autenticado()) {
    return true;
  }

  // Sessão de usuário comum ou ausência de sessão — redirecionar para raiz.
  // A raiz tem sessaoRequeridaGuard e levará para login se necessário.
  return router.createUrlTree(['/']);
};
