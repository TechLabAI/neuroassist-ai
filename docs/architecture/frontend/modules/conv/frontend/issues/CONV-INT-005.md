# CONV-INT-005 -- shared/observability telemetry integration (ConvServedDegraded event, session lifecycle events)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
`ConvObservabilityService` (CONV-FE-024) está integrado com `@neuroassist/shared/observability` e os eventos `ConvServedDegraded`, `ConvSessionStarted`, `ConvSessionEnded` são emitidos nos pontos corretos do ciclo de vida sem bloquear o fluxo do paciente em caso de falha de envio.

## Approach (execution path)
1. Verificar o contrato de `ObservabilityService` em `@neuroassist/shared/observability`: tipo de payload, método `emit(eventName, payload)` ou equivalente.
2. No `ConvObservabilityService`:
   - `emit('ConvServedDegraded', { locale, reason })` — sem dados de paciente.
   - `emit('ConvSessionStarted', { pacienteIdHash })` — hash de `pacienteId`, nunca nome/dados clínicos.
   - `emit('ConvSessionEnded', { reason: 'revoked' | 'user-exit' })`.
3. Wrapping de todas as chamadas em `try/catch` (ou `catchError`) — falha de telemetria é best-effort.
4. Verificar que payloads não contêm `texto`, `categoryLabel`, `variants` ou qualquer dado de frase (LGPD).

## Implementation checklist (by layer/file)
- [ ] `conv-observability.service.ts` usa `@neuroassist/shared/observability` sem redefini-lo
- [ ] payloads auditados: nenhum dado clínico ou de frase incluído
- [ ] teste: falha de emissão de telemetria não propaga para o usuário

## Acceptance criteria
- [ ] Payloads não contêm texto de frase, nome de paciente ou dado identificável (LGPD)
- [ ] `ConvServedDegraded` emitido quando `degraded: true` no SemanticAssistFacade
- [ ] Falha de envio de telemetria → fluxo de paciente não interrompido

## Dependencies
- `@neuroassist/shared/observability` (contrato de emissão de eventos)
- CONV-FE-024 (ConvObservabilityService — implementação)

## Empresa-context impact
Nenhum.

## Prioridade
P4
