# Roadmap

O trabalho do NeuroAssist AI é planejado por módulo, em issues atômicas. Cada módulo tem o seu plano e as suas issues, no backend e no frontend. Este arquivo só indica onde cada coisa está.

## Por módulo

### GEST, Detecção de Gesto (Edge)
- Plano: [backend](docs/architecture/backend/plans/plan-GEST.md) · [frontend](docs/architecture/frontend/plans/plan-FE-GEST.md)
- Issues: backend `GEST-NNN` em [backend/issues](docs/architecture/backend/issues/)

### EVNT, Eventos e Alarmes
- Plano: [backend](docs/architecture/backend/plans/plan-EVNT.md) · [frontend](docs/architecture/frontend/plans/plan-FE-EVNT.md)
- Issues: backend `EVNT-NNN` em [backend/issues](docs/architecture/backend/issues/)

### PAC, Pacientes e Catálogo de Comandos
- Plano: [backend](docs/architecture/backend/plans/plan-PAC.md)
- Issues: backend `PAC-NNN` em [backend/issues](docs/architecture/backend/issues/)

### ACS, Cuidadores e Acesso
- Plano: [backend](docs/architecture/backend/plans/plan-ACS.md) · [frontend](docs/architecture/frontend/plans/plan-FE-ACS.md)
- Issues: backend `ACS-NNN` em [backend/issues](docs/architecture/backend/issues/)

### EDGE, Integração de Dispositivo Edge
- Plano: [backend](docs/architecture/backend/plans/plan-EDGE.md)
- Issues: backend `EDGE-NNN` em [backend/issues](docs/architecture/backend/issues/)

### HIST, Histórico e Auditoria
- Plano: [backend](docs/architecture/backend/plans/plan-HIST.md) · [frontend](docs/architecture/frontend/plans/plan-FE-HIST.md)
- Issues: backend `HIST-NNN` em [backend/issues](docs/architecture/backend/issues/) · frontend `FE-HIST-NNN` em [frontend/issues](docs/architecture/frontend/issues/)

### DASH, Dashboard e Notificações
- Plano: [backend](docs/architecture/backend/plans/plan-DASH.md) · [frontend](docs/architecture/frontend/plans/plan-FE-DASH.md)
- Issues: backend `DASH-NNN` em [backend/issues](docs/architecture/backend/issues/) · frontend `DASH-FE-NNN` em [frontend/issues](docs/architecture/frontend/issues/)

### CONV, Conversação por Árvore de Falas
- Plano: [backend](docs/architecture/backend/plans/plan-CONV.md)
- Issues: backend `CONV-NNN` em [backend/issues](docs/architecture/backend/issues/)

### TREI, Treinamento Assistido por IA
- Plano: [backend](docs/architecture/backend/plans/plan-TREI.md) · [frontend](docs/architecture/frontend/plans/plan-FE-TREI.md)
- Issues: backend `TREI-NNN` em [backend/issues](docs/architecture/backend/issues/)

## Visão geral

- Ordem de execução: [master plan do backend](docs/architecture/backend/plans/architecture-master-plan.md) e [do frontend](docs/architecture/frontend/plans/architecture-master-plan.md)
- Integração ponta a ponta: [plano E2E do backend](docs/architecture/backend/plans/plan-e2e-integration.md) e [do frontend](docs/architecture/frontend/plans/plan-FE-E2E.md)
- Gateway, registro de serviços e migrações: [GWY](docs/architecture/backend/plans/plan-GWY.md), [REG](docs/architecture/backend/plans/plan-REG.md) e [MIG](docs/architecture/backend/plans/plan-MIG.md)
- Decisões de arquitetura: [ADRs do backend](docs/architecture/backend/adr/) e [do frontend](docs/architecture/frontend/adr/)
- Pesquisa e artigo acadêmico: [plano do paper](docs/paper/PAPER-PLAN.md)
