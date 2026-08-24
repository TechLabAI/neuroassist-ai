export {
  ToastService,
  type Aviso,
  type AcaoDeAviso,
  type TipoDeAviso,
} from './lib/toast.service';
export { ToastOutletComponent } from './lib/toast-outlet.component';

export { BannerService, type Faixa } from './lib/banner.service';
export { BannerOutletComponent } from './lib/banner-outlet.component';

export { ModalService, type Modal, type AcaoDeModal } from './lib/modal.service';
export { ModalOutletComponent } from './lib/modal-outlet.component';

// Mantido: havia código referenciando a constante do stub Wave-0.
export const UI_FEEDBACK_VERSION = '1.0.0';
