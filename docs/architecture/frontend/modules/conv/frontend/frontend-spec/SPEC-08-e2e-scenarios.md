# SPEC-08 — E2E Scenarios — CONV (Frontend)

> Gherkin scenarios (no EARS section — SPEC-08 lists scenarios). Each maps to a `.feature`
> under `../e2e/`. Scenarios share domain nouns with the EARS `THE SYSTEM SHALL` rules in
> SPEC-01/03/04/05/07 so the `fe-architect-specs-validate` coverage matcher casa. Every
> scenario is real (no empty `describe`); a not-yet-buildable path uses an explicit
> `@fixme` tag rather than an empty body (doc 06 §1 empty-suite risk).

## Feature files

| `.feature` | Covers | EARS specs covered |
|---|---|---|
| `falar-frase.feature` | golden path Tela 06: navigate category → select frase → speak cached audio; channel parity (gesto ⊕ toque → one selectedNodeId) | SPEC-01, SPEC-03, SPEC-04, SPEC-05 |
| `fallback-voz.feature` | audio absent → Web Speech; Web Speech absent → prominent text; pending-locale node disabled, no wrong-locale speech | SPEC-03, SPEC-04, SPEC-07 |
| `assist-e-degradacao.feature` | semantic assist match chosen; ES down → silent manual navigation (degraded, no error) | SPEC-03, SPEC-04, SPEC-07 |
| `isolamento-patient-shell.feature` | zero caregiver-surface leakage; session revoked → shell exit + camera released | SPEC-01, SPEC-05, SPEC-06, SPEC-07 |
| `confirmar-proposta.feature` | Tela 09 confirm (optimistic) / adjust (variants, no free text) / reject; rollback on failure — **@fixme** pending BE proposals endpoint | SPEC-03, SPEC-04, SPEC-07 |

## Scenario inventory (summary)

### `falar-frase.feature`
- Navegar da categoria raiz até a frase e falar com áudio cacheado (toque).
- Paridade de canal: a mesma frase alcançada por gesto reconhecido produz o mesmo `selectedNodeId` e a mesma fala (gesto).
- Categoria ativada apenas desce um nível (não chama `/select`).

### `fallback-voz.feature`
- Áudio ausente cai para Web Speech (`playbackMode: web-speech`, degraded).
- Web Speech ausente cai para texto proeminente (voz nunca em silêncio).
- Nó pendente no idioma ativo aparece desabilitado/esmaecido e nunca fala o outro idioma.

### `assist-e-degradacao.feature`
- Busca assistida retorna a frase mais próxima e o paciente confirma o match.
- Elasticsearch indisponível: assist degrada em silêncio para navegação manual, sem erro.

### `isolamento-patient-shell.feature`
- Nenhuma rota do cuidador (histórico/calibração/onboarding/login) é alcançável a partir do patient shell.
- Sessão revogada encerra o patient shell e libera a câmera (zero camera leaks).

### `confirmar-proposta.feature` (@fixme — depende do endpoint de propostas do conv-service)
- Confirmar proposta remove-a otimisticamente e reconcilia no ack.
- Ajustar escolhe uma variante curta (sem texto livre).
- Falha na escrita re-insere o nó pendente e mostra mensagem não bloqueante.
