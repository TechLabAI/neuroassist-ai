# CONV-005 -- Criar ConvTreeService — navigateTree (GET /tree, PostgreSQL walk, locale resolution, pending_locale)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Golden path de navegação estática. `ConvTreeService.navigateTree(pacienteId, locale)` busca a
árvore do paciente no PostgreSQL (schema-per-tenant) e aplica a regra de locale: nós com conteúdo
`ativo` no locale ativo são marcados `selecionavel: true`; nós sem conteúdo `ativo` são retornados
com `contentStatus: pending_locale`, `texto: null`, `selecionavel: false` — **nunca** substituir
pelo outro locale (ADR-005/ADR-018, regra de segurança clínica). `retrieval: static` sempre neste
endpoint. Inclui o controller REST para `GET /api/v1/conv/tree` com guard de escopo
`conv.arvore.navegar`.

## Spec de referencia

- SPEC-02 (speech-tree-navigation-and-selection — fluxo de navegação e pending_locale)
- SPEC-11 (locale-bilingual-content — regra de fallback)
- `contracts/api-contract.md` §2.1

## Escopo

- [ ] Criar `application/service/ConvTreeService.java` com operação `navigateTree(UUID pacienteId, String locale): SpeechTreeResponse`
- [ ] Implementar walk PostgreSQL: carregar `conv_arvore_fala` por `paciente_id`; carregar todos `conv_no_arvore_fala` da árvore; para cada nó, buscar `conv_no_conteudo` pelo `(no_id, locale)` ativo; mapear para `NodeDto` com `contentStatus`/`selecionavel`/`texto` conforme regra de locale
- [ ] Regra de locale: IF `conv_no_conteudo` com `(no_id, locale, status='ativo')` existe → `contentStatus: ativo`, `texto: <texto>`, `selecionavel: tipo==FRASE`; ELSE → `contentStatus: pending_locale`, `texto: null`, `selecionavel: false`
- [ ] Criar `api/controller/ConvTreeController.java` (`@RestController`, `@PreAuthorize("hasScope('conv.arvore.navegar')")`, mapeia `GET /conv/tree` → delegação ao service)
- [ ] Parâmetro `rootOnly` (default false): se `true`, retornar apenas root + filhos diretos do root
- [ ] Parâmetro `locale` via `Accept-Language` override: validar contra `conv_arvore_fala.locales_suportados`; rejeitar com `400 unsupported-locale` se não suportado
- [ ] Testes unitários: locale ativo presente → selecionavel; locale ausente → pending_locale; rootOnly=true reduz lista de nós

## Resultado esperado

`GET /api/v1/conv/tree` retorna a árvore completa (ou root+filhos) com locale correto aplicado, sem substituição de locale, `retrieval: static`.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: nó com `conv_no_conteudo` ativo no locale `en` → `selecionavel: true`, `texto` preenchido, `contentStatus: ativo`
- [ ] Teste unitário: nó sem `conv_no_conteudo` no locale ativo → `selecionavel: false`, `texto: null`, `contentStatus: pending_locale`
- [ ] Teste unitário: locale não em `localesSuportados` → `400 unsupported-locale` ProblemDetail
- [ ] `retrieval` sempre é `static` na resposta de `/tree`
- [ ] Controller rejeita token sem scope `conv.arvore.navegar` com `403 forbidden-scope`

## Dependencias

- CONV-002 (entidades JPA)
- CONV-003 (DTOs)
- CONV-004 (ports)

## Estimativa

L

## Prioridade

P2
