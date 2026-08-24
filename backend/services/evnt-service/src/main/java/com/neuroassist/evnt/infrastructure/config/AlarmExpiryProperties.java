package com.neuroassist.evnt.infrastructure.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Política de expiração por relevância clínica (ADR-011 §3).
 *
 * <p><b>O default de {@link #ttl} é vazio, e isso é a decisão, não um esquecimento.</b> O ADR-011
 * diz que a política de expiração precisa de um dono explícito por tipo de necessidade, e que
 * enquanto não houver esse dono o default seguro é <i>não expirar</i> — o oposto do default
 * anterior, que era desistir depois de dez tentativas. Um mapa vazio significa exatamente isso:
 * nenhum alarme envelhece até alguém declarar, por urgência, quanto tempo aquela necessidade
 * continua fazendo sentido.
 *
 * <p>Exemplo de configuração, quando houver dono:
 *
 * <pre>
 * evnt:
 *   alarm-expiry:
 *     ttl:
 *       rotina: 2h
 * </pre>
 *
 * <p>{@link #helpTerms} nunca é ignorado por {@code ttl}: um pedido de ajuda não expira nem que a
 * urgência dele tenha janela configurada.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "evnt.alarm-expiry")
public class AlarmExpiryProperties {

    /** Janela de relevância por {@code comando.urgencia}. Vazio = nada expira. */
    private Map<String, Duration> ttl = new LinkedHashMap<>();

    /**
     * Termos que identificam um pedido de ajuda no {@code comando_ref} ou no rótulo congelado.
     *
     * <p>Os defaults cobrem o vocabulário do catálogo de comandos em pt-BR e en. Sobrescrever esta
     * lista é o único jeito de mudar o que conta como ajuda — e reduzi-la é reduzir a proteção da
     * regra mais importante do ADR-011.
     */
    private Set<String> helpTerms = new LinkedHashSet<>(Set.of("ajuda", "socorro", "help"));
}
