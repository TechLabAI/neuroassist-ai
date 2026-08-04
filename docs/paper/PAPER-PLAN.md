# NeuroAssist AI — Paper Acadêmico
## Plano completo: contribuição, estrutura, experimentos e venues

*Danilo Amaral · agosto 2026*
*Para uso pelo Claude Code CLI: leia este documento antes de qualquer tarefa relacionada ao paper.*

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

É um **system paper** — a contribuição é a arquitetura e o sistema, não um novo modelo de ML. Em venues de saúde digital e tecnologia assistiva, isso é uma contribuição legítima e valorizada.

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
architecture that decouples gesture type detection from command catalog management.

[RESULTADOS] End-to-end latency from gesture completion to caregiver alarm display 
averaged [X] ms (σ=[Y]) across [N] trials. The deliberate gesture state machine reduced 
false-positive rate from [A]% (simple EAR threshold) to [B]% ([N] healthy volunteers, 
[M] minutes of naturalistic facial movement each).

[CONCLUSÃO] NeuroAssist AI demonstrates that high-accuracy, privacy-preserving 
residual gesture detection is achievable on commodity hardware without network 
dependency, using a calibratable, extensible architecture suitable for home deployment.
```

---

### Section 1 — Introduction

**Parágrafos a cobrir:**
1. O problema: MND/ALS destrói o controle motor, deixando apenas microgestos faciais. É a fase mais devastadora — e mais negligenciada em AAC.
2. O gap: sistemas existentes (Tobii, Grid 3) exigem rede, hardware especializado ou calibração de fábrica não adaptável.
3. Nossa proposta: detecção offline-first com webcam comum, calibração por paciente, arquitetura extensível.
4. Contribuições (listar as 3 do item 1).
5. Estrutura do paper.

**Fatos clínicos para a introdução (com fonte):**
- MND afeta ~41.000 australianos e ~30.000 brasileiros; incidência global ~2–3/100.000/ano
- Sobrevida média: 2–5 anos após diagnóstico; falência respiratória é a principal causa de morte
- Em estágios avançados, up to 30% dos pacientes desenvolve *locked-in-like syndrome* com comunicação dependente de microgestos
- NIV estende sobrevida 3x vs. melhor medicação (Berlowitz & Sheers, 2021) — contexto de por que comunicação com cuidadores durante NIV importa

---

### Section 2 — Related Work

**Clusters de literatura a cobrir:**

**2.1 AAC e doenças neurodegenerativas**
- Eye-tracking AAC (Tobii Dynavox, EyeGaze): alta precisão, alto custo, requer controle ocular razoável
- BNCI/BCI (interface cérebro-computador via EEG): invasivo ou de baixa portabilidade
- Surveys de AAC em MND/ALS: Beukelman & Mirenda (2013); Clarke et al. (2021) — gap em sistemas domiciliares de baixo custo

**2.2 Detecção de gesto facial via CV**
- OpenCV + Haar Cascades (primeira geração) — baixa robustez a variações de iluminação
- Dlib + 68 landmarks — precisão, mas sem suporte a WASM
- MediaPipe Face Mesh (468 landmarks) — estado da arte em client-side, suporte WASM nativo (Lugaresi et al., 2019; Google, 2023)
- EAR (Eye Aspect Ratio) para detecção de piscada: Soukupová & Čech (2016) — o método usado

**2.3 Detecção de gesto deliberado (anti-falso-positivo)**
- Problema de involuntary blink: freq. natural 15–20 blinks/min — qualquer threshold simples de EAR produz falsos-positivos
- State-machine approaches para gesto intencional: raro na literatura de AAC; mais comum em HCI (Zander & Kothe, 2011)
- Dwell time vs. state machine: dwell time exige fixação — inadequado para piscada

**2.4 Edge computing e privacidade em saúde**
- GDPR/LGPD e dados de saúde: vídeo facial é dado sensível; processamento local é princípio de privacy-by-design (European Data Protection Board, 2020)
- WebAssembly como runtime de edge ML: TensorFlow.js, ONNX Runtime Web, MediaPipe Tasks — velocidade comparável a nativo com isolamento de segurança

**2.5 Arquitetura de software para sistemas de saúde**
- Hexagonal architecture (Cockburn, 2005) em health IT: aplicações em EHR e dispositivos médicos (Kumar et al., 2022)
- Microservices em AAC: não encontrado na literatura — contribuição de engenharia

**Papers prioritários para encontrar e citar:**
- Soukupová, T. & Čech, J. (2016). Real-Time Eye Blink Detection using Facial Landmarks. CVWW.
- Lugaresi, C. et al. (2019). MediaPipe: A Framework for Building Perception Pipelines. arXiv:1906.08172.
- Clarke, M. et al. (2021). AAC provision for people with ALS: an update. Disability and Rehabilitation: AAC, 35(1).
- Zander, T.O. & Kothe, C. (2011). Towards passive brain-computer interfaces. Journal of Neural Engineering, 8(2).
- Beukelman, D. & Mirenda, P. (2013). Augmentative and Alternative Communication (4th ed.).

---

### Section 3 — System Architecture

**3.1 Visão geral**
```
[Webcam] → [Browser: MediaPipe WASM] → [EAR state machine]
              ↓ gesture detected
          [Edge Agent: POST /api/v1/reports]
              ↓
          [evnt-service: 12-step ingestion]
              ↓
          [Caregiver UI: polling → FULL_SCREEN_ALARM]
              ↓
          [Confirmation: verdict=real/falso_positivo]
```

**3.2 Detecção offline-first (GEST)**
- MediaPipe FaceLandmarker via WebAssembly, servido de `/assets/mediapipe-wasm/`
- CPU delegate (100% compatível sem GPU)
- EAR calculado em 6 pontos por olho (índices MediaPipe: {33,160,158,133,153,144})
- Máquina de estados: descrever as 6 transições e critérios

**3.3 Calibração por paciente (DetectionProfile)**
- `engineFeatureRef`: identificador plugável do tipo de gesto (`mp.blink.double.v1`)
- Thresholds: `EAR_CLOSE`, `EAR_OPEN`, `BLINK_MIN_FRAMES`, `OPEN_MIN_FRAMES`, `DOUBLE_BLINK_WINDOW_MS`
- Profile versioning: cada publicação cria nova versão imutável

**3.4 Arquitetura hexagonal (diagrama de dependências)**
- Mostrar: api/ → application/port/in → application/service → domain/ ← infrastructure/ → application/port/out
- ArchUnit rules RULE-01 a RULE-09
- `engineFeatureRef` como ponto de extensão para novos detectores

**3.5 Fluxo de ingestão (12 passos do ReportIngressService)**
- Descrever os 12 passos; destacar: idempotência via `reportId`, validação via Feign para gest-service, NotificationDispatch tier hierárquico

**3.6 O que o PoC prova vs. o que é arquitetura futura**
- Tabela clara: o que está implementado vs. o que usa stub/no-op (JWT, multi-tenancy, push, HIST, SSE)
- Justificativa: PoC valida o fluxo ponta a ponta sem os módulos periféricos

---

### Section 4 — Evaluation

**4.1 Métricas a medir**

| Métrica | Como medir | Ferramenta |
|---|---|---|
| Latência end-to-end (gesto → alarme) | Timestamp no edge agent + timestamp no polling do Angular | `performance.now()` + log do evnt-service |
| Taxa de falso-positivo | N minutos de movimento facial naturístico, sem intenção de disparar | Voluntários saudáveis |
| Taxa de detecção (recall) | N tentativas intencionais de piscar duplo | Voluntários saudáveis |
| Tempo de inicialização do WASM | Primeiro load do FaceLandmarker | `performance.now()` |
| Taxa de frames do detector | FPS real no loop requestAnimationFrame | Contador no detector |
| Comparativo: threshold simples vs. máquina de estados | Mesmo dataset, dois algoritmos | Script offline sobre frames gravados |

**4.2 Protocolo de experimento mínimo (solo)**

Para um paper de conferência tier B/C, 5–10 participantes saudáveis são suficientes para prova de conceito. Protocolo:

```
Participantes: 5–10 voluntários (amigos, família)
Hardware: webcam comum (720p), laptop Windows/macOS/Linux
Condições: iluminação normal de interior, câmera a ~50cm do rosto

Sessão por participante (~15 min):
1. Calibração (3 min): ajustar EAR_CLOSE para o participante
2. Baseline falso-positivo (5 min): conversa natural, leitura — sem tentar disparar
   → Registrar quantos alarmes falsos
3. Detecção intencional (5 min): 20 tentativas de piscar duplo deliberado
   → Registrar quantas dispararam com sucesso
4. Comparativo (opcional): repetir com threshold simples (sem state machine)
```

**4.3 Dados do PoC já existentes**
- 9 critérios de sucesso documentados no `poc-run.md` — todos validados (✅)
- Sessões 1–4 do `PoC-update.md` documentam problemas reais e soluções reais — isso é contribuição de engenharia documentada

---

### Section 5 — Discussion

**Pontos a cobrir:**
1. **Acessibilidade de hardware:** webcam comum de ~R$ 50–200 vs. Tobii (~USD 3.000+). Implicação para LMIC (Low and Middle Income Countries) — Brasil, países em desenvolvimento.
2. **Privacy-by-design:** vídeo nunca sai do dispositivo. Implicação para LGPD/GDPR em contexto de dado de saúde facial.
3. **Extensibilidade:** `engineFeatureRef` permite adicionar detecção de sobrancelha, sorriso, direção do olhar sem alterar o domínio — demonstrar com exemplo fictício.
4. **Limitações honestas:**
   - Pixel-density dependente: iluminação muito baixa afeta EAR (documentado no troubleshooting)
   - GPU delegate fallback aumenta latência (documentado nas sessões do PoC)
   - Sem avaliação com usuários com MND real (ética/HREC necessários)
   - Stub adapters: módulos reais (PAC, HIST, DASH) não avaliados
5. **Trabalho futuro:** SSE em vez de polling, módulo PAC com catálogo real, estudo com pacientes (protocolo ético), detecção de gestos adicionais.

---

### Section 6 — Conclusion

- Reafirmar as 3 contribuições
- Resultado principal (latência, FP rate)
- Impacto esperado: comunicação domiciliar de baixo custo para pacientes em fase avançada de MND

---

## 3. O que o Claude Code CLI deve extrair do repositório

Quando tu rodares o Claude Code CLI com este documento como contexto, ele deve:

### Para a seção de arquitetura (Section 3):
```
Ler e extrair:
- backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionProfileService.java
  → Lógica de publicação do perfil, versionamento
- backend/services/evnt-service/src/main/java/com/neuroassist/evnt/application/service/ReportIngressService.java
  → Os 12 passos do fluxo de ingestão
- frontend/design-reference/angular-prototype/src/app/core/poc-gesture-detector.ts
  → Máquina de estados, constantes EAR, lógica de detecção
- backend/services/gest-service/src/main/resources/db/migration/V1__gest_poc_schema.sql
  → Schema do domínio
- backend/services/evnt-service/src/main/resources/db/migration/poc/V1__evnt_poc_schema.sql
  → Schema de eventos
```

### Para os diagramas (Section 3):
```
Gerar em Mermaid ou PlantUML:
1. Diagrama de sequência: edge agent → gest-service → evnt-service → Angular → confirmação
2. Diagrama de componentes: hexagonal por microserviço (api/application/domain/infrastructure)
3. State machine diagram: máquina de estados do poc-gesture-detector.ts
4. Diagrama C4 nível 2: containers do sistema
```

### Para a seção de avaliação (Section 4):
```
Criar scripts de instrumentação:
- scripts/paper/measure_latency.py
  → Adicionar timestamps no poc-edge-agent-webcam.py (gesture_completed_ts, report_sent_ts, event_created_ts)
  → Medir: gesture_to_report_ms, report_to_event_ms, event_to_alarm_visible_ms
- scripts/paper/false_positive_experiment.py
  → Rodar detector por N minutos sem intenção, contar alarmes falsos
  → Comparar: state_machine vs. simples threshold crossing
  → Output: CSV com timestamp, ear_left, ear_right, state, triggered
- scripts/paper/generate_tables.py
  → Ler CSVs dos experimentos → gerar tabelas LaTeX/Markdown
```

### Para a escrita do paper:
```
Usar template:
- docs/paper/template/ (criar se não existir)
  - main.tex (IEEE ou ACM two-column)
  - sections/
    - 01_introduction.tex
    - 02_related_work.tex
    - 03_architecture.tex
    - 04_evaluation.tex
    - 05_discussion.tex
    - 06_conclusion.tex
  - figures/
    - fig1_system_overview.pdf
    - fig2_state_machine.pdf
    - fig3_hexagonal.pdf
    - fig4_latency_boxplot.pdf
  - references.bib
```

---

## 4. Venues e calendário de submissão

### Alvo primário (recomendado para primeira publicação)

**BIOSTEC / HEALTHINF 2027**
- Venue: onde o Stell 2024 foi publicado; aceita system papers de saúde digital e engenharia
- Deadline típico: **setembro/outubro 2026** (edição 2027)
- Pages: 8–12 páginas two-column
- Acceptance rate: ~30%
- URL: `biostec.scitevents.org`

**Ação imediata:** verificar o site agora para confirmar o deadline 2027.

### Alvo secundário

**ICCHP 2026** (International Conference on Computers Helping People with Special Needs)
- Específico de acessibilidade e tecnologia assistiva
- Biennial; próxima edição: 2026 (julho, Europa)
- **Verificar se ainda há submissão aberta**
- URL: `icchp.org`

**IEEE EMBC 2027**
- Papers de 4 páginas (short papers aceitos)
- Deadline típico: novembro/dezembro 2026
- Maior conferência de engenharia biomédica do mundo

### Estratégia de preprint

**Antes de qualquer submissão formal:**
1. Submeter ao **arXiv** (cs.CV + cs.HC) — estabelece prioridade, é citável, gratuito
2. URL permanente com DOI: permite mencionar na candidatura de bolsa mesmo antes de peer review

---

## 5. Ordem de execução recomendada

```
Semana 1–2: Instrumentação de latência
  → Adicionar timestamps no edge agent e no evnt-service
  → Rodar N=50 testes, coletar CSV

Semana 2–3: Experimento de falso-positivo
  → 5 voluntários, protocolo da Section 4.2
  → Comparar state machine vs. threshold simples

Semana 3–4: Escrever draft
  → Section 3 (arquitetura) — o Claude Code extrai do repo
  → Section 4 (resultados) — dos CSVs dos experimentos
  → Section 2 (related work) — pesquisa bibliográfica

Semana 4–5: Diagramas e figuras
  → State machine (já documentado no PoC-update.md)
  → Diagrama de sequência end-to-end
  → Boxplot de latência

Semana 5–6: Revisão, preprint no arXiv, submissão ao venue alvo
```

---

## 6. Conexão com a bolsa UniMelb

Este paper contribui para a candidatura de duas formas:

**Demonstra capacidade de pesquisa autônoma.** Um paper solo publicado (mesmo que preprint + aceito em conferência) mostra ao comitê de bolsas que tu sabes formular um problema, conduzir avaliação e escrever para peer review — independentemente da colaboração com o BEST.

**Conecta ao universo da pesquisa de destino.** MND é o contexto tanto do NeuroAssist AI quanto do grupo BEST. Um paper sobre tecnologia assistiva para MND, com citações do trabalho do Berlowitz, cria uma narrativa coerente: "este candidato já publica sobre MND antes de entrar no programa".

**Para a carta de motivação da bolsa:**
> "In addition to my industry experience, I have published work on assistive technology for patients with MND [citar o paper], which aligns directly with the BEST group's mission and demonstrates my ability to produce independent research contributions."

---

## 7. O que NÃO colocar no paper (honestidade)

- **Não afirmar** que o sistema foi testado em pacientes com MND real — foi testado em voluntários saudáveis no PoC
- **Não afirmar** que os módulos PAC, HIST, DASH, ACS, EDGE estão implementados — são stubs/no-op no PoC; apresentar como trabalho futuro
- **Não afirmar** que a máquina de estados foi comparada formalmente com outros métodos da literatura — apenas com o threshold simples do próprio sistema
- **Não afirmar** privacidade absoluta — o modelo de face landmarker é baixado do Google Storage no primeiro uso (cacheable); ser honesto sobre isso na seção de discussão

---

## 8. Referências já identificadas (a adicionar ao .bib)

```bibtex
@inproceedings{soukupova2016,
  author={Soukupová, Tereza and Čech, Jan},
  title={Real-Time Eye Blink Detection using Facial Landmarks},
  booktitle={21st Computer Vision Winter Workshop (CVWW)},
  year={2016}
}

@article{lugaresi2019mediapipe,
  author={Lugaresi, Camillo and others},
  title={MediaPipe: A Framework for Building Perception Pipelines},
  journal={arXiv preprint arXiv:1906.08172},
  year={2019}
}

@inproceedings{stell2024pva,
  author={Stell, Anthony J. and others},
  title={Identification of Patient Ventilator Asynchrony in Physiological Data Through Integrating Machine-Learning},
  booktitle={BIOSTEC/HEALTHINF},
  pages={436--443},
  year={2024},
  doi={10.5220/0012366700003657}
}

@article{berlowitz2021niv,
  author={Berlowitz, David J. and Sheers, Nicole},
  title={Not Only about the Drugs},
  journal={Annals of the American Thoracic Society},
  volume={18},
  number={3},
  pages={419--420},
  year={2021}
}

@article{yang2026pva,
  author={Yang, Joel and others},
  title={Using Machine Learning to Identify Patient-Ventilator Asynchrony during Non-Invasive Ventilation in Patients with Motor Neurone Disease},
  journal={Sleep Medicine},
  year={2026},
  doi={10.1016/j.sleep.2025.108637}
}

@book{beukelman2013aac,
  author={Beukelman, David R. and Mirenda, Pat},
  title={Augmentative and Alternative Communication},
  edition={4},
  publisher={Brookes Publishing},
  year={2013}
}

@article{cockburn2005hexagonal,
  author={Cockburn, Alistair},
  title={Hexagonal Architecture},
  year={2005},
  note={Available at: alistair.cockburn.us/hexagonal-architecture}
}
```

---

*Este documento é o entry point do paper. Claude Code CLI: ao receber qualquer tarefa relacionada ao paper, leia este arquivo primeiro e referencie as seções específicas antes de gerar código, diagramas ou texto.*
