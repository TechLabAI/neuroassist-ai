# SPEC-02 — Filter-pipeline orchestration (routing decision)

> The gateway's "core orchestration" is the ordered filter pipeline that turns an inbound
> request into a routing decision (forward | reject). EARS per standard.

## Objetivo
Especificar a pipeline de filtros que produz a **decisão de roteamento** — o "core" do
gateway, já que ele não tem agregado de negócio (03-domain-framing).

## Escopo
- **In:** ordem canônica dos filtros, contrato de cada estágio, produção do
  `GatewayPrincipal`, mapeamento para decisão forward/reject.
- **Out:** implementação de cada mecanismo de auth (SPEC-03/06), valores de rate-limit
  (SPEC-05).

## Decisões técnicas
- ADR-001 (hexagonal, adaptado): a pipeline é a camada de borda; ports de saída são
  `JwkClient`, discovery e `RateLimitStore`.
- Ordem **normativa** (não reordenável sem revisão):
  `CORS → CorrelationId → Authentication (JWT | Device) → TenantContextPropagation → RateLimiter → Route → ProblemDetailError`.

## Implementação
- Cada estágio é um `GlobalFilter`/`GatewayFilter` com `getOrder()` explícito.
- `Authentication` resolve exatamente um `GatewayPrincipal` (`kind`, `subject`,
  `empresa_id`, `scope`); em falha, curto-circuita a pipeline com erro tipado.
- `TenantContextPropagation` **remove** headers internos inbound e **define**
  `X-Empresa-Id`, `X-Principal-Kind`, `X-Principal-Id` a partir do principal.
- `ProblemDetailError` embrulha qualquer erro (curto-circuito ou upstream) em RFC 7807.
- Reactive/Netty: **nenhuma** chamada bloqueante dentro de filtro (R-5).

## Máquina de estados (por request)
```
RECEIVED -> CORS-checked -> correlation-ensured -> authenticated
  -> tenant-resolved -> rate-limit-checked -> FORWARDED
                                            \-> REJECTED (401|403|404|429|502|503|504)
```

## Casos de borda
- Falha de auth antes de resolver tenant → rejeição sem `X-Empresa-Id`.
- Erro em filtro após roteamento iniciado → resposta tipada, correlationId preservado.
- Principal ausente em rota `public` → segue sem `GatewayPrincipal`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL execute filters in the normative order above for every request.
- WHEN authentication succeeds, THE SYSTEM SHALL attach exactly one `GatewayPrincipal` to
  the exchange before tenant propagation runs.
- IF any filter short-circuits with an error, THEN THE SYSTEM SHALL produce an
  `application/problem+json` response carrying the effective `correlationId` and SHALL NOT
  forward the request.
- THE SYSTEM SHALL NOT perform a blocking call inside any filter.
