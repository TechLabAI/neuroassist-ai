package com.neuroassist.e2e.support;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;

/**
 * Espera ativa curta para propagação entre serviços.
 *
 * <p>Existe porque um cross-service não é síncrono do ponto de vista do cliente: o gest-service
 * pode ter cacheado o catálogo do PAC por alguns segundos. Um `sleep` fixo escolheria entre lento
 * demais e instável; o polling termina no instante em que a condição vale e só falha quando o prazo
 * estoura — e aí a mensagem diz qual foi o último valor observado, que é a informação que falta num
 * timeout anônimo.
 */
public final class Await {

    private static final Duration POLL = Duration.ofMillis(250);

    private Await() {}

    /** Repete {@code supplier} até que o valor mude em relação a {@code valorAnterior}. */
    public static <T> T untilChanged(
            String descricao, T valorAnterior, Duration prazo, Supplier<T> supplier) {
        Instant limite = Instant.now().plus(prazo);
        T ultimo = valorAnterior;
        while (Instant.now().isBefore(limite)) {
            ultimo = supplier.get();
            if (ultimo != null && !ultimo.equals(valorAnterior)) {
                return ultimo;
            }
            sleep();
        }
        Assertions.fail(
                "%s nao mudou em %ds — continua em %s"
                        .formatted(descricao, prazo.toSeconds(), ultimo));
        return ultimo;
    }

    /**
     * Repete {@code supplier} até que ele devolva um valor não nulo.
     *
     * <p>É o que a escrita assíncrona de fatos no hist-service exige. A regra que dá valor a este
     * método está no <b>predicado</b>, não aqui: ele tem de identificar <i>aquele</i> fato — pelo
     * id do evento que a resposta HTTP devolveu, pelo id do dispositivo desta rodada — e nunca "um
     * fato recente". Um predicado do tipo "existe algum fato nos últimos N segundos" passa por
     * acidente sob qualquer tráfego concorrente, e a suíte roda contra um dev compartilhado.
     *
     * @param descricao o que se está esperando, em uma linha; é o que aparece no timeout
     */
    public static <T> T untilPresent(String descricao, Duration prazo, Supplier<T> supplier) {
        Instant limite = Instant.now().plus(prazo);
        int tentativas = 0;
        while (Instant.now().isBefore(limite)) {
            tentativas++;
            T valor = supplier.get();
            if (valor != null) {
                return valor;
            }
            sleep();
        }
        Assertions.fail(
                "%s nao apareceu em %ds (%d tentativas)"
                        .formatted(descricao, prazo.toSeconds(), tentativas));
        return null;
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrompida", e);
        }
    }
}
