export type {
  EdgeRecognitionSignal,
  ComandoDraft,
  GestureTestVM,
  GestureTestState,
  GestureTestStep,
  DegradedReason,
  DetectorType,
  GestureTestOutcome,
  EligibilityResult,
  Sensibilidade,
  Urgencia,
} from './lib/contracts';

export type {
  PairingState,
  WireStatus,
  DispositivoEdgeView,
  RegisterDeviceResult,
  LivenessView,
  NotModified,
  DerivedState,
  ConnectivityTone,
  DeviceConnectivityVM,
  PairingVM,
  PairingPhase,
  DeviceDetailVM,
  PairedDeviceOutcome,
  EdgeReadModelDeps,
} from './lib/edge-contracts';

export type {
  OnboardingWizardStep,
  OnboardingWizardContext,
  OnboardingWizardContributor,
} from './lib/onboarding-wizard';

// Vocabulário de gestos — o nome canônico que motor, catálogo e banco compartilham.
export {
  ENGINE_FEATURE,
  ENGINE_FEATURE_REFS,
  GESTURE_GUIDANCE,
  isEngineFeatureRef,
  ehDetectavel,
  motivoIndisponivel,
  guidanceDe,
} from './lib/gesture-vocabulary';
export type {
  EngineFeatureRef,
  DetectabilityState,
  GestureGuidance,
} from './lib/gesture-vocabulary';

// Token de contexto HTTP compartilhado — ver o cabeçalho de http-context.ts
// para por que ele vive aqui e não em auth-login.
export { SKIP_AUTH_HEADER } from './lib/http-context';
