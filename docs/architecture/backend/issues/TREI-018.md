# TREI-018 -- Health endpoint GET /health (all-dependency status)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

`GET /health` reporta o estado de todos os 6 subsistemas do TREI: Anthropic API,
ElevenLabs, embedding sidecar, Elasticsearch, audio store, ACS. Quando algum subsistema
de autoria está degradado → `degradedCapabilities` lista o que não pode operar. ACS
inacessível → `status: DEGRADED` (identity fail-closed). Os demais → `status: UP` com
lista de capabilities afetadas. O `embedModelId` é exposto explicitamente para que
incompatibilidade G5 com CONV seja visível operacionalmente (SPEC-11).
`G5BootSmoke` valida `TREI_EMBED_MODEL_ID` no startup.

## Spec de referencia

- SPEC-07 (observability-health — health endpoint, actuator, G5 surface)
- api-contract.md §2.9 (GET /health — response shape completo)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/api/HealthController.java` — `@GetMapping("/health")`; chama `TreiHealthIndicator` e retorna o JSON do api-contract §2.9
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/health/TreiHealthIndicator.java` — verifica cada subsistema em paralelo (ou sequencial com timeout): Anthropic (HEAD request ou health check), ElevenLabs (idem), embeddingSidecar (`TREI_EMBED_SIDECAR_URL/health`), ES (`GET /_cluster/health`), audioStore (verifica `TREI_AUDIO_ROOT` is writable), ACS (`AcsSessionPort` ping)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/health/G5BootSmoke.java` — `@EventListener(ApplicationReadyEvent)` que: verifica `TREI_EMBED_MODEL_ID` está setado; se sidecar reachable, compara `TREI_EMBED_MODEL_ID` com model_id reportado; loga WARNING se mismatch (não falha o boot — operacional, não hard gate em startup)
- [ ] campo `embedModelId` no response igual a `TREI_EMBED_MODEL_ID` (lido do env, não do sidecar — o sidecar pode estar down)
- [ ] `status: DEGRADED` apenas quando ACS inacessível; todos os outros → `status: UP` + lista `degradedCapabilities`
- [ ] criar `src/test/java/com/neuroassist/trei/api/HealthControllerTest.java` — mock dos indicators; verificar response shape correto; verificar `status: DEGRADED` quando ACS down

## Resultado esperado

`GET /health` retorna JSON com status de todos subsistemas + `embedModelId`;
sem autenticação obrigatória neste endpoint; responde mesmo quando subsistemas de
autoria estão degradados (TREI é off do runtime do paciente).

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*HealthControllerTest*"` sai 0
- [ ] Response JSON contém campo `embedModelId` igual ao valor de `TREI_EMBED_MODEL_ID`
- [ ] Quando ACS down, `status` é `DEGRADED` e `acs` é `UNREACHABLE`
- [ ] `GET /health` retorna 200 sem `Authorization` header (sem auth, SPEC-07)

## Dependencias

- TREI-004 (HealthController já parcialmente criado — merge com endpoint real)
- TREI-011 (G5ModelIdGuard config — `TREI_EMBED_MODEL_ID` env var)

## Estimativa

M

## Prioridade

P7
