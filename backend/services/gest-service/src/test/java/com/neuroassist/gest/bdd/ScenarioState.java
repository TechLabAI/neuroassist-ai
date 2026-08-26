package com.neuroassist.gest.bdd;

import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Estado compartilhado entre steps de um mesmo cenário. Escopo {@code cucumber-glue}: uma instância
 * nova por cenário.
 *
 * <p>{@code currentToken} substituiu o antigo {@code currentCredentials} de HTTP Basic. O Basic
 * saiu do {@code SecurityConfig} de produção — ele existia apenas para servir esta suíte, com senha
 * em texto plano válida em todo ambiente —, então os cenários passaram a assinar JWTs de teste como
 * fazem os outros seis serviços.
 */
@Component
@Scope("cucumber-glue")
public class ScenarioState {

    /** Última resposta HTTP recebida do serviço sob teste. */
    public ResponseEntity<String> lastResponse;

    /** UUID (texto) do último TipoGesto criado ou transicionado no cenário. */
    public String lastEntityId;

    /** JWT do chamador atual, sem o prefixo "Bearer ". Nulo = requisição anônima. */
    public String currentToken;

    /** Resposta anterior, para os cenários de idempotência. */
    public ResponseEntity<String> previousResponse;
}
