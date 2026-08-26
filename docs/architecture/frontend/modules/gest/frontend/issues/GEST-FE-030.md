# GEST-FE-030 -- Rota /estacao + laço always-on + POST /api/v1/reports (fecha a cadeia)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
A cadeia gesto → relato → evento → alarme passa a existir. A rota `/estacao` roda o detector em
regime contínuo e, a cada reconhecimento, emite `POST /api/v1/reports`.

## Context / Problem statement
- **Esta é a issue que faz o produto existir.** Hoje o detector só roda quando alguém clica em
  "Testar gesto" na calibração; nenhuma rota o executa continuamente, e nenhum código produz o
  evento de gesto. As telas de alarme são alimentadas por mock.
- Contrato lido no produtor (`ReportIngressController` + `ReportIngressUseCase`), não presumido.
- `rawFramePayload` presente ⇒ **400** (EVNT-023). O quadro não sai do dispositivo — e o backend
  recusa quem tentar, então enviar "só para depurar" quebra em produção.
- `deviceTenant` é sobrescrito pelo servidor com o `tenant_id` do token: declarar não adianta, e
  relato entre empresas é rejeitado com 403.

## Approach (execution path)
1. Rota de topo `/estacao` em `app.routes.ts` — fora do `AppShellComponent`, como `/login`.
2. `EstacaoComponent`: tela cheia, preview + malha (`attachOverlay`), estado em alto contraste.
3. Laço: obter sessão de dispositivo (FE-027) → perfil (FE-028) → limiares (FE-029) → `detector.start()`.
4. `onGesture(engineFeatureRef, confidence)` → casar com o primitivo do perfil → montar o relato.
5. `reportId` **estável e idempotente**: derivado de `deviceId + engineFeatureRef + janela do
   cooldown`. Reenvio da fila não pode virar segundo alarme.
6. `commandRef` e `pacienteId` vêm do pareamento/perfil, nunca de configuração local.

## Non-goals
- Fila offline (GEST-FE-032), visibilidade/wake lock (GEST-FE-031).

## Layer(s)
frontend / feature lib + rota

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §1, §2.3, §3, §4 (I1, I4, I6)
- `ADR-008` D2, D4

## Architecture alignment
- `standalone: true`, `OnPush`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature + rota**
- [ ] `libs/gest/src/lib/estacao/estacao.component.ts`
- [ ] `libs/gest/src/lib/estacao/estacao.component.spec.ts`
- [ ] `libs/gest/src/lib/estacao/report-ingress.port.ts` + adapter HTTP
- [ ] `libs/gest/src/lib/estacao/estacao.guard.ts` — exige sessão de DISPOSITIVO
- [ ] `apps/neuroassist-ai-web/src/app/app.routes.ts` — rota de topo
- [ ] `libs/gest/src/index.ts` — export

## Acceptance
- Gesto reconhecido gera **um** `POST /api/v1/reports` com o `tipoGestoId` correspondente ao
  `engineFeatureRef` emitido.
- Repetir o gesto dentro do cooldown **não** gera segundo relato (mesmo `reportId`).
- Nenhuma requisição carrega quadro, landmark ou campo não declarado.
- Sessão de usuário em `/estacao` é recusada pelo guard.
