# SPEC-12 — Degradation and Resilience

## Objetivo
Especificar a degradação como **contrato explícito** (não falha silenciosa): navegação estática sem
Elasticsearch (OPS-003) e Web Speech sem áudio cacheado (OPS-004), sempre com marcador `degraded`
(`ADR-002`/`ADR-004`). Atende ao princípio "degrade, never stop" (doc 01) e ao risco R4.

## Escopo
**In:** política de degradação, marcadores de resposta (`degraded`, `retrieval`, `playbackMode`,
`X-Degraded`), sinal `ConvServedDegraded`, matriz de dependências soft vs hard.
**Out:** UX visual do estado degradado (FE); retry de auditoria (SPEC-09).

## Decisões técnicas
- **ES é soft (fail-open):** indisponível/sem metade vetorial → navegação estática em PostgreSQL,
  `retrieval: static`, `degraded: true` (OPS-003). Nunca falha a navegação.
- **Áudio é soft (fail-open):** `audio_ref` nulo/ausente/inacessível → texto com ref nula,
  `playbackMode: web-speech`, `degraded: true` (OPS-004).
- **Identidade é hard (fail-closed):** token inválido/fora de escopo/blacklisted → rejeição imediata
  (SPEC-06). Revogação tem efeito imediato.
- **HIST é best-effort:** falha não bloqueia; outbox (SPEC-09).
- Degradação é **sempre explícita** (marcador + header + sinal), nunca um rótulo "semantic" falso.

## Implementação
- `DegradationPolicy` na `application` centraliza as transições: `semantico ⇄ estatico`,
  `audio-cacheado ⇄ web-speech`, detectadas por request (não persistidas).
- Respostas carregam `degraded` e o header `X-Degraded`; `ConvServedDegraded` emitido para ops (R4).
- Timeouts curtos nas chamadas soft (ES/áudio) para não atrasar o caminho crítico.

## Casos de borda
- ES up mas sem `model_id`/inferência vetorial → `degraded:true` (metade semântica indisponível).
- Áudio presente no índice mas arquivo ausente no filesystem → Web Speech.
- ES e áudio simultaneamente fora → navegação estática + Web Speech; comunicação segue.

## Critérios de aceite (EARS)
- IF Elasticsearch is unreachable or its vector half is unavailable, THEN THE SYSTEM SHALL serve navigation statically with `retrieval: static`, `degraded: true`, and header `X-Degraded: true`, and SHALL NOT fail the navigation path.
- IF the cached audio reference is absent or unreachable, THEN THE SYSTEM SHALL return text with `playbackMode: web-speech` and `degraded: true`.
- THE SYSTEM SHALL surface degradation explicitly on every degraded response and SHALL emit a `ConvServedDegraded` signal, never labeling a static result as semantic.
- WHILE identity verification fails, THE SYSTEM SHALL fail closed regardless of any degradation elsewhere.
