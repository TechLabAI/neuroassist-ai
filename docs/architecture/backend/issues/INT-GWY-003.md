# INT-GWY-003 -- Integration bridge to EDGE/ACS device-auth authority (swappable, G-1)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O esquema de credencial de dispositivo (ADR-005, proposta) e a ponte com a autoridade EDGE/ACS; o gap G-1 exige que essa integracao fique isolada atras do port `DeviceCredentialValidator` para permitir troca de esquema (mTLS, API key) sem tocar em roteamento.

**Spec de referencia:** SPEC-03, SPEC-10 (integracao #3)

## Escopo
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/java/com/neuroassist/gateway/security/DeviceCredentialValidator.java`

## Resultado esperado
Integracao com a autoridade de auth de dispositivo (via JWK, proposta ADR-005) validada, com o port comprovadamente trocavel (implementacao alternativa nao quebra o contrato do filtro).

## Criterios de aceite
- [ ] troca da implementacao de `DeviceCredentialValidator` (ex.: stub alternativo) nao exige mudanca em `DeviceCredentialFilter` nem em roteamento
- [ ] token de dispositivo valido mas sem `empresa_id` e rejeitado com `401` (fail closed)

## Dependencias
GWY-010 (precede).

## Estimativa
S

## Prioridade
P4
