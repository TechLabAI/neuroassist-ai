package com.neuroassist.gest.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * O EDGE respondeu, e a resposta é "este dispositivo não está pareado". Erro de negócio, HTTP 403.
 *
 * <p>Existe como <b>tipo próprio</b> para que o circuit breaker possa ignorá-lo por classe ({@code
 * resilience4j...edgePairing.ignoreExceptions}). Enquanto era um {@link CodedException} genérico, o
 * breaker não tinha como distingui-lo do {@code edge-unavailable} — que é a mesma classe — e
 * contava os dois como falha: três dispositivos não pareados seguidos abriam o circuito e passavam
 * a reportar "EDGE indisponível" para todo mundo, inclusive para os dispositivos que estavam
 * perfeitamente pareados.
 *
 * <p>É a forma exata do defeito C-19: um erro de autorização/negócio lavado em "serviço degradado".
 * O {@code EdgePairingCircuitBreakerIT} afirmava o comportamento correto e estava vermelho.
 */
public class DeviceNotPairedException extends CodedException {

    public static final String CODE = "device-not-paired";

    public DeviceNotPairedException(UUID deviceId, String message) {
        super(CODE, message);
        withContext(Map.of("deviceId", deviceId.toString()));
    }
}
