<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Autoria bilingue independente por locale

## Status

aceita — materializa ADR-018 (BA/projeto, `accepted`).

## Contexto

O sistema é bilíngue EN/PT por inteiro (`_extensions.i18n`). No conteúdo crítico de saúde (CONV/TREI), a
segurança exige que não haja geração nem tradução não supervisionada: o LLM pode sugerir uma tradução, mas
alguém confirma cada idioma. ElevenLabs sintetiza uma voz por idioma; o Elasticsearch indexa cada idioma
de forma independente. Precisamos fixar a granularidade de autoria/aprovação/publicação por idioma.

## Decisão

Cada **locale** de um nó é autorado, aprovado e publicado **independentemente**. A estrutura da árvore (a
posição categoria/folha, o `no_id`) é **invariante** por idioma; o que varia é o conteúdo por locale
(`ConteudoLocale`: `texto`, `embeddingRef` + `embedding_model_id`, `audioRef`, `estadoPublicacao`). Uma
frase pode estar `ativa` em pt-BR e `rascunho`/pendente em en-US. A publicação de um locale (ADR-004)
nunca implica o outro; o embedding e o áudio são gerados por locale (ElevenLabs sintetiza 2x por frase,
uma por idioma). A LLM pode propor a tradução, mas ela entra como `rascunho` daquele locale e passa pela
mesma dupla autorização (ADR-003).

## Consequências

- **Positivas:** nenhuma tradução automática entra em produção sem revisão humana (mesmo racional de
  segurança do ADR-016/SEC-002); integridade de locale — o CONV nunca expõe silenciosamente um idioma não
  aprovado; estrutura de árvore compartilhada evita divergência de navegação entre idiomas.
- **Negativas / trade-offs:** dobra o esforço de autoria/aprovação e o custo de síntese (2x ElevenLabs por
  frase); abre a questão de granularidade dos flags de aprovação (por-nó vs por-locale) — gap G-A em
  doc 06, a resolver em specs; regra de fallback de locale (nó ativo em PT, sessão em EN) é
  responsabilidade de leitura do CONV (ADR-018 §fallback), fora do escopo de TREI.
- **Reversão:** não aplicável — o bilinguismo é requisito de produto; simplificar para monolíngue seria
  corte de escopo do projeto inteiro, não de TREI.
