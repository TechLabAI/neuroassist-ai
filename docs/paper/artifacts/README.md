# Aparato experimental — congelado

Material de reprodução do experimento descrito em `docs/paper/template/sections/04_evaluation.tex`
(§ *Software*), submissão HEALTHINF.

Estes arquivos **não são código de produção**. Eles vivem aqui porque a seção de avaliação os cita
nominalmente e três scripts de medição os carregam por caminho — apagá-los quebraria a
reprodutibilidade de um experimento já realizado com participante humano.

## O que está aqui

| Arquivo | Papel no experimento |
|---|---|
| `poc-edge-agent-webcam.py` | O pipeline de detecção. MediaPipe Face Mesh, 6 tipos de gesto, cálculo de EAR/BRI, envia reports ao `evnt-service`. Carregado como módulo por `measure_latency.py` e `false_positive_experiment.py`; lido como texto por `generate_tables.py` (extrai os limiares para a tabela) |
| `docker-compose.poc.yml` | Como `evnt-service` e `gest-service` subiram durante as sessões |
| `poc-seed.sql` | Dados de partida do banco nas sessões |

**O nome dos arquivos foi preservado de propósito.** `04_evaluation.tex` escreve
`\texttt{poc-edge-agent-webcam.py}`; renomear tornaria a seção não verificável.

## Como reproduzir

O `docker-compose.poc.yml` sobe os serviços com `SPRING_PROFILES_ACTIVE: poc`. **Esse profile não
existe mais no `HEAD`** — ele foi removido junto com a fiação alternativa que mantinha (multi-tenancy
e mensageria desligadas em EVNT e GEST, e um `EdgePairingPortStub` que devolvia o tenant fixo
`"poc"` para qualquer dispositivo).

Para reproduzir o ambiente exato das sessões, faça checkout do último commit em que o profile
existia:

```bash
git checkout be3f5a9     # último commit com o profile `poc` no backend
cd backend && ./gradlew bootJar
docker compose -f docker/../docker-compose.poc.yml up
```

Reprodutibilidade vem de um commit fixado, não de manter fiação morta viva no `HEAD`
indefinidamente. O commit acima é o registro; o `HEAD` segue limpo.

## O que ficou de fora, e por quê

- `poc-edge-agent.py` — simulador que enviava um gesto falso ao EVNT. Não participou das medições.
- `poc-alarm-screen.html` — tela de alarme avulsa. O paper registra que os timestamps de entrega
  vieram de um hook `effect()` da **aplicação Angular**, não desta página.
- `poc-run.md`, `PoC-update.md`, `build-poc-jars.log` — documentação de operação do PoC e log de
  build.

Nenhum desses é citado pelo paper nem carregado pelos scripts de medição.

## Relação com `backend/edge-agent-ws.py`

O `edge-agent-ws.py` **continua no backend** e não é PoC: é a ponte WebSocket do laço de
calibração interativa (GEST-FE-007 / GEST-INT-004).

Ele duplicava os limiares EAR/BRI deste agente. Não duplica mais (E2E-032): a fonte é
`backend/edge_agent_core.py`, e a ponte importa dali.

**Este arquivo continua com a cópia própria, de propósito.** Um artefato de reprodução que
dependesse de um módulo do backend deixaria de ser autocontido — e é exatamente essa propriedade
que o torna útil aqui. O preço da escolha é um guarda: `backend/scripts/check_edge_agent_drift.py`
compara os 17 limiares/índices e as três funções de EAR/BRI entre a fonte e esta cópia, por AST
normalizado (comentário e formatação são livres; qualquer mudança de valor ou de operação
reprova). Roda no `ci-backend.yml`, que passou a observar também o caminho deste arquivo.

Se você alterar a matemática ou um limiar aqui, o CI fica vermelho até que os dois lados
concordem — ou até que a divergência seja registrada como intencional no script, como já é o caso
de `COOLDOWN_SECONDS` (3,0 s aqui, 1,5 s na calibração de tiro único).
