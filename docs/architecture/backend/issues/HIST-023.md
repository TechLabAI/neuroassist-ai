# HIST-023 -- FactType vocabulary catalog: configurável + endpoint GET /fact-types

## Contexto

O vocabulário de `factType` v1 é co-owned com os produtores (EVNT/PAC/ACS/EDGE) e deve
ser configurável sem recompilação — carregado de `application.yml` (ou de um futuro banco
de vocabulário). O endpoint `GET /api/v1/hist/fact-types` expõe a lista ativa. A validação
de `factType` desconhecido usa este catalog (SPEC-02, §5 de api-contract).

## Spec de referencia

- SPEC-02 (factType controlado, rejeição de tipo desconhecido)
- `contracts/api-contract.md §5` (vocabulário v1 seed)

## Escopo

**`application.yml`:**
```yaml
hist:
  fact-types:
    - evento.confirmado
    - evento.descartado
    - evento.feedback.registrado
    - comando.sensibilidade.editada
    - comando.recalibrado
    - acesso.concedido
    - acesso.revogado
    - dispositivo.pareado
    - dispositivo.despareado
    - registro.corrigido
```

**`com.neuroassist.hist.application.service.FactTypeCatalogService` (`@Service`):**
- Injeta `@ConfigurationProperties("hist") HistProperties` com `factTypes: List<String>`.
- `listActive()`: retorna a lista ordenada.
- `isKnown(factType: String)`: retorna `Boolean` (usado por `RecordAuditFactService` e `FactType.of()`).

**`FactTypeCatalogController` (já em HIST-005):** `GET /api/v1/hist/fact-types` → 200 com lista.

**`FactType.of()` (HIST-002):** delegar para `FactTypeCatalogService.isKnown()` ou receber
lista no construtor (evitar acoplamento ao Spring no domínio — usar injeção).

## Resultado esperado

`GET /api/v1/hist/fact-types` retorna os 10 tipos do v1 seed. Adicionar novo tipo em
`application.yml` sem recompilar é suficiente.

## Criterios de aceite

- [ ] `GET /api/v1/hist/fact-types` retorna lista com exatamente os 10 tipos do vocabulário v1 seed.
- [ ] `POST /audit-facts` com `factType="desconhecido"` retorna `422 /errors/unknown-fact-type`.
- [ ] Adicionar `"novo.tipo"` em `application.yml` e reiniciar → aceito na ingestão.
- [ ] `FactTypeCatalogService` é injetável e testável com lista mockável.
- [ ] Nenhuma dependência de Spring em `FactType` domain class (usar validação via service injetado, não estática).

## Dependencias

- HIST-001 (ConfigurationProperties binding)
- HIST-002 (FactType value object — refatorar `of()` para ser configurável)
- HIST-005 (FactTypeCatalogController — endpoint já previsto)
- HIST-006 (RecordAuditFactService usa catalog para validação)

## Estimativa

S

## Prioridade

P9
