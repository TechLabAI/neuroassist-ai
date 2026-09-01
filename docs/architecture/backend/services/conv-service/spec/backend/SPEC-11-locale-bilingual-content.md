# SPEC-11 — Bilingual Content and Locale Fallback

## Objetivo
Especificar a resolução de conteúdo por locale e a **regra de fallback de locale** (resolve **G3**),
garantindo que o CONV nunca sirva silenciosamente o texto/áudio do locale errado — vetor de segurança em
comunicação assistiva de saúde (`ADR-005` → ADR-018).

## Escopo
**In:** resolução do locale ativo, comportamento quando o conteúdo do locale ativo está `pendente`/ausente,
contrato de resposta da API para esse caso.
**Out:** escolha do mecanismo i18n da UI administrativa (concern FE, ADR-018 §5); autoria por locale (TREI).

## Decisões técnicas
- **Locale ativo** = `Accept-Language` (se ∈ `localesSuportados`) senão claim `locale` do JWT.
- **Regra de fallback (DECISÃO de specs, fecha o TODO[arquiteto] do ADR-018/ADR-005):** o CONV **oculta a
  selecionabilidade** de um nó cujo conteúdo do locale ativo não é `ativo` e o **sinaliza explicitamente**
  (`contentStatus: pending_locale`, `texto: null`, `selecionavel: false`). **Nunca** substitui pelo texto/
  áudio do outro locale. Um paciente em sessão EN não pode "falar" uma frase PT que não lê.
- Estrutura da árvore é idêntica entre locales; só o conteúdo varia.

## Implementação
- `LocaleResolutionPolicy` na `application`: resolve o locale ativo, valida contra `localesSuportados`
  (`400 unsupported-locale` se fora), e para cada nó anexa o `ConteudoLocalizado` do locale ativo ou o
  marcador `pending_locale`.
- `GET /tree` retorna `localeRequested`/`localeServed` e o `contentStatus` por nó.
- `POST /select` de nó `pendente` no locale ativo → `409 content-pending-locale`.
- Busca (SPEC-03) filtra `status:ativo` no locale ativo — nós pendentes não aparecem nos matches.

## Casos de borda
- Nó ativo em PT, pendente em EN, sessão EN → nó visível como `pending_locale`, não selecionável, sem texto.
- Locale não suportado pela árvore → `400 unsupported-locale`.
- Nó sem nenhuma linha em `conv_no_conteudo` para o locale → tratado como `pending_locale`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL resolve the active locale as `Accept-Language` when supported, else the JWT `locale` claim.
- IF the requested locale is not in `localesSuportados`, THEN THE SYSTEM SHALL reject with `400 unsupported-locale`.
- WHILE a node's active-locale content is `pendente` or absent, THE SYSTEM SHALL return it with `contentStatus: pending_locale`, `texto: null`, `selecionavel: false`, and SHALL NOT substitute the other locale's text or audio.
- IF a `pending_locale` node is selected, THEN THE SYSTEM SHALL reject with `409 content-pending-locale`.
- THE SYSTEM SHALL serve node structure identically across locales, varying only localized content.
