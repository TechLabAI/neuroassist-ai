# INT-GEST-007 -- Bridge transport auth joint decision with EDGE team (mTLS vs OAuth2 client_credentials — open item SPEC-06 §3.2)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O formato de credencial de dispositivo usado quando o edge `GestureDetectionEngine` chama GEST diretamente (profile pull e report validation) ainda nao esta congelado. A decisao entre mTLS (certificado de cliente por dispositivo) e OAuth2 `client_credentials` (um par de credenciais por dispositivo registrado) impacta o esquema de autenticacao do `SecurityConfig` (GEST-017) e o contrato do pareamento em EDGE. O payload de `RecognizedCommandReport` e `EdgeRecognitionSignal` ja esta congelado (v1.0.0) — apenas o transporte de auth e aberto.

## Spec de referencia
- SPEC-06 §3.2, integration-contracts.md §1.3, api-contract.md §6.3, event-contracts.md §3.3

## Escopo
- [ ] conduzir sessao de decisao com time EDGE para escolher mTLS vs OAuth2 client_credentials por dispositivo
- [ ] registrar decisao em ADR (ADR-007 ou similar) em `docs/architecture/backend/services/gest-service/adr/`
- [ ] atualizar `SecurityConfig` (GEST-017) com o esquema de autenticacao escolhido para device credential
- [ ] atualizar `integration-contracts.md §1.3` para fechar o item aberto

## Resultado esperado
ADR documentado com decisao de autenticacao do dispositivo; SecurityConfig atualizado; item aberto em integration-contracts §1.3 fechado.

## Criterios de aceite
- [ ] ADR-00X documenta a decisao (mTLS ou client_credentials) com rationale e impactos
- [ ] `SecurityConfig` implementa o esquema escolhido para device credential (claim `deviceId`)
- [ ] `integration-contracts.md §1.3` marcado como FECHADO com referencia ao ADR
- [ ] testes de integracao com credencial de dispositivo real (ou stub) passam

## Dependencias
GEST-017, INT-GEST-001 (device pairing client precisa do formato de auth definido).

## Estimativa
M

## Prioridade
P4
