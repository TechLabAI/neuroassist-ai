# Retomada — GEST / estação do paciente (W10)

> Escrito em 2026-08-25 para sobreviver a um `/clear`. O contexto da conversa some;
> este arquivo e os commits não.

## Onde parou

Onda **W10** (estação do paciente) **completa em código**, P1 a P5:

| P | Entrega | Commit |
|---|---|---|
| P1 | X-Empresa-Id no eligibility + DetectionContractService | `fb7d0ad` |
| P2 | Identidade de dispositivo (`DeviceSessionStore`) | `4b751a9` |
| P3 | Perfil + limiares do perfil + guarda de drift | `53bd12d` |
| — | **EDGE-022** (backend): `GET /api/v1/edge/profile` | `6f838e1` |
| P4 | Rota `/estacao` + laço always-on + `POST /api/v1/reports` | `2eb23b9` |
| P5 | Heartbeat + Wake Lock + estado `parada` + fila offline | `57c6872` |

Estado dos gates: **1.363 unitários verdes** (11 projetos), build da aplicação limpo.
Branch `dev/backend`, tudo commitado e enviado.

## Como continuar

```
/fe-autopilot GEST --resume
```

O checkpoint está em `tmp/fe-autopilot/GEST/state.json` e aponta para **P6**.

## O que falta, em ordem de importância

1. **GEST-FE-033 — os 8 cenários e2e da SPEC-09 §5.** É a maior redução de incerteza
   disponível: **a estação nunca rodou contra o backend de verdade**, nem contra o
   `GET /api/v1/edge/profile` recém-criado. Todos os mocks foram escritos lendo os
   `@RestController` do produtor (o que reduz o risco), mas ler não é exercitar.
2. **Validator final** (`/fe-validator GEST`) — fecha o ciclo e recalcula o score.
   O baseline desta rodada foi 51/100, com −25 pelo gap "cadeia sem runtime", que a
   W10 fechou.

## Pendências declaradas, que NÃO devem ser esquecidas

- **`clientSecret` não pode viver no browser** (ADR-008 P1, herdada da ADR-007 custo nº 5).
  Até haver esquema próprio, a estação vale para **piloto fechado em máquina dedicada**
  e nada além. Está na ADR e no bloqueador da `GEST-FE-027`.
- **Faixa de limiares calibrada para BLENDSHAPES** (ADR-008 P6). O backend publica
  valores de BRI — razão geométrica `(eye_top_y − brow_peak_y) / inter_eye_dist` —, e o
  motor do browser lê blendshapes do MediaPipe. Escalas diferentes. Hoje a divergência é
  **afirmada** pelo guarda de drift (`CAMPOS_ESCALA_BLENDSHAPE`), não silenciada.
- **Workflows de CI desativados** desde 25/08/2026 (`gh workflow enable`). O guarda de
  drift existe e **não roda**.
- **`shared/empresa-context` não é buildável** — só `project.json` e `src`, sem
  `ng-package.json`. Ninguém consegue importá-la; a `GEST-FE-027` usa interface local.
- **`TODO[GEST-FE-009][deferred]`** — validação de contrato por stream, pendente de
  coordenação com `EdgeDetectionBrowserAdapter.init()`.

## Armadilhas desta base que custaram tempo hoje

- **O gate de build é `npx nx build neuroassist-ai-web`**, nunca `nx build gest`. Produção
  em `libs/gest` só pode importar `@neuroassist/shared/contracts` (o `tsconfig.lib.json`
  sobrescreve `paths`); qualquer outra lib **compila no Jest e reprova no build da app**.
- Se precisar compartilhar um símbolo, mova para `contracts` e **reexporte** da origem.
  Nunca redeclare: `HttpContextToken` é comparado por referência, e duas declarações
  iguais falham em silêncio.
- **Comentário não é gate.** Três invariantes desta sessão estavam afirmadas em comentário
  e verificadas em nada.
- **Teste verde não prova que pega o defeito.** Quebre a produção de propósito e confirme
  o vermelho.
