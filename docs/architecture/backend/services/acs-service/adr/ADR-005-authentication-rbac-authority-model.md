<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Local authentication and RBAC authority model

## Status
aceita

## Contexto
Toda requisição a dados sensíveis de paciente precisa carregar uma identidade autenticada, com tenant e
autoridades, para que os demais serviços apliquem `@PreAuthorize("hasAuthority('<code>')")` (constituição
RBAC.001). O RAG confirma que os serviços do projeto (GEST, EDGE, EVNT) já assumem **OAuth2 JWT resource
server com `jwk-set-uri` via REG** — ou seja, existe um emissor de JWT no ecossistema, mas ninguém ainda
foi designado como esse emissor. As opções: (a) um IdP externo (Keycloak/Auth0/OIDC); (b) o gateway
(GWY) emitir tokens; (c) o ACS ser o authorization server local. Um IdP externo adiciona infraestrutura
e integração desproporcionais ao MVP (uma família, um paciente); o GWY é um roteador, não o dono da
identidade; e a identidade (cuidador, papel, tenant) já é domínio do ACS.

## Decisão
Vamos fazer do **ACS o authorization server local** do MVP: ele autentica o `Cuidador` por
credencial (hash bcrypt/argon2), emite um **JWT de curta duração** com `sub = cuidadorId`, claim
`tenantId` e claim `authorities`, e publica um **JWK set** que todo serviço valida localmente como
resource server (`jwk-set-uri` via REG) — sem callback ao ACS por requisição. As **autoridades derivam
determinísticamente do `papel`** (`cuidadora`, `coordenador_clinica`) por um mapa `role → authorities`
total; papel é dado, não ramo de código. Federação com IdP externo fica **explicitamente adiada** — o
modelo é desenhado para trocar o emissor local por um OIDC externo depois sem mudar os resource servers
(a interface é a mesma: JWT + JWK set). MFA, self-service reset e refresh-token rotation ficam fora do
MVP.

## Consequências
**Positivas:** autenticação stateless e verify-local — o ACS não fica no caminho quente de cada
requisição autorizada, só de login e resolves; zero infra externa no MVP; o mapa papel→autoridades é a
única fonte de RBAC, alinhado à constituição; caminho de evolução para IdP externo preservado (mesma
forma de token). **Negativas / trade-offs:** o ACS vira âncora de confiança (ver risco em doc 06); sem
store de revogação, desativar um cuidador só surte efeito no próximo ciclo de token — exige TTL curto,
que aumenta a frequência de login; o ACS assume responsabilidade sensível (guarda de credenciais e de
chaves de assinatura) que precisa de revisão de segurança rigorosa; o mapa de autoridades atual é
grosseiro (dois papéis) e precisará ser reconciliado com `permission-catalogue.arch.json` em Specs.
