# NeuroAssist AI — Paper Acadêmico
## Plano completo: contribuição, estrutura, experimentos e venues

*Danilo Amaral · agosto 2026*
*Para uso pelo Claude Code CLI: leia este documento antes de qualquer tarefa relacionada ao paper.*

---

## 0. Progresso atual (atualizado em 2026-08-05)

### Seções escritas

| Arquivo | Status | Observações |
|---|---|---|
| `template/sections/01_introduction.tex` | ✅ completa | 3 contribuições, fatos clínicos, estrutura do paper |
| `template/sections/02_related_work.tex` | ✅ completa | 5 clusters, 12 referências, gap claims honestos |
| `template/sections/03_architecture.tex` | ✅ completa | extraída do código-fonte, 7 subseções, 2 tabelas |
| `template/sections/04_evaluation.tex`   | 🟡 esqueleto completo | estrutura + placeholders; preencher após experimentos |
| `template/sections/05_discussion.tex`   | ✅ completa | limitações honestas, trabalho futuro |
| `template/sections/06_conclusion.tex`   | ✅ completa | placeholders [X]/[Y]/[A]/[B]/[N] para resultados |
| `template/main.tex`                     | ✅ esqueleto | IEEEtran, \input de todas as seções |
| `template/references.bib`              | ✅ 17 entradas | clínica, AAC, CV, BCI, WASM, arq. software |

### Infraestrutura de medição criada

| Script | Status | O que faz |
|---|---|---|
| `scripts/paper/measure_latency.py` | ✅ pronto | loop de detecção com timestamps → CSV |
| `scripts/paper/false_positive_experiment.py` | ✅ pronto | SM vs. threshold simples → CSV |
| `scripts/paper/generate_tables.py` | ✅ pronto | CSV + alarm log → LaTeX e Markdown |

### Instrumentação feita no PoC

| Mudança | Onde | Para quê |
|---|---|---|
| `createdAt` no `ReportIngressResponse` | `evnt-service` | Python captura timestamp server-side |
| `effect()` no `ShellComponent` | Angular | grava `{eventId, alarmShownAtMs}` no localStorage |

### O que falta

1. **Rodar os experimentos** (Seções 4.2 e 4.3 abaixo) — dados reais para preencher [X]/[Y]/[A]/[B]/[N]
2. **Escrever Section 4** (Evaluation) — após os dados
3. **Related Work bibliográfico** — confirmar Clarke 2021 DOI; verificar `mehta2018mnd` volume/páginas
4. **Diagramas** — exportar `fig_overview.mmd` e `fig_statemachine.mmd` como PDF para LaTeX
5. **Verificar deadline BIOSTEC/HEALTHINF 2027** — site `biostec.scitevents.org`
6. **Preprint arXiv** — submeter antes do deadline formal

---

## 1. Visão geral e posicionamento

### O que existe hoje (base do paper)

Sistema funcional validado end-to-end com:
- **Detecção de gesto offline-first** via MediaPipe Face Mesh (WASM servido localmente, sem CDN, sem rede para o pipeline de CV)
- **Máquina de estados para gesto deliberado** (anti-falso-positivo): `WAITING_BLINK1 → IN_BLINK1 → WAITING_OPEN1 → WAITING_BLINK2 → IN_BLINK2 → DONE`
- **Calibração por paciente** via DetectionProfile (thresholds EAR configuráveis, `engineFeatureRef` plugável)
- **Arquitetura hexagonal** em microserviços Java 21 + Spring Boot 3.5.0, ArchUnit-enforced
- **Fluxo completo validado via curl e browser**: gesto → report → evento → alarme → confirmação da cuidadora

### Ângulo do paper

> **"NeuroAssist AI: Privacy-Preserving, Offline-First Assistive Communication Using Calibratable Residual Facial Gestures and Hexagonal Microservices"**

É um **system paper** com **caso de estudo real (paciente MND)**. A contribuição combina arquitetura e sistema com validação preliminar em contexto clínico real — um nível de evidência raro em papers de AAC de engenharia.

### Três contribuições originais (para o abstract e introdução)

1. **Pipeline de detecção offline-first e privacy-preserving.** O WASM do MediaPipe é servido localmente; o vídeo nunca sai do dispositivo. CPU delegate garante compatibilidade universal sem GPU.

2. **Reconhecimento de gesto deliberado via máquina de estados com thresholds calibráveis por paciente.** Em vez de simples threshold de EAR, a máquina de 6 estados exige fechamento mínimo de 3 frames por piscada, reabertura completa entre piscadas, e janela configurável — reduzindo falsos-positivos de piscadas involuntárias. Thresholds são configuráveis por paciente via DetectionProfile.

3. **Arquitetura hexagonal extensível com plug-in de tipos de gesto e catálogo de comandos.** O `engineFeatureRef` no DetectionProfile desacopla a camada de detecção do catálogo de comandos — novos gestos (sobrancelha, sorriso) são adicionados sem alterar o domínio. ArchUnit enforcement garante que a separação persiste.

---

## 2. Estrutura IMRaD

### Title (working)
*NeuroAssist AI: A Privacy-Preserving, Offline-First Platform for Residual Facial Gesture-Based Assistive Communication in Neurodegenerative Disease*

### Abstract (a preencher após experimentos)
```
[CONTEXTO] Patients with motor neurone disease (MND/ALS) progressively lose voluntary 
motor control, often retaining only subtle facial movements as a communication channel. 
Existing AAC systems require reliable network connectivity and do not adapt to the 
residual motor profile of individual patients.

[PROPOSTA] We present NeuroAssist AI, a privacy-preserving, offline-first assistive 
communication platform that transforms residual facial gestures into structured emergency 
requests using a locally-executed WebAssembly-based computer vision pipeline.

[MÉTODO] The system combines a six-state deliberate gesture recognition state machine 
with patient-specific EAR threshold calibration, deployed over a hexagonal microservices 
architecture. We evaluate the system with one advanced-stage MND patient and five 
caregivers/volunteers in a preliminary case study.

[RESULTADOS] End-to-end latency from gesture completion to caregiver alarm display 
averaged [X] ms (σ=[Y]) across [N] trials. The deliberate gesture state machine reduced 
false-positive rate from [A]% (simple EAR threshold) to [B]% across participants.

[CONCLUSÃO] NeuroAssist AI demonstrates that high-accuracy, privacy-preserving 
residual gesture detection is achievable on commodity hardware without network 
dependency, using a calibratable, extensible architecture validated in a real MND 
home-care context.
```

---

## 3. Caso de Estudo — Márcio Amaral e Participantes

### Perfil dos participantes

| ID | Nome | Papel | Relação com Márcio | Sessão experimental |
|---|---|---|---|---|
| P0 | Márcio Amaral | **Paciente MND** | — | Calibração + detecção intencional |
| P1 | Jamilly | Esposa / cuidadora principal | Íntima, diária | Alarme real + FP durante cuidado |
| P2 | Haron | Cuidador 1 | Familiar, frequente | FP durante interação, confirmação |
| P3 | Giordano | Cuidador 2 | Familiar, frequente | FP durante interação, confirmação |
| P4 | Karuzo | Controle (sem contato) | Desconhecido | FP puro — baseline naturístico |
| P5 | Thiago | Controle (sem contato) | Desconhecido | FP puro — baseline naturístico |

### Por que este grupo é significativo para o paper

- **P0 (Márcio)** é um **paciente real com MND** — eleva o paper de "PoC com voluntários saudáveis" para "estudo de caso preliminar em contexto clínico real". Isso é raro em papers de engenharia de AAC e aumenta substancialmente a relevância clínica.
- **P1–P3** (familiares/cuidadores) permitem medir FP em condições realistas de uso: conversação com Márcio, atividades de cuidado, expressões emocionais naturais. Estas condições são mais exigentes do que o protocolo de voluntário neutro.
- **P4–P5** (controles sem contato) fornecem a linha de base de FP puramente naturístico, comparável à literatura.
- A **separação P1-P3 vs. P4-P5** permite analisar se a proximidade emocional e contextual com o paciente afeta a taxa de FP dos cuidadores — uma pergunta nova na literatura de AAC.

### Histórico clínico de Márcio (a integrar no paper)

Márcio Amaral tem histórico documentado de MND disponível para o estudo.  
**Claude Code CLI:** ao escrever a Section 4 (Evaluation), solicitar ao usuário os dados clínicos relevantes:
- Estágio da doença (ALSFRS-R score se disponível)
- Capacidade motora residual atual (quais movimentos voluntários estão preservados)
- Uso de NIV (ventilação não-invasiva)
- Capacidade ocular e palpebral atual
- Qualquer limitação específica para o protocolo de experimento

Estes dados permitem contextualizar os thresholds de calibração e a taxa de detecção de Márcio em relação às capacidades motoras preservadas.

---

## 4. Protocolo de Experimento Atualizado (Section 4)

### 4.1 Métricas a medir

| Métrica | Como medir | Ferramenta |
|---|---|---|
| Latência gesture→HTTP | `time.time()` no Python edge agent | `measure_latency.py` |
| Latência event→alarme visível | `Date.now()` no Angular (localStorage) | `ShellComponent` effect |
| Taxa de falso-positivo (SM) | N min. de atividade facial naturística | `false_positive_experiment.py` |
| Taxa de falso-positivo (threshold simples) | Mesmo dataset, algoritmo baseline | `false_positive_experiment.py` |
| Taxa de detecção (recall) de Márcio | N tentativas intencionais de piscar duplo | `measure_latency.py` (modo paciente) |
| Taxa de detecção de voluntários saudáveis | Idem para P4/P5 | `measure_latency.py` |
| Tempo de calibração por participante | Registro manual durante sessão | Planilha |

### 4.2 Sessão de Márcio (P0) — Paciente MND

Adaptar para as capacidades motoras atuais de Márcio. Protocolo base:

```
Duração estimada: 20-30 min (com pausas)
Hardware: webcam comum, laptop, tela visível para Márcio

1. Calibração (5-10 min):
   → Observar movimento palpebral natural de Márcio
   → Ajustar EAR_CLOSE e BLINK_MIN_FRAMES ao seu perfil
   → Publicar DetectionProfile personalizado

2. Detecção intencional (10-15 min, com pausas):
   → N=20 tentativas de piscar duplo deliberado
   → Registrar: quantas dispararam, quantas falharam, motivo
   → Registrar latência gesture→alarme para cada trial

3. Observação naturística (5 min, opcional):
   → Márcio em repouso/conversa, sem tentar disparar
   → Contar FP com seus thresholds calibrados
```

**Dados adicionais a registrar por sessão:**
- Hora do dia (fadiga ocular é maior à tarde em MND)
- Iluminação do ambiente (lux, se possível)
- Posição da câmera em relação ao rosto
- Thresholds finais do DetectionProfile publicado

### 4.3 Sessão de Cuidadores Familiares (P1 Jamilly, P2 Haron, P3 Giordano)

```
Duração: ~15 min por participante
Contexto: interação natural com Márcio (conversa, cuidado)

1. FP durante interação (10 min):
   py -3.11 scripts/paper/false_positive_experiment.py \
     --duration 600 --participant <P1|P2|P3>
   → Participante interage normalmente com Márcio
   → Registrar FP SM e FP simples

2. Alarme real (5 min):
   → Márcio dispara o gesto propositalmente
   → Participante confirma na tela de alarme
   → Registrar event→alarme visível e tempo até confirmação
```

### 4.4 Sessão de Controles (P4 Karuzo, P5 Thiago)

```
Duração: ~10 min por participante
Contexto: neutro, sem Márcio presente

py -3.11 scripts/paper/false_positive_experiment.py \
  --duration 600 --participant <P4|P5>

→ Participante fala, lê, olha para os lados normalmente
→ Sem instruções sobre os gestos
→ Baseline de FP naturístico puro
```

### 4.5 Exportar alarm log após cada sessão

Após cada sessão onde o Angular estava aberto:
```
# No browser, F12 → Console:
JSON.stringify(JSON.parse(localStorage.getItem('_neuroassist_alarm_log')||'[]'),null,2)
# Salvar em: scripts/paper/data/alarm_log_<participante>_<data>.json
```

### 4.6 Gerar tabelas finais

```bash
py -3.11 scripts/paper/generate_tables.py
# → scripts/paper/data/table_latency.tex
# → scripts/paper/data/table_fp.tex
```

### 4.7 Dados do PoC já existentes

- 9 critérios de sucesso documentados no `poc-run.md` — todos validados (✅)
- Sessões 1–4 do `PoC-update.md` documentam problemas reais e soluções reais

---

## 5. Estrutura IMRaD — Seção 4 (Evaluation) a escrever

Após os dados, escrever `sections/04_evaluation.tex` cobrindo:

**5.1 Participants and Ethics**
- Declarar que P0 é paciente MND com consentimento informado
- Declarar que P1–P5 são adultos voluntários com consentimento
- Notar que não há aprovação HREC formal (escopo de PoC/estudo piloto)

**5.2 Experimental Setup**
- Hardware: webcam comum (especificar modelo/resolução usada)
- Software: poc-gesture-detector.ts, poc-edge-agent-webcam.py
- Ambiente: domiciliar, iluminação de interior

**5.3 Calibration (P0 — Márcio)**
- Reportar os thresholds calibrados para Márcio
- Comparar com defaults usados para P4/P5

**5.4 Latency Results**
- Table: gesture→HTTP e event→alarm para todos os trials
- Separar: Márcio vs. voluntários saudáveis

**5.5 False Positive Rate**
- Table: SM vs. threshold simples, por participante
- Análise: P1-P3 (cuidadores, contexto emocional) vs. P4-P5 (controles neutros)
- Destaque: diferença entre grupos como achado novo

**5.6 Detection Rate (Recall)**
- P0 (Márcio): N tentativas, quantas detectadas
- P4/P5: mesma métrica para comparação com paciente

---

## 6. Ordem de execução — próximas semanas

```
Semana atual:
  → Coletar histórico clínico de Márcio (dados para contextualizar Section 4)
  → Agendar sessões com todos os 6 participantes

Sessão P0 (Márcio):
  → Calibração → measure_latency.py → observação naturística

Sessões P1-P3 (cuidadores):
  → false_positive_experiment.py --duration 600 --participant <P1|P2|P3>
  → alarme real + confirmação

Sessões P4-P5 (controles):
  → false_positive_experiment.py --duration 600 --participant P4 P5

Após coleta:
  → generate_tables.py → copiar tabelas para 04_evaluation.tex
  → Escrever 04_evaluation.tex com os dados
  → Preencher placeholders [X]/[Y]/[A]/[B]/[N] em 06_conclusion.tex e abstract

Revisão e preprint:
  → Submeter arXiv (cs.CV + cs.HC)
  → Submeter BIOSTEC/HEALTHINF 2027
```

---

## 7. Venues e calendário

### Alvo primário (recomendado)

**BIOSTEC / HEALTHINF 2027**
- Venue onde o Stell 2024 foi publicado; aceita system papers + case studies
- Deadline típico: **setembro/outubro 2026**
- Pages: 8–12 páginas two-column
- URL: `biostec.scitevents.org` — **verificar deadline 2027 agora**

### Alvo secundário

**ICCHP 2026** (International Conference on Computers Helping People with Special Needs)
- Específico de acessibilidade — case study com paciente real é forte neste venue
- Biennial; verificar se há submissão aberta: `icchp.org`

**IEEE EMBC 2027** — short paper (4 páginas); deadline novembro/dezembro 2026

### Estratégia de preprint
1. Submeter ao **arXiv** (cs.CV + cs.HC) antes de qualquer submissão formal
2. URL permanente: citável na candidatura de bolsa UniMelb

---

## 8. O que o Claude Code CLI deve extrair do repositório

### Para a seção de arquitetura (Section 3 — já completa):
- `backend/services/gest-service/.../DetectionProfileService.java` ✅ extraído
- `backend/services/evnt-service/.../ReportIngressService.java` ✅ extraído
- `frontend/.../poc-gesture-detector.ts` ✅ extraído
- `backend/services/gest-service/.../V1__gest_poc_schema.sql` ✅ extraído
- `backend/services/evnt-service/.../poc/V1__evnt_poc_schema.sql` ✅ extraído

### Para a seção de avaliação (Section 4 — a fazer):
```
Ler e integrar:
- scripts/paper/data/latency_*.csv           → métricas de latência
- scripts/paper/data/fp_*.csv                → taxas de FP por participante
- scripts/paper/data/alarm_log_*.json        → event→alarm latency
- scripts/paper/data/table_latency.tex       → tabela gerada por generate_tables.py
- scripts/paper/data/table_fp.tex            → tabela FP gerada
- Dados clínicos de Márcio (fornecidos pelo usuário)
```

### Para os diagramas (Section 3 — a exportar):
```
Converter para PDF (para incluir no LaTeX):
- docs/paper/template/figures/fig_overview.mmd     → fig_overview.pdf
- docs/paper/template/figures/fig_statemachine.mmd → fig_statemachine.pdf

Ferramentas: mmdc (Mermaid CLI) ou exportar via mermaid.live
  npx -p @mermaid-js/mermaid-cli mmdc -i fig_overview.mmd -o fig_overview.pdf -t neutral
```

---

## 9. Honestidade — O que declarar claramente (seções Discussion e Conclusion)

- **Estudo de caso com N=1 paciente MND** — não é ensaio clínico; não generaliza para a população de MND. Declarar como "preliminary case study" no abstract e na Section 4.
- **Márcio não está em estágio terminal locked-in** — declarar o estágio real de doença; a calibração pode ser mais fácil para ele do que para pacientes em estágio mais avançado.
- **Sem aprovação HREC** — deixar claro que é um estudo piloto informal com consentimento dos participantes, não um ensaio clínico aprovado.
- **Módulos stub** — PAC, HIST, DASH, ACS não avaliados (Section 3.7 já cobre isso).
- **Modelo MediaPipe** — download inicial do Google Storage (Section 5.2 já cobre isso).
- **Polling 5s** — contribui para latência event→alarm; já declarado em Section 5.4.

---

## 10. Conexão com a bolsa UniMelb

Este paper contribui para a candidatura de duas formas:

**Demonstra capacidade de pesquisa autônoma.** Um paper com case study real + preprint + submissão em conferência mostra ao comitê que o candidato sabe formular problema, conduzir avaliação e escrever para peer review.

**Conecta ao universo da pesquisa de destino.** MND é o contexto tanto do NeuroAssist AI quanto do grupo BEST (Berlowitz, Yang et al.). Um paper sobre AAC para MND com dados de paciente real, citando Berlowitz & Yang, cria narrativa coerente.

**Para a carta de motivação:**
> "In addition to my industry experience, I have published work on assistive technology for patients with MND, including a preliminary case study with an advanced-stage patient [citar o paper], which aligns directly with the BEST group's mission and demonstrates my ability to produce independent research contributions in the clinical technology space."

---

*Este documento é o entry point do paper. Claude Code CLI: ao receber qualquer tarefa relacionada ao paper, leia este arquivo primeiro e referencie as seções específicas antes de gerar código, diagramas ou texto.*
