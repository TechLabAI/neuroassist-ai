# CONV-FE-024 -- Telemetry events via @neuroassist/shared/observability (ConvServedDegraded, session events, node selection audit trail)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
`ConvObservabilityService` em `libs/conv/integration` emite os eventos de telemetria relevantes do CONV via `@neuroassist/shared/observability`: `ConvServedDegraded` (quando ES kNN degrada), eventos de sessão (init, destroy, session-revoked), e seleção de nó (para audit trail de voz disparada).

## Approach (execution path)
1. Criar `libs/conv/integration/src/lib/conv-observability.service.ts`:
   - Injetar `ObservabilityService` de `@neuroassist/shared/observability`.
   - Emitir `ConvServedDegraded` quando `SemanticAssistResultVM.degraded === true` (monitoramento OPS-003).
   - Emitir `ConvSessionStarted` / `ConvSessionEnded` no `CONV_BOOTSTRAP.init` / `destroy`.
   - Emitir `ConvNodeSelected { nodeId, via, locale }` após `NodeSelectionPort.select` bem-sucedido.
2. **Nenhum dado de paciente** nos payloads de telemetria — apenas IDs e eventos (LGPD).
3. Best-effort: falhas de envio de evento não bloqueiam o fluxo do paciente.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/integration/src/lib/conv-observability.service.ts`
- [ ] teste: `ConvServedDegraded` emitido quando `degraded: true` na resposta de search
- [ ] best-effort: falha de envio de telemetria não propaga erro para o usuário

## Acceptance criteria
- [ ] Payloads de telemetria não contêm texto de frase nem dado identificável de paciente (LGPD)
- [ ] `ConvServedDegraded` emitido no mesmo frame em que `degraded: true` é recebido
- [ ] Falha de envio de evento → fluxo do paciente não é interrompido
- [ ] `npx nx test conv-integration` PASS

## Dependencies
- CONV-FE-005 (ConvShellIntegrationService — coordenação de eventos)
- CONV-FE-007 (SemanticAssistFacade — fonte de `degraded`)
- INT-FE-CONV-005 (shared/observability telemetria integration)

## Empresa-context impact
Nenhum — eventos de telemetria não transitam dados multi-empresa.

## Prioridade
P8
