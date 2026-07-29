# SPEC-03 — Edge device authentication (critical sub-feature)

> Derived from `_brief.md` §6 and **ADR-005 (status: proposta)**. This is the critical
> domain decision for the gateway and the one non-frozen contract (gap G-1). EARS per
> standard.

## Objetivo
Especificar como o gateway autentica o **agente edge** (cliente headless no dispositivo do
paciente) de forma determinística, ligada a `empresa`, e desacoplada do esquema exato de
credencial — que depende do design de pareamento do EDGE.

## Escopo
- **In:** contrato do `DeviceCredentialFilter`, formato do principal `device`, resolução de
  `empresa_id`, comportamento de rejeição, ponto de extensão para o esquema de credencial.
- **Out:** o fluxo de emissão/rotação/revogação de token (pertence a EDGE/ACS); o valor
  final do esquema (ADR-005 permanece **proposta** até G-1 ser resolvido).

## Decisões técnicas
- **Proposta (ADR-005):** signed device token emitido no pareamento EDGE, validado pelo
  mesmo caminho JWK dos JWTs de cuidador; claims `device_id` + `empresa_id`.
- **Isolamento de esquema:** toda a lógica de credencial de dispositivo vive em um único
  filtro/porta `DeviceCredentialValidator`, para que mTLS (opção 2) ou API key (opção 3)
  possam substituir a implementação sem tocar em roteamento (mitiga G-1).

## Implementação
- `DeviceCredentialFilter` roda após `JwtAuthenticationFilter`: se a requisição não tem JWT
  de cuidador válido mas tem credencial de dispositivo, tenta validar como `device`.
- `DeviceCredentialValidator` (port) → impl default `SignedTokenDeviceValidator`
  (JWK-based). Emite `GatewayPrincipal(kind=device, subject=device_id, empresa_id, scope)`.
- Falha → 401 `/errors/unauthenticated`, `auth.failed{principalKind=device}`.

## Casos de borda
- Token de dispositivo válido em assinatura mas sem claim `empresa_id` → 401 (não é possível
  resolver tenant; fail closed, INV-1/INV-2).
- Dispositivo com token expirado → 401; o outbox do edge segura e re-tenta.
- Requisição com **ambos** JWT de cuidador e token de dispositivo → trata como cuidador
  (primeiro filtro que resolve principal vence; documentar).

## Critérios de aceite (EARS)
- WHEN an edge request presents a valid device token, THE SYSTEM SHALL authenticate it as a
  `device` principal and resolve `empresa_id` from the token claim.
- IF a device token is missing, expired, or has an invalid signature, THEN THE SYSTEM SHALL
  reject the request with `401` and forward nothing.
- IF a device token is validly signed but lacks a bound `empresa_id`, THEN THE SYSTEM SHALL
  reject with `401` (cannot resolve tenant — fail closed).
- WHERE the device-credential scheme (ADR-005) is not yet confirmed, THE SYSTEM SHALL keep
  validation behind a single swappable `DeviceCredentialValidator` port.
