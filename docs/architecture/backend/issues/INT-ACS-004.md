# INT-ACS-004 -- JWK set propagation: garantir que todos os resource servers validam contra ACS

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

Todos os serviços (GEST, EVNT, PAC, EDGE, DASH, HIST) devem ter `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
apontando para `http://acs-service/oauth2/jwks` (resolvido via REG). Esta issue rastreia a
validação cross-service de que nenhum serviço usa `issuer-uri` direta ou public key hardcoded.

## Spec de referência

- SPEC-03 §6 (JWK set: Cache-Control max-age=300, publishes active + retired keys)
- SPEC-10 §3.1 (inbound integration: JWK set as shared dependency)

## Escopo

- [ ] verificar `application.yml` de cada serviço já scaffoldado: GEST, EVNT, PAC, EDGE
      (DASH, HIST quando scaffoldados) — confirmar `jwk-set-uri=http://acs-service/oauth2/jwks`
- [ ] se algum serviço usar `issuer-uri` ou chave pública hardcoded → corrigir
- [ ] verificar que `acs-service` já está registrado no Eureka com nome `acs-service` antes dos
      outros serviços subirem (startup order: REG → GWY → ACS → resto)
- [ ] documentar startup order em `docs/architecture/shared/distributed-architecture.md`

## Critérios de aceite

- [ ] `curl http://gest-service/actuator/health` retorna `UP` após ACS subir (validação JWT funcional)
- [ ] Token ACS aceito por todos os serviços scaffoldados
- [ ] Nenhum serviço usa chave pública hardcoded

## Dependências

- ACS-007 (JwkSetController)
- GEST/EVNT/PAC/EDGE M5 scaffold

## Estimativa

S

## Prioridade

P5
