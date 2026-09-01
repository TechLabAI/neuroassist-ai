<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Conteúdo bilíngue por locale com estrutura da árvore invariante

## Status
aceita

> Escopo: local (`conv-service`). Implementa **ADR-018 (accepted)** — "Internacionalização (EN/PT):
> autoria independente por idioma no CONV/TREI". Registra o efeito no modelo de dados e no runtime de
> leitura do CONV; a escolha de i18n da UI administrativa e a UX de fallback de locale ficam fora deste ADR.

## Contexto
O ADR-018 fechou que o sistema é bilíngue EN/PT e que, em CONV/TREI, o conteúdo de cada nó é **autorado e
aprovado independentemente por idioma** — a LLM do TREINAMENTO pode *sugerir* tradução, mas cada idioma
exige confirmação (dupla autorização, ADR-016). Rejeita-se tradução automática sem revisão em comunicação
assistiva de saúde, pelo mesmo racional do ADR-015 (uma tradução errada de frase crítica é tão arriscada
quanto uma frase alucinada). Isso muda o modelo do `NoArvoreFala`: texto/áudio deixam de ser campos únicos e
passam a ser conteúdo **por locale**. Um nó pode ficar "ativo em PT, pendente em EN".

## Decisão
No modelo do CONV, a **estrutura da árvore é invariante por locale**: `parentId`, `tipo`,
`engineFeatureRef` e `ordem` são únicos por nó; **só o conteúdo varia** — modelado como
`ConteudoLocalizado` por locale (`texto`, `audio_ref`, `confirmadoCuidadora`, `confirmadoPaciente`,
`status`). O rascunho de persistência favorece uma **tabela normalizada** `conv_no_conteudo` (unique em
`no_id` + `locale`) sobre uma coluna JSON, para ativar/consultar cada locale de forma independente — decisão
final em specs (ADR-018 deixa em aberto). No Elasticsearch, **cada locale de um nó é um documento próprio**
(embedding independente por idioma), e a busca em runtime roda contra o embedding do **locale ativo da
sessão**. CONV serve apenas conteúdo `ativo` do locale ativo. A escrita/aprovação por locale é do TREI; CONV
só lê.

## Consequências
**Positivas:** cada idioma evolui de forma independente e segura (sem tradução não supervisionada); o modelo
multilingual de embedding (ADR-015) cobre os dois idiomas sem troca de modelo; consistência arquitetural com
o resto da Emenda 4. **Negativas / trade-offs:** dobra o volume de autoria e de síntese (2× por frase) — no
TREI, não no CONV; introduz uma regra de **fallback de locale** ainda em aberto (o que servir quando o nó
está ativo em PT e pendente em EN e a sessão está em EN — mostrar PT com aviso? ocultar?) — TODO[arquiteto]
do ADR-018, a fechar em specs/FE. CONV **nunca** deve servir silenciosamente o texto do locale errado como
se fosse o ativo. Reduzir escopo para "só CONV/TREI bilíngue" é um corte, não uma mudança estrutural — o
modelo por locale já estaria pronto.
