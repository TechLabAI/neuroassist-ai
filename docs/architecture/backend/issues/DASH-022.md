# DASH-022 -- Implement degradation matrix: per-upstream circuit breaker (soft-degrade EDGE/EVNT-read; hard-503 ACS/PAC/HIST)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O DASH compoe dados de 5 upstreams. Cada um tem politica de degradacao diferente (ADR-004
+ integration-contracts.md §0): EDGE e EVNT-read sao soft-degrade (retornam secao omitida
ou degraded flag); ACS, PAC e HIST sao hard-dependency (retornam 503). Um circuit breaker
por upstream evita que um upstream lento cascateie timeouts para todos os requests.

## Spec de referencia

- SPEC-02 (degradedSections, fail-open EDGE e EVNT-read)
- SPEC-10 (degradation matrix: hard vs soft)

## Escopo

- [ ] Resilience4j circuit breaker por upstream (ou Feign + Resilience4j): configurar `UPSTREAM_TIMEOUT_MS` como timeout de read+connect; 1 retry em GETs idem potentes; write-throughs sem retry (ADR-006)
- [ ] `UpstreamUnavailableException` para hard-dependencies (ACS/PAC/HIST): mapeado para `503` pelo `@ControllerAdvice` com ProblemDetail `#upstream-unavailable`
- [ ] `UpstreamDegradedException` para soft-dependencies (EDGE/EVNT-read): capturado no `PanelCompositionService`, que preenche `degradedSections` e continua
- [ ] `UpstreamRejectedException` para 4xx do upstream: mapeado para relay do status e ProblemDetail original
- [ ] Teste de integracao: mock EDGE down → summary retorna com `degradedSections=["edge"]`; mock ACS down → `503`

## Resultado esperado

Falha de upstream soft-degradada ou hard-falhada de acordo com a politica arquitetural, sem cascata.

## Criterios de aceite

- [ ] EDGE indisponivel → `degradedSections=["edge"]` no summary (200, nao 503)
- [ ] EVNT indisponivel (read) → `degradedSections=["evnt"]` no summary (200)
- [ ] ACS indisponivel → `503` com ProblemDetail `#upstream-unavailable`
- [ ] PAC indisponivel no overview → `503`
- [ ] HIST indisponivel → `503`
- [ ] Write-throughs (calibracao, confirmacao, ack) nao fazem retry automatico

## Dependencias

- DASH-016 ao DASH-020 (adapters com excecoes tipadas)
- DASH-007 ao DASH-010 (PanelCompositionService: logica de captura e degrade)

## Estimativa

M

## Prioridade

P6
