<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-003 — Dupla autorizacao assincrona como gate de publicacao

## Status

aceita — materializa ADR-016 (decisões complementares de RBAC, BA/projeto, `accepted`).

## Contexto

O fluxo de TREINAMENTO é colaborativo: a cuidadora inicia/assiste e o paciente confirma/ajusta (ADR-016,
Opção 1). Um fluxo só-cuidadora perderia a voz do paciente; um fluxo só-paciente quebraria com a
progressão da doença. Ao mesmo tempo, exigir que os dois estejam na mesma tela ao mesmo tempo criaria um
handoff de UI frágil. Precisamos de um mecanismo que preserve a voz do paciente sem travar o processo.

## Decisão

Um `NoArvoreFala` proposto pela LLM só é publicado após **duas ações RBAC distintas e obrigatórias**:
`treinamento.no.aprovar.cuidadora` (via ACS, no shell da cuidadora/DASH) **e**
`treinamento.no.aprovar.paciente` (via sessão de paciente do ADR-012, no patient-shell). O nó carrega dois
booleanos independentes — `confirmadoCuidadora` e `confirmadoPaciente` — e só quando **ambos** são `true`
o pipeline de publicação (ADR-004) dispara. As duas confirmações são **assíncronas**: acontecem em
momentos diferentes, cada perfil no seu próprio shell, sob a sua própria identidade — não há handoff de
tela. A dupla autorização é um **invariante de domínio**, aplicado na camada `application`, não uma mera
justaposição de dois endpoints. TREI é resource server OAuth2 no realm compartilhado do ACS
(fail-closed), honrando a blacklist para revogação imediata da sessão do paciente.

## Consequências

- **Positivas:** preserva a palavra final do paciente sobre a própria árvore; robusto à progressão da
  doença (não trava se a capacidade do paciente varia); elimina o handoff de UI (cada um confirma no seu
  ritmo).
- **Negativas / trade-offs:** RBAC envolve duas identidades numa mesma transição de estado; nós ficam em
  `rascunho` indefinidamente se só um aprovar (sem expiração automática — ORG-001; descarte manual).
- **Reversão:** degradar para Opção 2 (só cuidadora) é um corte de escopo direto — basta ignorar o flag do
  paciente no gate; **sem mudança de schema**.
