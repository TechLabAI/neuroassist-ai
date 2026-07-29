# INT-GWY-002 -- Integration with ACS JWK/OIDC endpoint (cache TTL + stale-while-revalidate)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
Validacao de JWT de cuidador depende do JWK set do ACS via HTTPS GET; esta issue cobre o contrato de integracao e a tolerancia a indisponibilidade breve (cache TTL + stale-while-revalidate, R-4).

**Spec de referencia:** SPEC-10 (integracao #2)

## Escopo
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/java/com/neuroassist/gateway/security/JwkClient.java`

## Resultado esperado
Integracao com o endpoint JWK do ACS validada: cache, refresh em `kid` desconhecido, e rejeicao correta quando cache frio + endpoint indisponivel.

## Criterios de aceite
- [ ] endpoint JWK do ACS indisponivel com cache ainda valido nao impede autenticacao
- [ ] cache frio + endpoint indisponivel resulta em `401` (sem fallback inseguro)

## Dependencias
GWY-008 (precede).

## Estimativa
S

## Prioridade
P4
