# CONV-018 -- Criar GET /health (projeção de domínio: elasticsearch/audioStore/acs/degradedCapabilities) + emissão de ConvServedDegraded + métricas

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Observabilidade do CONV: endpoint `GET /api/v1/conv/health` reporta a alcançabilidade das
dependências soft e hard, emite o evento `ConvServedDegraded` quando qualquer request é servido
em modo degradado, e instrumenta métricas Micrometer para rastreamento operacional. `status: UP`
enquanto navegação estática funciona (ES pode estar down); `status: DEGRADED` apenas se ACS
indisponível (hard dependency). Não é uma substituição do Actuator — é a projeção de domínio
legível pelo GWY e pelo DASH.

## Spec de referencia

- SPEC-07 (observability-health — health endpoint, ConvServedDegraded, métricas)
- `contracts/api-contract.md` §2.5 (GET /health response shape)

## Escopo

- [ ] Criar `api/controller/ConvHealthController.java` (`GET /conv/health`, sem autenticação — GWY/ops acesso)
- [ ] Implementar `application/service/ConvHealthService.java`: `getHealth()` verifica ES via `SearchPort` (ping simples), audio store via `AudioRefPort` (verificação de `CONV_AUDIO_ROOT`), ACS via `AcsSessionPort` (liveness ping); retorna `ConvHealthResponse` com `degradedCapabilities[]`
- [ ] Emissão de `ConvServedDegraded` (via `HistAuditPort` best-effort): emitir em `SemanticSearchService` quando ES down; emitir em `NodeSelectionService` quando `audioRef.present=false`
- [ ] Métricas Micrometer: `Counter` `conv.retrieval.total` com tag `retrieval=semantic|static`; `Counter` `conv.playback.total` com tag `mode=audio-cacheado|web-speech`; `Timer` `conv.acs.blacklist.duration` (latência da consulta à blacklist)
- [ ] Registrar as métricas no `MeterRegistry` do Spring Boot Actuator (expostos em `/actuator/metrics`)
- [ ] Testes unitários: ES unreachable → `elasticsearch: UNREACHABLE`, `degradedCapabilities: ["semantic-search"]`, `status: UP`; ACS unreachable → `status: DEGRADED`

## Resultado esperado

`GET /health` reporta estado real das dependências; métricas instrumentadas no Actuator; `ConvServedDegraded` emitido ao HIST quando degradado.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: ES inacessível → resposta JSON com `"elasticsearch": "UNREACHABLE"`, `"status": "UP"`, `degradedCapabilities` inclui `"semantic-search"`
- [ ] Teste unitário: ACS inacessível → `"status": "DEGRADED"`, `"acs": "UNREACHABLE"`
- [ ] Counter `conv.retrieval.total{retrieval="semantic"}` incrementa a cada busca semântica bem-sucedida
- [ ] `ConvServedDegraded` emitido (para HistAuditPort mockado) quando ES responde com exceção

## Dependencias

- CONV-011 (AcsSessionAdapter)
- CONV-014 (SemanticSearchService — source de degradação ES)

## Estimativa

M

## Prioridade

P7
