# INT-CONV-001 -- GAP G4 — Confirmar contrato de VocabularyPort com PAC/GEST (formato real do engineFeatureRef, endpoint público, autenticação)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

Gap G4 da arquitetura CONV: o `engineFeatureRef` é tratado como **string opaca** porque o formato
real do vocabulário de gestos do PAC/GEST (commit `89f4ee5`) não foi confirmado como acessível via
API pública. Se PAC/GEST não expuserem endpoint de resolução, `VocabularyPort` sempre retorna
`bound: false` — o canal de gesto fica não-vinculado, mas navegação e toque continuam funcionando.
Esta issue não bloqueia o scaffold do CONV, mas deve ser resolvida antes de ir para produção com
o canal de gesto funcional.

## Spec de referencia

- SPEC-10 (integrations — VocabularyPort, gap G4 explícito)
- `contracts/hub-integration.md` §3
- `contracts/integration-contracts.md` §3 (boundary guards)

## Escopo

- [ ] Investigar o PAC/GEST backlog: o `pac-service` expõe endpoint `GET /pac/vocabulary/resolve?ref=<ref>&pacienteId=<id>` ou equivalente?
- [ ] Confirmar o formato real de `engineFeatureRef` (ex.: `gest:blink-double`, `89f4ee5:face_landmark_001`, etc.) com o time de GEST
- [ ] Atualizar `contracts/hub-integration.md` §3 com o contrato confirmado (endpoint, request shape, response shape) ou registrar "PAC/GEST não expõem resolução — CONV degrada a `bound: false` permanentemente no MVP"
- [ ] Atualizar `VocabularyFeignClient.java` (CONV-012) com o endpoint real confirmado (ou deixar como stub se gap não resolvido no MVP)

## Resultado esperado

Decisão documentada: VocabularyPort funcional com endpoint PAC/GEST real, ou gap documentado formalmente como "CONV degrada canal de gesto para `bound: false` no MVP".

## Criterios de aceite

- [ ] `contracts/hub-integration.md` §3 atualizado com "RESOLVIDO" ou "MVP: bound: false permanente"
- [ ] Se endpoint existe: `VocabularyFeignClient` com URL/path corretos e WireMock stub atualizado
- [ ] Se endpoint não existe: comentário em `VocabularyAdapter.java` com referência a INT-CONV-001 e decisão de degradação permanente

## Dependencias

- CONV-012 (VocabularyAdapter — afetado pela resolução deste gap)

## Estimativa

M

## Prioridade

P4
