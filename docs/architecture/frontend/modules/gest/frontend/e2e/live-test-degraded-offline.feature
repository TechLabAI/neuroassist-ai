# language: pt
Funcionalidade: Modo degradado honesto quando o edge está indisponível
  Como sistema de canal crítico de segurança,
  nunca devo reportar um reconhecimento falso quando o edge não está disponível.

  Cenário: Dispositivo offline entra em modo degradado sem bloquear a rota
    Dado que não há DispositivoEdge pareado e online
    Quando a superfície de teste ao vivo ativa
    Então o sistema ativa a superfície em modo degradado
    E o sistema não bloqueia a rota
    E o sistema não apresenta um veredito de reconhecimento real
    E um banner de modo degradado fica visível

  Cenário: Bridge inacessível cai para modo degradado
    Dado que o edge bridge está inacessível
    Quando a cuidadora aciona "Testar agora"
    Então o sistema entra em modo degradado com banner visível
    E o sistema não reporta um reconhecimento real
    E qualquer GestureTestOutcome emitido tem degraded verdadeiro

  Cenário: Versão de contrato incompatível força modo degradado
    Dado que o edge bridge anuncia um contractVersion incompatível com o frontend
    Então o sistema troca para o mock stream em modo degradado com motivo "version-mismatch"

  Cenário: Feature flag edgeLiveBridge desligada roda em modo degradado
    Dado que a feature flag "edgeLiveBridge" está desligada
    Quando a cuidadora aciona "Testar agora"
    Então o sistema roda o teste ao vivo em modo degradado usando o mock stream
