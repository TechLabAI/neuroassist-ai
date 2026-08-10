# NeuroAssist AI — Plano Acadêmico: Dois Papers

*Danilo Amaral · agosto 2026*
*Para uso pelo Claude Code CLI: leia este documento antes de qualquer tarefa relacionada aos papers.*

---

## 0. Visão geral da estratégia de publicação

O trabalho gera **dois papers independentes** com contribuições distintas e venues diferentes.

| | Paper 1 — Clínico / Visão Computacional | Paper 2 — Arquitetura de Software |
|---|---|---|
| **Foco** | Sistema de CAA offline-first, pipeline CV, avaliação com paciente real | DetectionProfile, hexagonal + ArchUnit, plug-in extensível |
| **Pergunta científica** | É possível detectar gestos residuais com baixo FP, preservando privacidade, em hardware comum sem rede? | Como projetar uma arquitetura que evolua com a capacidade motora do paciente sem alterar o domínio? |
| **Venue EN primário** | HEALTHINF 2027 — 15 Set 2026 | SBES 2027 ou IEEE Software / JSS |
| **Venue PT-BR** | SBCAS 2027 — ~Mar 2027 | — |
| **Template EN** | `docs/paper/template/` (IEEEtran) | `docs/paper/template-arch/` (IEEE, a criar) |
| **Template PT-BR** | `docs/paper/template-ptbr/` (SBC) | — |
| **Limite de páginas** | HEALTHINF: 8–12 pp · SBCAS: 12 pp | SBES: 10 pp · IEEE Software: 8 pp |
| **Status** | Esqueleto EN completo · PT-BR compilando | Planejamento — scaffold após Paper 1 submetido |

---

## 1. Progresso atual — Paper 1 (2026-08-05)

### Seções EN (`template/`)

| Arquivo | Status | Observações |
|---|---|---|
| `sections/01_introduction.tex` | ✅ completa | 3 contribuições, loop MND→NIV→PVA, Atkeson 2011 stat |
| `sections/02_related_work.tex` | ✅ completa | 6 clusters (II-F VNI/PVA); **→ reduzir para 3 para HEALTHINF** |
| `sections/03_architecture.tex` | ✅ completa | 7 subseções, 2 figuras; **→ extrair hexagonal/ArchUnit para Paper 2** |
| `sections/04_evaluation.tex`   | 🟡 esqueleto | P0 NIV profile preenchido; aguarda dados experimentais |
| `sections/05_discussion.tex`   | ✅ completa | VNI integration pathway, limitações honestas |
| `sections/06_conclusion.tex`   | ✅ completa | placeholders [X]/[Y]/[A]/[B]/[N] para resultados |
| `main.tex`                     | ✅ compilando | IEEEtran, 10 pp · 0 erros · Overfull normais |
| `references.bib`               | ✅ 20 entradas | atkeson2011pva, yang2026pva, berlowitz2025tla; clarke2021aac DOI pendente |

### Seções PT-BR (`template-ptbr/`)

| Arquivo | Status | Observações |
|---|---|---|
| `sections/01_introducao.tex`            | ✅ completa | Tradução PT-BR |
| `sections/02_trabalhos_relacionados.tex` | ✅ completa | ELA / VNI / PVA em PT-BR |
| `sections/03_arquitetura.tex`           | ✅ completa | |
| `sections/04_avaliacao.tex`             | 🟡 esqueleto | mesmos placeholders do EN |
| `sections/05_discussao.tex`             | ✅ completa | menciona LGPD |
| `sections/06_conclusao.tex`             | ✅ completa | |
| `main-ptbr.tex`                         | ✅ compilando | SBC template, 17 pp · 1 erro cosmético babel (recuperável) |
| `sbc-template.sty` / `sbc.bst`         | ✅ copiados de `references/Template_SBC/template-latex/` | |

### Infraestrutura de medição

| Script | Status | O que faz |
|---|---|---|
| `scripts/paper/measure_latency.py` | ✅ pronto | loop de detecção com timestamps → CSV |
| `scripts/paper/false_positive_experiment.py` | ✅ pronto | SM vs. threshold simples → CSV |
| `scripts/paper/generate_tables.py` | ✅ pronto | CSV + alarm log → LaTeX |

### Instrumentação no PoC

| Mudança | Onde | Para quê |
|---|---|---|
| `createdAt` no `ReportIngressResponse` | `evnt-service` | Python captura timestamp server-side |
| `effect()` no `ShellComponent` | Angular | grava `{eventId, alarmShownAtMs}` no localStorage |

---

## 2. O que falta — Paper 1

### Antes dos experimentos (pode fazer agora)
- [ ] **Ajuste de tamanho PT-BR:** cortar de 17 → 12 páginas (ver Seção 6 abaixo)
- [ ] **Ajuste de tamanho EN:** confirmar se 10 pp cabe em HEALTHINF (limite 8–12 pp)
- [ ] **Verificar `clarke2021aac`** — DOI não confirmado; volume=35/number=1/pages=1-10; verificar antes da submissão
- [ ] **ALSFRS-R de Márcio** — se disponível, inserir em `04_evaluation.tex` no `\fillme{}`
- [ ] **Modelo/resolução da webcam** — preencher `\fillme{modelo/resolução}`
- [ ] **Remoção de autoria PT-BR** — antes de submeter ao SBCAS (blind review)

### Após os experimentos (preenche Section 4)
- [ ] Rodar sessões com P0–P5 (protocolo na Seção 8)
- [ ] Executar `generate_tables.py` → gerar `table_latency.tex` e `table_fp.tex`
- [ ] Preencher todos `[X]/[Y]/[A]/[B]/[N]/[Z]/[D]/[C]` nos dois templates
- [ ] Preencher `\fillme{horário das sessões}`
- [ ] Escrever narrativa final de Section 4 com dados reais
- [ ] Atualizar resumo/abstract com valores reais

### Submissão
- [ ] Preprint arXiv (cs.CV + cs.HC) antes do deadline formal
- [ ] Submeter HEALTHINF 2027 (EN) — 15 Set 2026
- [ ] Submeter SBCAS 2027 (PT-BR) — ~Mar 2027 (verificar deadline exato)
- [ ] Declarar uso de IA generativa no SBCAS (regra obrigatória)

---

## 3. Paper 1 — Clínico / Visão Computacional

### Título
**EN:** *NeuroAssist AI: A Privacy-Preserving, Offline-First Platform for Residual Facial Gesture-Based Assistive Communication in Motor Neurone Disease*

**PT-BR:** *NeuroAssist AI: Uma Plataforma Offline-First e Preservadora de Privacidade para Comunicação Assistiva Baseada em Gestos Faciais Residuais em Doenças Neurodegenerativas*

### Pergunta científica
> É possível construir um sistema de comunicação assistiva baseado exclusivamente em webcam comercial — operando completamente offline, preservando a privacidade e adaptando-se ao perfil motor residual individual do paciente — com taxa de falso-positivo clinicamente aceitável?

### Três contribuições

1. **Pipeline de detecção offline-first e privacy-by-design.** MediaPipe FaceLandmarker via WebAssembly servido localmente; vídeo nunca sai do dispositivo; único dado transmitido é payload JSON estruturado (sem coordenadas de landmarks).

2. **Reconhecimento de gesto deliberado via máquina de estados de 6 estados com calibração por paciente.** A FSM exige fechamento de duração mínima, reabertura confirmada e janela temporal — reduzindo FP em relação a threshold EAR simples; `DetectionProfile` permite ajuste de thresholds por paciente.

3. **Avaliação em contexto clínico real com gradiente ecológico de FP.** P0 é paciente MND em estágio avançado (ELA troncular, VNI desde 2026); P1–P3 são cuidadores em três níveis de proximidade (cônjuge/doméstico, profissional NDIS, controle neutro) — análise inédita de FP por contexto na literatura de CAA.

### Estrutura — Paper 1 (IMRaD)

```
I.   Introdução                    (~1 coluna)
     - gap clínico: MND → perda motora → VNI → PVA → comunicação
     - solução: NeuroAssist AI offline-first
     - 3 contribuições

II.  Trabalhos Relacionados         (~1.5 colunas) ← REDUZIR de 6 para 3 subseções
     II-A  CAA comercial e limitações (rastreio ocular, BCI)
     II-B  Visão computacional para CAA (EAR, MediaPipe, WASM)
     II-C  VNI/PVA em MND e assincronia paciente-cuidador

III. Sistema                        (~2 colunas) ← MANTER pipeline; RETIRAR detalhes ArchUnit
     III-A  Visão geral (figura)
     III-B  Pipeline de detecção (WASM, EAR, FSM)
     III-C  Máquina de estados de 6 estados (figura + tabela)
     III-D  DetectionProfile e calibração (mencionar engineFeatureRef → remete ao Paper 2)
     III-E  Arquitetura do sistema (menção hexagonal — sem tabela de regras ArchUnit)
     III-F  Fluxo ponta-a-ponta (gesto → evento → alarme)

IV.  Avaliação                      (~2.5 colunas)
     IV-A  Participantes e ética
     IV-B  Configuração experimental
     IV-C  Calibração do paciente
     IV-D  Latência
     IV-E  Taxa de falsos positivos
     IV-F  Taxa de detecção (recall)

V.   Discussão                      (~1 coluna)
     V-A  Acessibilidade de hardware / LMICs
     V-B  Privacidade desde a concepção (GDPR/LGPD)
     V-C  Limitações
     V-D  Trabalhos futuros (incluindo integração VNI)

VI.  Conclusão                      (~0.5 coluna)

Referências                         (~1 coluna)
```

### Ajuste de tamanho para 12 páginas (SBCAS PT-BR)

O template PT-BR atual tem 17 páginas. Cortes necessários (~5 páginas):

| Seção | Ação | Economia estimada |
|---|---|---|
| Related Work | Condensar 6 → 3 subseções | ~1.5 pp |
| Arquitetura | Remover tabela regras ArchUnit, reduzir detalhes microserviços stub | ~1.5 pp |
| Calibração | Transformar subseção em parágrafo dentro de IV | ~0.5 pp |
| Introdução | Compactar contexto MND/VNI | ~0.5 pp |
| Discussão | Condensar subseções V-A e V-B | ~0.5 pp |
| **Total** | | **~4.5–5 pp** |

**Fazer após os dados:** não reduzir agora para não perder contexto durante a escrita.

---

## 4. Paper 2 — Arquitetura de Software

### Título (candidatos)

**Opção A (recomendada):**
*DetectionProfile: A Plugin-Oriented Hexagonal Architecture for Adaptive Gesture Recognition in Assistive Communication Systems*

**Opção B:**
*Extensible Architecture for Adaptive Assistive Communication: Hexagonal Microservices with Compile-Time Boundary Enforcement*

### Pergunta científica
> Como projetar uma arquitetura de sistema assistivo que evolua continuamente conforme a capacidade motora do paciente muda, preservando o isolamento entre o domínio clínico e os mecanismos de reconhecimento de gesto, com invariantes arquiteturais verificáveis em tempo de compilação?

### Três contribuições

1. **DetectionProfile como abstração de separação entre capacidade motora e lógica de detecção.** Um registro imutável que encapsula thresholds calibrados, tipo de motor, e referência de feature (`engineFeatureRef`) — desacopla "o que o paciente consegue fazer" de "como o sistema detecta".

2. **Mecanismo plug-in `engineFeatureRef` para extensão sem modificação de domínio.** Adicionar um novo tipo de gesto (sobrancelha, boca, pose) requer apenas: (i) novo valor `TipoGesto`; (ii) novo módulo detector no agente de borda; (iii) novo `DetectionProfile`. Nenhuma regra ArchUnit, caso de uso ou entidade de domínio é modificado.

3. **Aplicação ArchUnit em tempo de compilação como documentação executável da arquitetura.** Seis regras hexagonais (domínio livre de Spring/JPA, `@Transactional` apenas em application, `@FeignClient` apenas em infrastructure) são verificadas no CI — a arquitetura não pode ser violada sem falha de build.

### Estrutura — Paper 2

```
I.   Introdução                         (~1 coluna)
     - problema: sistemas assistivos degradam conforme doença progride
     - solução: arquitetura que evolui junto com o paciente
     - 3 contribuições

II.  Problema Arquitetural              (~1 coluna)
     - requisitos de evolução para CAA em doenças neurodegenerativas
     - tensão entre estabilidade do domínio e variabilidade do gesto
     - limitações de abordagens existentes (acoplamento detecção-domínio)

III. DetectionProfile                   (~1.5 colunas) ← seção central
     III-A  Motivação: separação domínio / detecção
     III-B  Estrutura do registro (campos, invariantes)
     III-C  Ciclo de vida: calibração → publicação → uso
     III-D  Comparação com abordagens sem abstração equivalente

IV.  Mecanismo Plug-in engineFeatureRef (~1 coluna)
     IV-A  Design do mecanismo
     IV-B  Exemplo: adicionando sobrancelha como novo gesto
     IV-C  Análise de variabilidade: O que muda vs. o que permanece estável

V.   Arquitetura Hexagonal + ArchUnit   (~1.5 colunas)
     V-A   Estrutura de pacotes e camadas
     V-B   Seis regras ArchUnit (tabela completa)
     V-C   Verificação em tempo de compilação: setup e CI
     V-D   Portas e adaptadores: GEST e EVNT como exemplos

VI.  Avaliação / Discussão              (~1 coluna)
     VI-A  Estudo de caso: evolução de 1 para 2 tipos de gesto
     VI-B  Esforço de mudança: linhas de código adicionadas vs. modificadas
     VI-C  Limitações
     VI-D  Generalização para outros sistemas assistivos

VII. Trabalhos Relacionados             (~1 coluna)
     VII-A  Arquiteturas plug-in e extensíveis
     VII-B  Hexagonal / Ports & Adapters
     VII-C  Testes arquiteturais em software

VIII. Conclusão                         (~0.5 coluna)

Referências
```

### Conteúdo que migra do Paper 1 para o Paper 2

| Conteúdo atual (Paper 1) | Destino no Paper 2 |
|---|---|
| Tabela de regras ArchUnit (RULE-01 a RULE-09) | Seção V-B (tabela completa) |
| Detalhes dos microserviços stub (PAC, HIST, ACS) | Seção V-D como exemplos de ports |
| Subseção `engineFeatureRef` em III-F do Paper 1 | Seção IV completa do Paper 2 |
| Footnote EDF timestamp / integração VNI | Seção VI-D (generalização) |

### Status e próximos passos — Paper 2

- [ ] Scaffold do template EN em `docs/paper/template-arch/`
- [ ] Escrever Seções I–III (DetectionProfile) — pode fazer antes dos experimentos do Paper 1
- [ ] Escrever Seções IV–V (engineFeatureRef + ArchUnit) — extrair de `template/sections/03_architecture.tex`
- [ ] Definir venue alvo: SBES 2027 (verificar deadline ~Jul 2027) ou IEEE Software (rolling)

### Venues — Paper 2

| Venue | Tipo | Deadline | Páginas | Fit |
|---|---|---|---|---|
| **SBES 2027** (Simpósio Brasileiro de Eng. Software) | Full paper | ~Jul 2027 | 10 pp | ⭐⭐⭐ |
| **IEEE Software** | Journal | Rolling | 6–8 pp | ⭐⭐⭐ |
| **JSS** (Journal of Systems and Software) | Journal | Rolling | sem limite | ⭐⭐ |
| **ICSA 2027** (Int. Conf. on Software Architecture) | Full paper | ~Nov 2026 | 10 pp | ⭐⭐⭐ |
| **SAC 2027** (ACM Symposium on Applied Computing) | Full paper | ~Set 2026 | 8 pp | ⭐⭐ |

---

## 5. Separação de conteúdo entre os dois papers

### O que é exclusivo de cada paper

| Conteúdo | Paper 1 | Paper 2 |
|---|---|---|
| Contexto clínico ELA/MND/VNI/PVA | ✅ profundo | contextualização mínima |
| Pipeline WASM + MediaPipe + EAR | ✅ | menção |
| Máquina de estados FSM 6 estados | ✅ | menção |
| DetectionProfile — uso e calibração | ✅ uso | ✅ design profundo |
| engineFeatureRef — mecanismo plug-in | menção | ✅ profundo |
| Arquitetura hexagonal — visão geral | ✅ visão geral | ✅ profundo |
| Tabela regras ArchUnit | ❌ fora | ✅ central |
| Microserviços stub (PAC/HIST/ACS) | ❌ fora | ✅ como exemplos |
| Avaliação experimental (dados reais) | ✅ central | ❌ fora |
| Análise de FP por contexto ecológico | ✅ | ❌ |
| Privacidade / LGPD / GDPR | ✅ | ❌ |
| Acessibilidade hardware / LMICs | ✅ | ❌ |
| Análise de variabilidade arquitetural | ❌ | ✅ |
| Estudo de caso: adicionar novo gesto | ❌ | ✅ |

### O que é compartilhado (com profundidade diferente)

| Conteúdo compartilhado | Paper 1 | Paper 2 |
|---|---|---|
| Visão geral do NeuroAssist AI | 1 figura + parágrafo | 1 parágrafo |
| DetectionProfile (estrutura) | suficiente para entender calibração | profundo |
| Arquitetura hexagonal (visão macro) | suficiente para entender o sistema | profundo |
| Referências clínicas básicas (MND) | 5–8 refs | 2–3 refs |

---

## 6. Caso de estudo — Participantes

### Tabela de participantes

| ID | Nome | Papel | Relação com P0 | Sessão experimental |
|---|---|---|---|---|
| P0 | Márcio | Paciente ELA | — | Calibração + detecção intencional |
| P1 | Jamilly | Cônjuge / cuidadora doméstica (fisioterapeuta) | Íntima, diária; não elegível NDIS | FP cuidado + alarme real |
| P2 | Haron | Cuidador profissional 1 (NDIS) | Profissional, frequente | FP cuidado + alarme real |
| P3 | Giordano | Cuidador profissional 2 (NDIS) | Profissional, frequente | FP cuidado + alarme real |
| P4 | Karuzo | Controle (sem contato prévio) | Desconhecido | FP baseline naturístico |
| P5 | Thiago | Controle (sem contato prévio) | Desconhecido | FP baseline naturístico |

**Contexto:** Melbourne, Austrália (domicílio de P0). Sistema projetado também para o Brasil (hardware acessível, LGPD).

### Perfil clínico de P0 (Márcio)

- ELA de início troncular · onset: fim de 2019 · duração na avaliação: ~6,5 anos
- Fraqueza em quatro membros com movimento palpebral voluntário preservado
- VNI prescrita desde início de 2026 e ativa no momento do estudo
- FVC 71% / SNIP 31% (mai/2023) — progressão lenta confirmada
- Sem riluzole documentado
- `\fillme{ALSFRS-R score se disponível; avaliação atual da mobilidade palpebral}`

### Por que este grupo importa para o paper

- **P0** eleva o paper de "PoC com voluntários" para "estudo de caso em contexto clínico real"
- **P1** (cônjuge, fisioterapeuta) — cuidado doméstico íntimo, não elegível NDIS (conflito de interesse)
- **P2–P3** — cuidadores profissionais NDIS; permitem medir FP em cuidado profissional estruturado
- **P4–P5** — controles neutros, baseline de FP puro
- **Gradiente P1→P2-P3→P4-P5** — 3 contextos de proximidade — análise inédita de FP por contexto ecológico na literatura de CAA

---

## 7. Protocolo de experimento

### 7.1 Métricas

| Métrica | Como medir | Ferramenta |
|---|---|---|
| Latência gesto→HTTP | `time.time()` no agente Python | `measure_latency.py` |
| Latência evento→alarme visível | `Date.now()` no Angular (localStorage) | `ShellComponent effect()` |
| Taxa FP — máquina de estados | N min. atividade facial sem tentar disparar | `false_positive_experiment.py` |
| Taxa FP — threshold simples | mesmo dataset, algoritmo baseline | `false_positive_experiment.py` |
| Taxa de detecção P0 | N tentativas intencionais de piscar duplo | `measure_latency.py` (modo paciente) |
| Taxa de detecção P4/P5 | idem, comparação com saudáveis | `measure_latency.py` |

### 7.2 Sessão P0 (Márcio)

```
Duração: 20–30 min com pausas

1. Calibração (5–10 min)
   → Observar palpebral natural → ajustar EAR_CLOSE e BLINK_MIN_FRAMES
   → Publicar DetectionProfile personalizado

2. Detecção intencional (10–15 min, pausas de ≥30s entre tentativas)
   → N=20 tentativas de piscar duplo deliberado
   → Registrar: detectadas, falhas, latência gesto→alarme

3. Observação naturística (5 min, opcional)
   → Márcio em repouso/conversa, sem tentar disparar → medir FP calibrado
```

Registrar por sessão: hora do dia, iluminação, posição da câmera, thresholds finais.

### 7.3 Sessão P1–P3 (cuidadores)

```
Duração: ~15 min por participante
Contexto: interação natural com Márcio

1. FP durante interação (10 min)
   py -3.11 scripts/paper/false_positive_experiment.py \
     --duration 600 --participant <P1|P2|P3>

2. Alarme real (5 min)
   → Márcio dispara gesto propositalmente
   → Participante confirma na tela de alarme
   → Registrar event→alarme visível + tempo até confirmação
```

### 7.4 Sessão P4–P5 (controles)

```
Duração: ~10 min por participante
Contexto: neutro, sem Márcio presente

py -3.11 scripts/paper/false_positive_experiment.py \
  --duration 600 --participant <P4|P5>

→ Participante fala, lê, olha para os lados — sem instruções sobre gestos
```

### 7.5 Exportar alarm log após cada sessão

```js
// Browser F12 → Console:
JSON.stringify(JSON.parse(localStorage.getItem('_neuroassist_alarm_log')||'[]'), null, 2)
// Salvar em: scripts/paper/data/alarm_log_<participante>_<data>.json
```

### 7.6 Gerar tabelas

```bash
py -3.11 scripts/paper/generate_tables.py
# → scripts/paper/data/table_latency.tex
# → scripts/paper/data/table_fp.tex
```

---

## 8. Venues e calendário

### Paper 1 — Venues

#### HEALTHINF 2027 (EN) — alvo primário
- Venue onde Stell 2024 foi publicado; aceita system papers + case studies
- Conferências: Valletta, Malta (Fev 2027) · Nanjing, China (Mai 2027)
- Limite: 8–12 pp two-column

| Tipo | Deadline | Notificação | Camera Ready |
|---|---|---|---|
| **Regular Paper** | **15 Set 2026** | 13 Nov 2026 | 27 Nov 2026 |
| Position Paper | 22 Out 2026 | 4 Dez 2026 | 18 Dez 2026 |

**Hoje: 2026-08-05 → 41 dias para Regular · 78 dias para Position**

Cronograma Regular Paper (15 Set):
- Até 20 ago — experimentos com todos os 6 participantes
- 21 ago – 5 set — Section 4 + placeholders + ajuste de tamanho
- 6–12 set — revisão + preprint arXiv
- 15 set — submissão HEALTHINF

Se experimentos atrasarem: **Position Paper (22 Out)** é a alternativa.

#### SBCAS 2027 (PT-BR) — alvo secundário
- Simpósio Brasileiro de Computação Aplicada à Saúde
- Limite: **12 pp** (trabalho concluído) · 8 pp (em andamento)
- Deadline: ~março 2027 (verificar em sbcas.sbcas.org.br quando abrir)
- Submissão cega — remover autoria de `main-ptbr.tex` antes de submeter
- Declarar uso de IA generativa (regra SBCAS)

#### Alternativas Paper 1
| Venue | Deadline | Páginas | Fit |
|---|---|---|---|
| ICCHP 2026 | verificar `icchp.org` | var. | ⭐⭐⭐ (acessibilidade) |
| IEEE EMBC 2027 | Nov/Dez 2026 | 4 pp | ⭐⭐ (short paper) |
| JBHI (IEEE) | Rolling | var. | ⭐⭐ (journal longo prazo) |

### Paper 2 — Venues

| Venue | Deadline | Páginas | Fit |
|---|---|---|---|
| **SBES 2027** | ~Jul 2027 | 10 pp | ⭐⭐⭐ |
| **ICSA 2027** | ~Nov 2026 | 10 pp | ⭐⭐⭐ |
| **IEEE Software** | Rolling | 6–8 pp | ⭐⭐⭐ |
| JSS | Rolling | sem limite | ⭐⭐ |

---

## 9. Honestidade — o que declarar claramente

- **N=1 paciente MND** — não é ensaio clínico; não generaliza para a população MND. Declarar como "preliminary case study" no abstract e na Section 4.
- **Sem aprovação HREC** — estudo piloto com consentimento; aprovação será necessária para estudo multicêntrico futuro.
- **Módulos stub** — PAC, HIST, DASH, ACS representados por stubs; latência e FP refletem apenas GEST + EVNT.
- **Polling 5s** — contribui para latência event→alarm; a solução (SSE/WebSocket) está descrita em Discussion.
- **Modelo MediaPipe** — download inicial do Google Storage; Section 3 já cobre.
- **Márcio não está em estágio locked-in** — a calibração pode ser mais simples para ele do que para pacientes em estágio terminal; declarar claramente o perfil motor residual.

---

## 10. Conexão com a bolsa UniMelb

Este paper contribui para a candidatura de duas formas:

**Demonstra capacidade de pesquisa autônoma.** Um paper com case study real + preprint + submissão em conferência mostra ao comitê que o candidato sabe formular problema, conduzir avaliação e escrever para peer review.

**Conecta ao universo da pesquisa de destino.** MND é o contexto tanto do NeuroAssist AI quanto do grupo BEST (Berlowitz, Yang et al.). Um paper sobre CAA para MND com dados de paciente real, citando Berlowitz & Yang, cria narrativa coerente para a carta de motivação:

> *"In addition to my industry experience, I have published work on assistive technology for patients with MND, including a preliminary case study with an advanced-stage patient [citar o paper], which aligns directly with the BEST group's mission and demonstrates my ability to produce independent research contributions in the clinical technology space."*

---

## 11. Ordem de execução — próximas semanas

```
AGORA (agosto 2026):
  → Agendar sessões com todos os 6 participantes
  → Confirmar ALSFRS-R de Márcio se disponível
  → Confirmar modelo/resolução da webcam

SEMANA 1–2 (antes de 20 ago):
  → Sessão P0 (Márcio): calibração + measure_latency.py
  → Sessões P1–P3: false_positive_experiment.py + alarme real
  → Sessões P4–P5: false_positive_experiment.py

PÓS-DADOS (20 ago – 5 set):
  → generate_tables.py → copiar tabelas para 04_evaluation.tex / 04_avaliacao.tex
  → Escrever narrativa de Section 4 com dados reais
  → Preencher todos [X]/[Y]/[A]/[B]/[N] nos dois templates
  → Cortar Paper 1 PT-BR de 17 → 12 páginas (ver Seção 3 acima)

REVISÃO (6–12 set):
  → Verificar clarke2021aac DOI
  → Submeter preprint arXiv (cs.CV + cs.HC)
  → Remover autoria do PT-BR (blind review SBCAS)

SUBMISSÃO PAPER 1:
  → 15 set 2026 — HEALTHINF (EN)
  → ~mar 2027 — SBCAS (PT-BR) — após confirmar deadline

PÓS-SUBMISSÃO PAPER 1 (Set–Out 2026):
  → Scaffold template-arch/ para Paper 2
  → Escrever Seções I–III do Paper 2 (DetectionProfile)
  → Extrair conteúdo ArchUnit/hexagonal de template/sections/03_architecture.tex
  → Definir venue alvo: ICSA 2027 (deadline ~Nov 2026) ou SBES 2027
```

---

*Claude Code CLI: ao receber qualquer tarefa relacionada aos papers, leia este documento primeiro. Paper 1 é o alvo imediato (deadline 15 Set 2026 para HEALTHINF). Paper 2 inicia o scaffold após a submissão do Paper 1.*
