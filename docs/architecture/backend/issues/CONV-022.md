# CONV-022 -- Criar ADR técnico / spike sobre cache de blacklist ACS (TTL curto vs. custo por request, risco R3, SPEC-06)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

A consulta à blacklist do ACS em **toda** requisição (ADR-003) garante revogação imediata, mas tem
custo de latência/throughput — risco R3 do `06-risks-gaps-assumptions.md`. O SPEC-06 menciona a
avaliação de "cache de blacklist com TTL curto vs. custo por request". Esta issue não implementa
o cache ainda; cria o ADR técnico (decisão arquitetural) com análise das opções e a decisão formal,
para que o scaffold não assuma nenhuma das duas estratégias implicitamente.

**Decisão a tomar (nesta issue):**
- **Opção A:** Cache local `Caffeine` com TTL de 5-10s (aceitar janela de ~10s de atraso na
  revogação; adequado para uso diário do paciente)
- **Opção B:** Sem cache — consulta ACS por request (latência de ~50ms por chamada; simples e
  safe; adequado para baixo volume MVP)
- **Opção C:** Cache com invalidação por evento (ACS publica evento de revogação → CONV invalida)
  [requer messaging.enabled=true — fora de escopo MVP]

## Spec de referencia

- SPEC-06 (security — ACS blacklist, risco R3)
- `contracts/hub-integration.md` §2 (ACS fail-closed)
- `docs/architecture/backend/services/conv-service/06-risks-gaps-assumptions.md` risco R3

## Escopo

- [ ] Analisar latência atual da consulta ACS em local dev (medição simples via Actuator timer `conv.acs.blacklist.duration` do CONV-018)
- [ ] Redigir ADR em `docs/architecture/backend/services/conv-service/adr/ADR-006-blacklist-cache-strategy.md` com 3 opções, prós/contras e decisão
- [ ] Decisão deve considerar: (a) unicidade clínica — revogação imediata é feature de segurança crítica; (b) volume MVP ≤ 5 pacientes simultâneos (custo absoluto de latência pequeno); (c) MVP → recomendar Opção B (sem cache) com plano de revisão em escala
- [ ] Atualizar `AcsSessionAdapter.java` (CONV-011) com `TODO[ADR-006]` para facilitar futura adição de cache sem alterar interface

## Resultado esperado

ADR-006 redigido e revisado; decisão MVP documentada; `AcsSessionAdapter` com ponto de extensão anotado.

## Criterios de aceite

- [ ] `docs/architecture/backend/services/conv-service/adr/ADR-006-blacklist-cache-strategy.md` existe com seções: Context, Decision, Options, Consequences
- [ ] ADR registra que MVP usa Opção B (ou a decisão tomada) com justificativa de volume
- [ ] ADR referencia risco R3 do `06-risks-gaps-assumptions.md` e SPEC-06
- [ ] `AcsSessionAdapter` tem comentário `// TODO[ADR-006]: ponto de extensão para cache Caffeine TTL` no método `validateAndCheckBlacklist`

## Dependencias

- CONV-015 (SecurityConfig — contexto do Resource Server estabelecido)

## Estimativa

S

## Prioridade

P9
