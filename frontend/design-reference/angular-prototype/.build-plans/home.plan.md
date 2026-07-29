# Build-Plan — Home (home)
wireframe-ref: §5.1–5.5 | component: HomeComponent | route: /home | ai-drawer: sim

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Responder, em até 2 segundos de leitura, "Márcio está bem?" — a pergunta central do produto (Princípio 2.1). Também mostra o status do dispositivo edge sem exigir navegação extra (Princípio 2.6), cobrindo o fluxo 4.6.

## Regiões / layout (best-effort da §5.2)
- Status ● Márcio Amaral 🟢 Dispositivo online
- Histórico
- Calibração
- ✓ (grande, verde)
- Márcio está bem
- Última verificação: 08h12 sincronizado agora
- [ Ver histórico de hoje ]

## Componentes
- StatusCard (ícone grande + texto de estado + timestamp) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- DeviceStatusBadge (online/offline + última sincronização) — também presente na Topbar (3.2.B) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Link rápido para Histórico do dia → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: Paciente[]  (id, tenantId, nome, dispositivo)
- lê: DispositivoEdge[]  (id, pacienteId, status, ultimaSincronizacao, versao)
- lê: Evento[] (do dia
- lê: com `Evento.comando` resolvido para rótulo + urgência)
- escreve: (nenhuma neste momento — leitura pura)
- enum: DispositivoEdge.status = online | offline

## Mock (mock-data.json)
- pacientes: 1 instâncias de Paciente → mock-data.json#/pacientes
- dispositivos: 1 instâncias de DispositivoEdge → mock-data.json#/dispositivos
- comandos: 3 instâncias de Comando → mock-data.json#/comandos
- eventos: 8 instâncias de Evento → mock-data.json#/eventos

> Importar do `mock-data.json` — **não** reinventar `const MOCK` no componente.

## Estados obrigatórios
- empty, loading, error

## IA inline
- Comportamento: **advisory** — sugere, nunca executa sem confirmação.
  Botões Confirmar / Ignorar; toda decisão logada em auditoria.
