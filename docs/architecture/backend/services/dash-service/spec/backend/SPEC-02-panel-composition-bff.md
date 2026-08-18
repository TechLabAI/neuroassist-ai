# SPEC-02 — Panel Composition (Backend-for-Frontend)

## Objetivo
Especificar a composição das telas do painel por **fan-out paralelo** para os serviços donos, retornando
view models moldados para a UI, com **degradação por seção** quando um upstream falha (ADR-004).

## Escopo
- **In:** `ComposePanelViewUseCase` (summary, overview, history, device); política de degradação; mapeamento
  upstream → view model.
- **Out:** entrega de notificação (SPEC-03), write-through (documentado aqui apenas quanto ao roteamento;
  contrato em api-contract §5).

## Decisões técnicas
- **Fan-out paralelo** com timeout por upstream (`UPSTREAM_TIMEOUT_MS`, default 2s).
- **Degradação por seção:** upstream soft (EDGE, leitura EVNT) degrada só sua fatia; upstream hard
  (ACS/PAC/HIST na própria tela) → `503`. Ver `integration-contracts.md` §0.
- DASH nunca retorna payload cru de upstream — sempre view model (`domain.PanelViewModel`).

## Implementação
- Cada porta de leitura (`IdentityDirectoryPort`, `CommandCatalogPort`, `EventStatePort`, `HistoryQueryPort`,
  `DeviceLivenessPort`) é chamada em paralelo; resultados montados em VO; falhas marcam `degraded`.
- Escopo do cuidador (ACS) resolvido **antes** de qualquer fan-out; nenhuma tela é montada sem escopo.

## Casos de borda
- EDGE stale além do limiar → `status=unknown`, `degraded=true` (nunca assume `offline`).
- EVNT indisponível no summary → seção de eventos omitida, `degradedSections` inclui `evnt`.
- PAC indisponível no overview → `503` (overview sem catálogo não tem sentido).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL resolve caregiver scope from ACS before composing any view.
- THE SYSTEM SHALL compose upstream reads with parallel fan-out and per-upstream timeouts.
- WHILE EDGE is unavailable or stale, THE SYSTEM SHALL report device `status=unknown` and `degraded=true`.
- WHEN EVNT read is unavailable, THE SYSTEM SHALL omit its section and flag `degradedSections`.
- IF PAC/HIST is unavailable for its own screen, THEN THE SYSTEM SHALL respond `503 upstream-unavailable`.
- THE SYSTEM SHALL return panel-shaped view models, never raw upstream payloads.
