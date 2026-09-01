<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — Playback de áudio cacheado por referência e fallbacks de degradação

## Status
aceita

> Escopo: local (`conv-service`). Implementa **ADR-017 (accepted)** — "TTS via ElevenLabs: síntese na fase
> de TREINAMENTO, cache de áudio" (que **supersede parcialmente** o trecho do ADR-013 sobre TTS síncrono).
> Registra o papel do CONV no runtime; não reabre a decisão de síntese.

## Contexto
A voz sintetizada do paciente (clone ElevenLabs) já existe. O ADR-017 fechou que a **síntese acontece só na
fase de TREINAMENTO** (TREI), com o áudio **cacheado** para reuso: o Elasticsearch guarda texto + embedding
(ADR-015) + **referência** ao arquivo de áudio (não o binário), e o arquivo vive num storage separado —
**filesystem local** no servidor único (OPS-002), em MP3/Opus. O runtime do CONV **nunca** chama a API do
ElevenLabs. Falta registrar como o CONV reproduz e como degrada quando o áudio não existe.

## Decisão
No runtime, ao selecionar um nó `frase`, CONV retorna o **texto** + a **referência de áudio** (`audio_ref`)
do locale ativo; o frontend reproduz o arquivo cacheado. CONV **lê** o áudio por referência (via
`AudioRefPort` na `infrastructure`, resolvendo o arquivo no filesystem) e **nunca** escreve áudio nem chama
ElevenLabs — a escrita é do TREI. O storage de áudio é uma **dependência soft**: se o `audio_ref` estiver
ausente (frase nova ainda não treinada) ou o arquivo indisponível, CONV retorna o texto com referência
nula e o frontend cai para o **TTS nativo do navegador (Web Speech API)** — voz genérica, pior fidelidade,
mas comunicação não para (OPS-004). Esse `degraded`/ausência de áudio é **explícito** na resposta, para a UI
poder avisar por que a voz mudou.

## Consequências
**Positivas:** runtime sem custo de API por interação, sem latência de rede e sem depender de internet no
caminho crítico de comunicação diária (FIN-002); o índice guarda referência leve, não blobs; comunicação
sobrevive à ausência de áudio (Web Speech). **Negativas / trade-offs:** frase autorada fora de uma sessão de
TREINAMENTO não tem áudio até a próxima sessão — mitigado pelo fallback; exige um contrato de storage
(layout de diretórios, nomenclatura do `audio_ref`, sinal de presença/ausência) a formalizar em specs;
áudio de voz clonada é dado sensível/biométrico (SEC-002, LGPD) — mesmas salvaguardas de REG-001/OPS-002.
Reverter para síntese em runtime é remoção de cache (o pipeline ElevenLabs continua no TREI), mas contraria
o ADR-017.
