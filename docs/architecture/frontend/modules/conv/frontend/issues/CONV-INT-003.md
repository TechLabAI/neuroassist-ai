# CONV-INT-003 -- GEST InputChannelPort composition at scope:web (ADR-005 — zero scope:conv→scope:gest import enforced by Nx boundaries)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
Em `apps/neuroassist-ai-web` (scope:web), `INPUT_CHANNEL_PORT` é vinculado ao `GestInputChannelAdapter` (backed pelo GEST engine) e ao `MockInputChannelAdapter` (e2e/Estágio 0), garantindo que `libs/conv/*` nunca importa diretamente nenhum artefato de `libs/gest/*`.

## Approach (execution path)
1. Criar `apps/neuroassist-ai-web/src/app/patient/gesture-input-channel.adapter.ts`:
   - Implementa `InputChannelPort`.
   - `start()`: delega para o engine GEST (via `scope:web` — o único lugar onde `scope:gest` pode ser importado).
   - `stop()`: libera todos os `MediaStream` tracks do GEST.
   - `recognizedGesture`: `Signal<{ engineFeatureRef: string } | null>` derivado do output do engine.
2. Criar `apps/neuroassist-ai-web/src/app/patient/mock-input-channel.adapter.ts`:
   - Implementa `InputChannelPort` com gestures scriptados (para e2e e Estágio 0).
   - `available: Signal<boolean>` sempre `true` no mock.
3. Registrar via `{ provide: INPUT_CHANNEL_PORT, useClass: GestInputChannelAdapter }` no AppModule / patient-route providers.
4. Verificar via `nx lint conv-*` que nenhuma lib `scope:conv` importa `scope:gest`.

## Implementation checklist (by layer/file)
- [ ] implementar `apps/neuroassist-ai-web/src/app/patient/gesture-input-channel.adapter.ts`
- [ ] implementar `apps/neuroassist-ai-web/src/app/patient/mock-input-channel.adapter.ts`
- [ ] `npx nx lint conv-shell-entry conv-integration conv-feature-speech-tree conv-data-access` PASS (nenhum import de `scope:gest`)
- [ ] teste: `MockInputChannelAdapter.recognizedGesture` emite `{ engineFeatureRef }` scriptado → `SpeechTreeFacade.selectNode` chamado

## Acceptance criteria
- [ ] `nx lint` não reporta nenhuma violação de boundary `scope:conv → scope:gest`
- [ ] `MockInputChannelAdapter` disponível para injeção em testes e2e
- [ ] Camera tracks liberados em `stop()` (zero camera leaks)
- [ ] `npx nx build neuroassist-ai-web` PASS

## Dependencies
- CONV-FE-005 (INPUT_CHANNEL_PORT token e interface)
- CONV-FE-002 (CONV_BOOTSTRAP — chama `INPUT_CHANNEL_PORT.start()/stop()`)
- `libs/gest/*` (GEST engine — importado apenas em scope:web)

## Empresa-context impact
Nenhum.

## Prioridade
P4
