# GEST-020 -- Implement DetectionProfilePublished listener — deactivate prior active profile and invalidate profile cache

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Quando `DetectionProfilePublished` e emitido pos-commit, dois efeitos colaterais precisam ocorrer: (1) o perfil anterior deve ser desativado (`active=false`) — embora isso ja seja feito dentro da mesma transacao de `publish()`, o listener faz a invalidacao de cache in-memory para que `GET /detection-profiles/active` nao sirva o perfil antigo em caso de cache hit; (2) a entrada de cache do perfil ativo para aquele tenant e invalidada para forcaro proximo request a ler do BD.

## Spec de referencia
- SPEC-03, SPEC-09, event-contracts.md §2

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/listener/DetectionProfilePublishedListener.java` com `@TransactionalEventListener(phase=AFTER_COMMIT)` para `DetectionProfilePublished`
- [ ] invalidar entrada de cache do perfil ativo do tenant (Spring `@CacheEvict` ou operacao direta em `CacheManager`)
- [ ] loga evento com profileId, version, tenantRef, publishedAt (sem dados biometricos)
- [ ] testes: cache invalido apos publish (segundo request ao `GET /active` vai ao BD e retorna novo perfil)

## Resultado esperado
Apos `DetectionProfilePublished`, o cache do perfil ativo do tenant e invalidado; proximo `GET /detection-profiles/active` retorna a versao mais recente diretamente do BD.

## Criterios de aceite
- [ ] `GET /detection-profiles/active` imediatamente apos publish retorna o perfil publicado (nao o anterior em cache)
- [ ] listener e chamado apenas pos-commit (nao invocado se publish() lancou excecao e sofreu rollback)
- [ ] log de invalidacao de cache emitido com versao e tenantRef (sem dados biometricos)
- [ ] excecao no listener nao afeta a resposta do `POST /detection-profiles` que disparou o evento

## Dependencias
GEST-009, GEST-019 (padrao de listeners estabelecido).

## Estimativa
S

## Prioridade
P6
