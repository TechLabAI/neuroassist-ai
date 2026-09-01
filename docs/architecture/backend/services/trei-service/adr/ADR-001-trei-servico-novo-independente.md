<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-001 — TREI como servico novo independente (autoria fora do runtime CONV)

## Status

aceita — materializa a decisão de produto ADR-016 (BA/projeto, `accepted`).

## Contexto

O runtime do CONV é retrieval puro (ADR-015): ele nunca gera texto nem chama um LLM. Toda a
responsabilidade de **criar e expandir** a árvore de falas precisa viver num momento separado — a fase de
TREINAMENTO (ADR-016). Essa fase não é extensão de `conv-service` (que é só navegação/apresentação, ver
ADR-013), não cabe em `dash-service` (painel de consulta, não de autoria assistida por IA) e não é PAC
(catálogo de paciente/comando). Precisamos decidir onde essa fase de autoria vive na topologia de
serviços.

## Decisão

Vamos criar `trei-service` como **microserviço novo e independente** (alinhado ao
`ADR-002-independent-microservices` compartilhado, organização interna hexagonal pelo
`ADR-001-hexagonal-architecture` compartilhado), dedicado à autoria da árvore de falas. TREI é o **único
plano de escrita** dos artefatos que CONV lê em runtime: o documento do índice `arvore-fala` (texto +
embedding + `audio_ref`) e o arquivo de áudio cacheado. CONV nunca escreve esses artefatos; nenhum outro
serviço os escreve. Assim TREI passa a ser o dono do contrato de consistência CONV↔TREI (o item aberto
que o CONV deixou registrado no seu ADR README).

## Consequências

- **Positivas:** separação limpa entre geração (TREI) e uso (CONV) — o runtime do paciente nunca depende
  de LLM/ElevenLabs; deploy e escala independentes; um único dono do write-side do índice/áudio elimina
  ambiguidade de escrita concorrente.
- **Negativas / trade-offs:** mais um deployable para operar (OPS-002, servidor único); o contrato do
  documento `arvore-fala` precisa ser co-owned com CONV e mantido coerente (G5 model-id) — acoplamento de
  dados, ainda que sem chamada direta em runtime.
- **Reversão:** dobrar TREI dentro de outro serviço seria possível, mas reabriria o risco de acoplar
  geração ao runtime — não recomendado.
