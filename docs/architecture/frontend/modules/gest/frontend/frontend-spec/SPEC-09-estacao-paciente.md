# SPEC-09 — Estação do paciente (superfície always-on)

> Módulo **GEST** · implementa `ADR-008` · depende de `ADR-007` (motor no browser)
>
> Esta SPEC descreve a superfície que **executa** a detecção em regime contínuo. A SPEC-03..08
> descrevem o teste ao vivo (calibração), que é outra coisa: lá uma cuidadora clica e observa;
> aqui a máquina observa o paciente sem ninguém olhando.

---

## 1. Rota e composição

| Item | Valor |
|---|---|
| Rota | `/estacao` — **rota de topo**, irmã de `/login` e `/entrada` |
| Fora do `AppShellComponent` | sim — sem menu, sem barra lateral, sem breadcrumb |
| Guard | `estacaoDeviceGuard` — exige sessão de **dispositivo**, não de usuário |
| `showInNav` | `false` |
| Lib | `libs/gest` (`scope:gest`, `type:feature`) |

A rota não aparece para nenhum papel de usuário. Não é uma tela de trabalho da cuidadora: é o que
a máquina dedicada exibe.

---

## 2. Contratos consumidos — lidos do produtor, não presumidos

> Os quatro contratos abaixo foram lidos nos `@RestController` do backend em 2026-08-25. Qualquer
> divergência entre esta tabela e o código do backend é **defeito desta SPEC**, não do backend —
> o produtor manda. Reescrever um destes campos "por dedução" é o defeito nº 1 catalogado do
> repositório.

### 2.1 Identidade — `POST /oauth2/token/device` (acs-service)

```
request   { "clientId": string, "clientSecret": string }     // ambos @NotBlank
200       AutenticacaoResponse — SEM refreshToken
401       credencial inválida (motivo nunca detalhado)
```

O token traz `tenant_id` e a authority `edge.device`. O `sub` é o `deviceId`.

### 2.2 O que detectar — `GET /api/v1/edge/profile` (edge-service, `edge.device`) — **EDGE-022**

> **Correção de 2026-08-25.** Esta seção descrevia a estação chamando
> `GET /gest-service/detection-profile/devices/{deviceId}` diretamente. **Ela não consegue**: aquele
> endpoint exige `gest.detection-profile.read` e o token de dispositivo carrega só `edge.device` —
> o defeito **C-19**, já registrado no Javadoc do `DetectionProfileClient` do edge-service.
>
> O edge-service passa a servir o perfil ao dispositivo (**EDGE-022**), reusando aquele cliente com
> identidade de serviço. O **formato do corpo abaixo não muda** — é o mesmo
> `DetectionProfileResponse` repassado —, então o store e o adapter do FE seguem válidos. Muda a
> URL, a guarda, e o fato de que a estação passa a receber `pacienteId` por este caminho, que é o
> que faltava para montar o relato.

O contrato de corpo, que vale nos dois casos:

Suporta **GET condicional**: enviar `If-None-Match` e tratar `304`. Resposta traz
`Cache-Control: max-age=60`.

Cada primitivo do perfil:

```
{ tipoGestoId: uuid,          // id de CATÁLOGO
  nome: string,
  engineFeatureRef: string,   // o que o motor casa — vocabulário do commit 89f4ee5
  sensibilidade: 'baixa'|'media'|'alta',
  origem: 'PACIENTE' | 'PERFIL_EMPRESA' | 'INDISPONIVEL' }
```

`origem` **não é decoração**: distingue "esta faixa foi calibrada para este paciente" de "ninguém
calibrou, vale o padrão da empresa" de "o catálogo não pôde ser lido". A estação exibe a terceira
como degradada, nunca em silêncio.

### 2.3 Relato — `POST /api/v1/reports` (evnt-service, `edge.device`)

```
{ reportId, pacienteId, commandRef,
  primitives: [{ tipoGestoId, confidence }],
  confidence, profileVersion, recognizedAt, deviceId }
```

- **`rawFramePayload` NUNCA é enviado.** Presença ⇒ 400 (EVNT-023). O quadro não sai do dispositivo.
- `deviceTenant` é sobrescrito pelo servidor com o `tenant_id` do token — não adianta declarar.
- `201` = evento criado · `200` = idempotente (mesmo `reportId`).

### 2.4 Sinal de vida — `POST /api/v1/edge/heartbeat` (edge-service, `edge.device`)

```
{ agentVersion: string,          // @NotBlank
  runningProfileVersion: number|null,
  deviceId: uuid|null }          // se presente, DEVE bater com o JWT — senão 403
```

**Campo não declarado no corpo ⇒ erro** (invariante de privacidade nº 6, imposta por
`@JsonAnySetter`). Não acrescente telemetria aqui.

Resposta traz `status`, `serverTime`, `currentProfileVersion` e `profileDrift` — é assim que a
estação descobre que há perfil novo a buscar.

---

## 3. Máquina de estados da estação

```
        ┌──────────────┐
        │ sem-vinculo  │  dispositivo não pareado a paciente
        └──────┬───────┘
               │ pareamento existe
        ┌──────▼───────┐
        │  iniciando   │  autenticando · carregando perfil · abrindo câmera
        └──────┬───────┘
               │ modelo carregou e há ≥1 primitivo detectável
        ┌──────▼───────┐   aba oculta / wake lock perdido
        │  vigiando    │ ─────────────────────────────► ┌──────────────┐
        │              │ ◄───────────────────────────── │   parada     │
        └──────┬───────┘   aba visível de novo          └──────────────┘
               │ falha de câmera, perfil ou modelo
        ┌──────▼───────┐
        │  degradada   │  motor não roda — diz por quê, na tela
        └──────────────┘
```

**`parada` e `degradada` são estados de primeira classe, não banners.** Ocupam a tela. A estação
existe para ser olhada de longe: um aviso discreto num canto não cumpre a função.

Nenhum estado além de `vigiando` pode parecer normal.

---

## 4. Invariantes — o que não pode ser negociado na implementação

| # | Invariante | Por quê |
|---|---|---|
| I1 | Nenhum quadro de vídeo, landmark ou `confidence` cru sai do dispositivo além do que §2.3 declara | privacidade; e o backend rejeita |
| I2 | `parada` e `degradada` são visíveis à distância | detecção parada em silêncio é o pior modo de falha |
| I3 | Heartbeat continua batendo mesmo em `parada` e `degradada` | é como o servidor distingue "parada" de "morta"; parar de bater esconde o problema |
| I4 | Um gesto reconhecido é relatado **uma vez** (`reportId` estável, idempotente) | duplicar vira dois alarmes para um pedido |
| I5 | Limiar aplicado vem do perfil; o default do código só vale com `origem: INDISPONIVEL` declarada | senão a sensibilidade escolhida não significa nada |
| I6 | A estação nunca exibe dado clínico além do rótulo do comando reconhecido | a tela fica visível num quarto |

---

## 5. Cenários e2e (`apps/gest-e2e`)

| # | Cenário | Afirma |
|---|---|---|
| E1 | dispositivo pareado → estação chega a `vigiando` | caminho feliz |
| E2 | gesto reconhecido → `POST /api/v1/reports` com o `engineFeatureRef` certo | a cadeia fecha |
| E3 | mesmo gesto duas vezes na janela de cooldown → **um** relato | I4 |
| E4 | aba oculta → estado `parada` visível, heartbeat continua | I2 + I3 |
| E5 | perfil indisponível → `degradada` dizendo o motivo, sem detectar | I5 |
| E6 | rede caída → relato enfileirado, e reenviado ao voltar | fila offline |
| E7 | sessão de **usuário** tentando `/estacao` → recusada | o guard é de dispositivo |
| E8 | nenhum quadro/landmark em qualquer requisição | I1 |

---

## 6. O que esta SPEC NÃO cobre

- **Enroll e pareamento do dispositivo** — são do EDGE, já existem.
- **Esquema seguro de credencial no browser** — pendência P1 da `ADR-008`; até fechar, a estação
  só vale para piloto em máquina dedicada.
- **Classificador ONNX** — pendência P4.
- **O teste ao vivo da calibração** — SPEC-03..08, superfície distinta.
