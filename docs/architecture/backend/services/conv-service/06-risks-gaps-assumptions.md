<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R1 — kNN não expressável pela porta atual.** `SearchPort.search(indexPattern, Map query)` do
  `elastic-stack-client-lib` passa uma Query DSL sob `query`; busca vetorial em ES usa um bloco `knn` de
  topo. Se o mapa não acomodar `knn`, a porta precisa de método kNN-aware. *Mitigação proposta (a decidir
  em specs):* confirmar experimentalmente e, se necessário, estender `SearchPort` de forma aditiva — não
  criar um cliente ES paralelo (violaria o princípio de reuso). Nenhum teste/gate cobre isto ainda.
- **R2 — Índice derivado escrito por outro módulo.** O `arvore-fala` é escrito pelo TREI e lido pelo CONV;
  divergência índice↔PostgreSQL (lag, falha de upsert) faz a busca "não encontrar" uma frase que existe.
  *Mitigação:* PostgreSQL é autoritativo e a navegação estática (OPS-003) não depende do índice; contrato
  de consistência CONV↔TREI a definir em specs.
- **R3 — Sessão não-expirável é superfície de risco.** O ator "paciente com sessão" (ADR-012) não expira;
  se o dispositivo for perdido, só a revogação por blacklist protege. *Mitigação:* CONV deve honrar a
  blacklist do ACS em toda chamada (fail-closed) — a verificação real do custo/latência da blacklist por
  request fica para specs; risco herdado do ADR-012, não resolvido pelo CONV.
- **R4 — Degradação silenciosa.** Cair para navegação estática (sem busca) ou Web Speech (sem a voz do
  paciente) sem sinalização confunde o cuidador sobre "por que a voz mudou". *Mitigação:* expor `degraded`
  explicitamente na resposta e na UI (OPS-003/OPS-004); regra de UX final é decisão FE.
- **R5 — Fidelidade da busca semântica em baixo volume.** Com poucas dezenas de nós, kNN pode não superar
  navegação manual, e embeddings ruins geram "quase-acertos" enganosos num contexto de saúde. *Mitigação:*
  tratar busca como assistência opcional sobre a árvore navegável, nunca como único caminho; qualidade dos
  embeddings é responsabilidade do TREI (ADR-015).
- **R6 — Plataforma restrita a Android/Chromium** (ADR-014) e always-on frágil no celular do paciente
  (bloqueio de tela, troca de app). Afeta principalmente o canal de gesto via GEST; CONV por toque/mouse é
  menos sensível, mas herda a restrição de plataforma do app do paciente.
- **R7 — LGPD/PHI no conteúdo.** Texto da árvore + áudio de voz clonada são dado sensível (SEC-002); o ToS
  do ElevenLabs proíbe PHI (ADR-017). CONV não sintetiza, mas serve o conteúdo — vocabulário deve
  permanecer cotidiano, não clínico (responsabilidade de curadoria no TREI, observada pelo CONV).

## Gaps Conhecidos
- **G1 — Assinatura kNN da porta ES** (ver R1): confirmar se `SearchPort` aceita `knn` ou precisa de
  extensão. **Bloqueia specs** da busca assistida.
- **G2 — Schema de conteúdo por locale**: JSON-column vs tabela normalizada `conv_no_conteudo` — ADR-018
  deixa aberto; este documento propõe a tabela normalizada, decisão final em specs.
- **G3 — Regra de fallback de locale**: o que servir quando um nó está `ativo` em PT e `pendente` em EN e o
  paciente está em EN (mostrar PT com aviso? ocultar?) — TODO[arquiteto] aberto no ADR-018 §UX.
- **G4 — Contrato do `engineFeatureRef`**: confirmar contra o código real se o vocabulário (commit
  `89f4ee5`) é consumível por um serviço externo a GEST/PAC via API, ou se precisa de exposição nova
  (TODO[arquiteto] do ADR-013).
- **G5 — Fronteira CONV↔TREI**: quem cria o `arvore_fala`/nós e o momento exato do upsert no ES e da escrita
  de áudio — o modelo de escrita é do TREI, mas o contrato de leitura/consistência do CONV depende dele.
- **G6 — Storage de áudio**: layout de diretórios, nomenclatura do `audio_ref` e presença/ausência como
  sinal de fallback — a formalizar em specs (ADR-017 fixou filesystem local + MP3/Opus).
- **G7 — Auth serviço-a-serviço** para `VocabularyPort` (CONV→PAC/GEST): reusar `client_credentials`
  (padrão do ADR edge-device-auth) — confirmar escopo específico em specs.

## Suposicoes a Validar
- **A1** — O `elastic-stack-client-lib` já materializado (`SearchPort`/`IndexPort` + `ElasticsearchAdapter`)
  é reutilizável pelo CONV sem fork; CONV o importa como dependência (a confirmar no scaffold).
- **A2** — A blacklist de JWT existente (`shared/rbac/session-policy.md`) suporta o ciclo "sem expiração,
  revogável a qualquer momento" sem adaptação (assunção herdada do ADR-012, ainda a confirmar).
- **A3** — O modelo de embedding multilingual (ADR-015) cobre PT e EN sem troca de modelo, e o TREI popula
  os dois locales — CONV só lê.
- **A4** — Volume permanece de piloto (1 paciente, dezenas de nós): single-node ES, sem ILM, sem broker,
  filesystem local para áudio são suficientes.
- **A5** — `Paciente` permanece owned pelo PAC; CONV referencia por `paciente_id` sem FK cross-service e
  sem exigir transação atômica PAC↔CONV (sinal de erro do ADR-013 se isso deixar de valer).
- **A6** — GWY roteia `/api/v1/conv/**` e REG descobre `conv-service` como qualquer outro serviço (padrão
  do ADR public API surface).

## Dependencias Criticas
- **ACS** — emissão/validação do JWT do "paciente com sessão" + blacklist de revogação (ADR-012). Sem isso
  não há identidade para operar o CONV. *Hard dependency (fail-closed).*
- **Elasticsearch + `elastic-stack-client-lib`** — motor de retrieval semântico (ADR-015). *Soft
  dependency (fail-open → navegação estática, OPS-003).*
- **TREI** — produtor do conteúdo indexado, dos embeddings e do áudio cacheado (ADR-016/017/018). CONV não
  tem o que servir sem o TREI ter autorado. *Dependência de dado, não de call-path em runtime.*
- **PAC/GEST** — vocabulário `engineFeatureRef` compartilhado para o binding gesto→nó (ADR-013). *Soft para
  navegação/busca; necessária só para o canal de gesto.*
- **Storage de áudio (filesystem)** — origem do playback com a voz do paciente (ADR-017). *Soft dependency
  (fail-open → Web Speech, OPS-004).*
- **HIST** — trilha de auditoria das seleções (ADR-012, auditoria). *Best-effort: não deve bloquear a
  comunicação do paciente.*
- **MIG** — provisionamento das tabelas CONV por schema-per-tenant (Flyway centralizado).
