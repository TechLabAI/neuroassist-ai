# Build-Plan — Onboarding (onboarding)
wireframe-ref: §5.1–5.5 | component: OnboardingComponent | route: /onboarding | ai-drawer: não

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Cadastro inicial de tenant, paciente e pareamento do dispositivo edge — conduzido uma única vez por Danilo Amaral (condutor técnico), não pela cuidadora no dia a dia. Cobre o fluxo 4.5.

## Regiões / layout (best-effort da §5.2)
- Configuração inicial
- 1. Unidade de cuidado: [ Família Amaral ]
- 2. Paciente: [ Márcio Amaral ]
- 3. Dispositivo edge: [ Pareando... ● ]
- 4. Primeiro comando: → vai para Tela 04 (catálogo)
- 5. Convidar cuidadora: [ Jamilly Amaral email ]
- [ Concluir configuração ]

## Componentes
- Wizard de passos (tenant → paciente → dispositivo → primeiro comando → convite) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- DevicePairingStatus (indicador de pareamento em andamento) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- InviteCaregiverForm → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: Tenant[]  (id, nome, tipo)
- lê: Paciente[]  (id, tenantId, nome, dispositivo)
- lê: DispositivoEdge[]  (id, pacienteId, status, ultimaSincronizacao, versao)
- escreve: Tenant
- escreve: Paciente
- escreve: DispositivoEdge
- escreve: Cuidador (cria os registros)
- escreve: Comando (opcionalmente já cria o primeiro comando do catálogo
- escreve: se o condutor preencher na Tela 04 antes de voltar)
- enum: Tenant.tipo = familia | clinica
- enum: DispositivoEdge.status = online | offline

## Mock (mock-data.json)
- (sem coleção de mock declarada)

## Estados obrigatórios
- empty, loading, error

## IA inline
- IA não disponível nesta tela.
