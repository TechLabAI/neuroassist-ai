import { Injectable, signal } from '@angular/core';

// =============================================================================
// CameraPreferenceService — ADR-007, follow-up 2
//
// Escolha de câmera para o browser. O `deviceId` do enumerateDevices() é
// escopado por origem + permissão e rotaciona quando o usuário limpa as
// permissões: é preferência DE MÁQUINA, não estado de domínio. Por isso mora em
// localStorage e não é modelado no EDGE — lá o que existe é o dispositivo, não a
// câmera dele. Ver ADR-007, "Configuração de câmera".
//
// Todo acesso a localStorage vai em try/catch: em aba anônima ou com
// armazenamento bloqueado o getter lança, e a calibração não pode cair por causa
// de uma preferência.
// =============================================================================

const STORAGE_KEY = 'neuroassist.gest.cameraDeviceId';

export interface CameraOption {
  readonly deviceId: string;
  readonly label: string;
}

@Injectable({ providedIn: 'root' })
export class CameraPreferenceService {
  private readonly _cameras = signal<readonly CameraOption[]>([]);
  private readonly _selected = signal<string | null>(readStored());

  /** Câmeras disponíveis. Vazio até refresh() rodar. */
  readonly cameras = this._cameras.asReadonly();
  /** deviceId escolhido, ou null para "deixar o browser decidir". */
  readonly selected = this._selected.asReadonly();

  /**
   * Lista as câmeras do sistema.
   *
   * Antes da primeira permissão concedida o browser devolve `label` vazio (é
   * proteção contra fingerprinting) — por isso o fallback numerado. Chamar de
   * novo depois de um getUserMedia bem-sucedido traz os nomes reais.
   */
  async refresh(): Promise<void> {
    if (!navigator.mediaDevices?.enumerateDevices) {
      this._cameras.set([]);
      return;
    }
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices
        .filter((d) => d.kind === 'videoinput')
        .map((d, i) => ({
          deviceId: d.deviceId,
          label: d.label || `Câmera ${i + 1}`,
        }));
      this._cameras.set(cameras);

      // A câmera guardada pode ter sumido (desconectada, ou id rotacionado).
      // Cair para o default do browser é melhor que falhar o getUserMedia.
      const atual = this._selected();
      if (atual && !cameras.some((c) => c.deviceId === atual)) {
        this.select(null);
      }
    } catch {
      this._cameras.set([]);
    }
  }

  select(deviceId: string | null): void {
    this._selected.set(deviceId);
    try {
      if (deviceId) {
        localStorage.setItem(STORAGE_KEY, deviceId);
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      /* armazenamento indisponível — a escolha vale só para esta sessão */
    }
  }

  /**
   * Constraint para o getUserMedia. `exact` de propósito: se a câmera escolhida
   * não estiver disponível é melhor falhar visivelmente do que silenciosamente
   * calibrar na câmera errada — é a webcam apontada para o paciente.
   */
  videoConstraints(): MediaTrackConstraints | true {
    const deviceId = this._selected();
    return deviceId ? { deviceId: { exact: deviceId } } : true;
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
