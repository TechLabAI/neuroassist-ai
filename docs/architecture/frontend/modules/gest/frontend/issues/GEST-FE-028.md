# GEST-FE-028 -- Perfil de detecção do dispositivo — GET condicional com ETag/304

**Status:** RESOLVIDA — EDGE-022 entregue; endpoint migrado para GET /api/v1/edge/profile
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

> **BLOQUEIO (2026-08-25).** O store e o adapter estão implementados e testados, mas a estação não
> alcança o endpoint: token `edge.device` recebe **403** em
> `GET /gest-service/detection-profile/devices/{id}`, que exige `gest.detection-profile.read`
> (defeito **C-19**). O transporte passa a ser `GET /api/v1/edge/profile` — ver **EDGE-022**.
> O corpo da resposta não muda, então o trabalho já feito segue válido.

## Outcome
A estação sabe **o que** deve detectar. `DetectionProfileStore` busca
`GET /gest-service/detection-profile/devices/{deviceId}`, honra `If-None-Match`/`304`, e expõe os
primitivos ativos com `tipoGestoId`, `engineFeatureRef`, `sensibilidade` e `origem`.

## Context / Problem statement
- O perfil é a única fonte do par catálogo↔motor: devolve `tipoGestoId` (uuid) **e**
  `engineFeatureRef` (o que o detector emite). É exatamente a tradução que o commit `89f4ee5`
  estabeleceu no lado da calibração.
- `origem` distingue três coisas que hoje seriam indistinguíveis: faixa calibrada para o paciente
  (`PACIENTE`), padrão da empresa (`PERFIL_EMPRESA`) e catálogo ilegível (`INDISPONIVEL`).
  A terceira **precisa** virar estado degradado na tela — foi por confundir isso que HB-04 não passava.
- O endpoint devolve `Cache-Control: max-age=60` e suporta GET condicional: ignorar isso faz a
  estação refazer download completo do perfil indefinidamente.

## Approach (execution path)
1. `detection-profile.port.ts` + adapter HTTP com `If-None-Match`.
2. Guardar o ETag; `304` mantém o perfil em memória e não substitui nada.
3. Expor `primitivos()`, `profileVersion()` e `degradado()` como signals.
4. `INDISPONIVEL` em qualquer primitivo → sinalizar degradação parcial com o nome do gesto afetado.
5. Recarregar quando o heartbeat reportar `profileDrift` (GEST-FE-031).

## Non-goals
- Publicação de perfil (é do gest-service, permissão `gest.detection-profile.publicar`).

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §2.2, §4 (I5)

## Architecture alignment
- Signals; sem estado duplicado entre store e componente
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data access**
- [ ] `libs/gest/src/lib/estacao/detection-profile.port.ts`
- [ ] `libs/gest/src/lib/estacao/detection-profile-http.adapter.ts`
- [ ] `libs/gest/src/lib/estacao/detection-profile.store.ts`
- [ ] specs cobrindo 200, 304 e `origem: INDISPONIVEL`
- [ ] `libs/gest/src/index.ts` — export

## Acceptance
- Segunda busca com ETag inalterado devolve 304 e **não** troca o perfil em memória.
- `origem: INDISPONIVEL` nunca é tratado como faixa válida.
- Cada primitivo do perfil casa com um `EngineFeatureRef` conhecido; desconhecido é descartado com
  aviso, nunca entregue ao motor.
