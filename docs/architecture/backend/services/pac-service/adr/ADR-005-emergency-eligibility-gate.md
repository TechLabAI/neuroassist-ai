<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Emergency-Command Eligibility Gate (Synchronous, Fail-Closed)

## Status
aceita

## Contexto
Um `Comando` com `urgencia = emergencia` é o canal de socorro do paciente. A GEST domain framing
estabelece a invariante (Invariant 1): *"A `Comando` with `urgencia = emergencia` may reference only
`supported` primitives ... GEST is the enforcement point for this eligibility check."* O ponto de
**aplicação** dessa regra precisa ser decidido em PAC: nada impede, no ato de autoria, que a família
selecione um `TipoGesto` que ainda esteja `registered`/`pending-training` (nomeado, mas não realmente
detectável — ver a nota do `project-context.json`: *"Cadastrar um nome aqui NÃO implica suporte real do
motor"*). Publicar uma emergência mapeada a um gesto indetectável é um defeito de segurança silencioso.
Messaging está desabilitado (`stack.messaging.enabled = false`), então não há barramento para uma
verificação assíncrona — a checagem tem de ser síncrona ou inexistente.

## Decisão
PAC aplica um **eligibility gate no momento da publicação**: antes de um `Comando` com
`urgencia = emergencia` transicionar para `published`, PAC chama **GEST de forma síncrona** para
confirmar que todo primitivo referenciado (principal e, em gesto composto, o secundário) está
`supported`. O comportamento é **fail-closed**: em timeout, erro ou resultado desconhecido, a emergência
**não é publicada** — permanece `drafted`/`validated` e PAC retorna um erro RFC 7807 descritivo. Para
`urgencia = rotina` o gate é **advisory**: um primitivo não-`supported` gera aviso, não bloqueio (uma
rotina perdida é tolerável; uma emergência perdida não é).

## Consequências
**Positivas:** impossível publicar uma emergência mapeada a um gesto indetectável (segurança por
construção, no ato de autoria e não na hora da detecção); a assimetria emergência/rotina evita fricção
desnecessária em comandos de baixo risco; o erro é acionável para o operador durante o tuning.
**Negativas / trade-offs:** acopla a publicação de emergências à disponibilidade de GEST — GEST fora do
ar bloqueia publicar/recalibrar emergências (aceito: falha segura, mas precisa de erro claro e retry, não
hang silencioso); adiciona uma chamada síncrona no caminho de publicação; a corretude depende de o estado
`supported` em GEST estar preciso — um primitivo marcado `supported` sem feature real de motor
contornaria o gate (mitigado pela Invariant 5 de GEST: `engineFeatureRef` não-nulo para chegar a
`supported`).
